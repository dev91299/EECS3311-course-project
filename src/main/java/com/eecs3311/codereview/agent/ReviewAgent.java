package com.eecs3311.codereview.agent;

import com.eecs3311.codereview.agent.strategy.AnalysisContext;
import com.eecs3311.codereview.agent.strategy.ReviewStrategy;
import com.eecs3311.codereview.agent.strategy.StrategyRegistry;
import com.eecs3311.codereview.agent.tools.AnalysisService;
import com.eecs3311.codereview.agent.tools.AnalyzeFileTool;
import com.eecs3311.codereview.agent.tools.GitHistoryTool;
import com.eecs3311.codereview.agent.tools.ListFilesTool;
import com.eecs3311.codereview.agent.tools.ReadFileTool;
import com.eecs3311.codereview.agent.tools.SearchCodeTool;
import com.eecs3311.codereview.agent.tools.ToolRegistry;
import com.eecs3311.codereview.app.AppConfig;
import com.eecs3311.codereview.llm.LLMClient;
import com.eecs3311.codereview.llm.LLMException;
import com.eecs3311.codereview.llm.PromptBuilder;
import com.eecs3311.codereview.model.ChatMessage;
import com.eecs3311.codereview.model.DeltaReport;
import com.eecs3311.codereview.model.FileNode;
import com.eecs3311.codereview.model.ReviewItem;
import com.eecs3311.codereview.model.ReviewReport;
import com.eecs3311.codereview.repo.RepoManager;
import com.eecs3311.codereview.repo.StandardsChecker;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * FACADE PATTERN - the single entry point to the entire AI subsystem.
 *
 * <p>Behind this class sit nine collaborating components: the Singleton model client and its
 * four decorators, the strategy registry and its four strategies, the deterministic standards
 * checker, the retrieval index, long-term memory, the tool registry and its five tools, and
 * the autonomous planner. A controller that had to orchestrate those directly would need to
 * know the order in which memory is recalled, context is retrieved, rules are loaded, prompts
 * are assembled and findings are recorded - and both the GUI and the CLI would need to know
 * it identically.</p>
 *
 * <p>Instead {@code AgentController} calls {@link #analyzeFile}, {@link #summarise},
 * {@link #generateTests}, {@link #suggestRefactor}, {@link #chat} or {@link #runAudit} and
 * receives a result. Every one of those methods performs the same preparation internally:
 * load the project's conventions, recall what the team decided about similar findings, pull
 * in related code from elsewhere in the repository, and only then call the model.</p>
 *
 * <p><strong>Change from Stage 1.</strong> Stage 1 gave the facade two operations,
 * {@code analyzeCode()} and {@code generateTests()}, and a field {@code strategy}. The
 * implemented facade exposes the six operations above, because features F02, F04, F08 and
 * F10 each need a different prompt shape and a different return type, and forcing them
 * through {@code analyzeCode()} would have meant callers inspecting the returned text to work
 * out what they had been given.</p>
 */
public class ReviewAgent implements AnalysisService {

    private final RepoManager repoManager;
    private final ReviewReport report;
    private final StandardsChecker standardsChecker = new StandardsChecker();
    private final StrategyRegistry strategies;
    private final CodeRetriever retriever;
    private final AgentMemory memory = new AgentMemory();
    private final ToolRegistry tools = new ToolRegistry();
    private final AgentPlanner planner;
    private final List<ChatMessage> conversation = new ArrayList<>();

    private ReviewStrategy activeStrategy;

    /**
     * @param repoManager file and Git access
     * @param report      the session report findings are recorded in
     */
    public ReviewAgent(RepoManager repoManager, ReviewReport report) {
        this.repoManager = repoManager;
        this.report = report;
        this.strategies = new StrategyRegistry(standardsChecker);
        this.activeStrategy = strategies.getDefault();
        this.retriever = new CodeRetriever(repoManager);
        this.planner = new AgentPlanner(tools, memory);

        tools.register(new ListFilesTool(repoManager));
        tools.register(new ReadFileTool(repoManager));
        tools.register(new SearchCodeTool(retriever));
        tools.register(new AnalyzeFileTool(this, strategies.names()));
        tools.register(new GitHistoryTool(repoManager));

        conversation.add(ChatMessage.system(
                "You are a code review assistant embedded in a developer tool. You answer "
              + "questions about the user's own codebase using the context supplied with each "
              + "question. You are precise, you cite file names and line numbers, and when the "
              + "supplied context does not contain the answer you say so plainly instead of "
              + "guessing. You decline requests that are not about software or this codebase."));
    }

    /**
     * Called after a repository is imported: loads its conventions, builds the retrieval
     * index, and attaches long-term memory to that repository.
     *
     * @return a human-readable summary of what was prepared
     */
    public String onRepositoryImported() {
        standardsChecker.loadRules(repoManager.readRulesFile());
        int indexed = retriever.buildIndex(400);
        if (repoManager.getRootPath() != null) {
            memory.attachToRepository(repoManager.getRootPath());
        }
        return "Indexed " + indexed + " file(s); rules from " + standardsChecker.getRulesSource()
                + "; recalled " + memory.size() + " remembered review decision(s).";
    }

    // ------------------------------------------------------------------ F03, F06: analysis

    /**
     * Features F03 and F06. Analyses one file with the named strategy and records the
     * findings in the session report.
     */
    @Override
    public List<ReviewItem> analyzeFile(String filePath, String strategyName) throws LLMException {
        Optional<FileNode> node = repoManager.findFile(filePath);
        String resolvedPath = node.map(FileNode::getPath).orElse(filePath);
        String code = repoManager.readFileQuietly(resolvedPath);
        if (code.isEmpty()) {
            return new ArrayList<>();
        }
        return analyzeCode(resolvedPath, code, strategyName);
    }

    /**
     * Analyses a code string. Used for a selection in the GUI editor, which may not
     * correspond to a whole file.
     */
    public List<ReviewItem> analyzeCode(String filePath, String code, String strategyName)
            throws LLMException {
        ReviewStrategy strategy = strategies.find(strategyName).orElse(activeStrategy);
        this.activeStrategy = strategy;

        AnalysisContext context = new AnalysisContext(
                filePath,
                code,
                null,
                repoManager.readRulesFile(),
                memory.renderRecall(code, filePath, 6),
                retrieveRelatedContext(code, filePath));

        List<ReviewItem> findings = strategy.executeStrategy(context);
        report.addItems(findings);
        return findings;
    }

    /** Runs every registered strategy over one file, which is what the GUI "Full review" does. */
    public List<ReviewItem> analyzeWithAllStrategies(String filePath, String code)
            throws LLMException {
        List<ReviewItem> all = new ArrayList<>();
        LLMException lastFailure = null;
        for (ReviewStrategy strategy : strategies.all()) {
            try {
                all.addAll(analyzeCode(filePath, code, strategy.getName()));
            } catch (LLMException e) {
                lastFailure = e;
            }
        }
        if (all.isEmpty() && lastFailure != null) {
            throw lastFailure;
        }
        return all;
    }

    // ------------------------------------------------------------------ F02: summarisation

    /** Feature F02. Produces a natural-language summary of a file or selection. */
    public String summarise(String filePath, String code) throws LLMException {
        if (code == null || code.isBlank()) {
            return "There is nothing to summarise: the selection is empty.";
        }
        PromptBuilder builder = new PromptBuilder()
                .role("You are a senior engineer explaining unfamiliar code to a colleague "
                    + "who has just joined the project.")
                .code(filePath, code)
                .instruction(
                        "Summarise the code above in four short sections:\n"
                      + "1. PURPOSE - what this code is for, in one or two sentences.\n"
                      + "2. HOW IT WORKS - the main flow through it, in three to five bullets.\n"
                      + "3. DEPENDENCIES - what it relies on and what relies on it, as far as "
                      + "the code shows.\n"
                      + "4. RISKS - anything a reviewer should look at closely.\n"
                      + "Write prose and bullets, not JSON. Do not restate the code line by line.");

        return LLMClient.getInstance()
                .sendPrompt(builder.buildSystemPrompt(), builder.build(), "code-summary");
    }

    // ------------------------------------------------------------------ F05: test generation

    /** Feature F05. Generates unit tests for a function or class. */
    public String generateTests(String filePath, String code) throws LLMException {
        if (code == null || code.isBlank()) {
            return "There is nothing to generate tests for: the selection is empty.";
        }
        String framework = detectTestFramework(filePath);

        PromptBuilder builder = new PromptBuilder()
                .role("You are a test engineer who writes small, focused, executable tests.")
                .code(filePath, code)
                .instruction(
                        "Write unit tests for the code above using " + framework + ".\n"
                      + "Cover, in this order: the typical case; each boundary value; each "
                      + "failure path the code can take; and any null or empty input it accepts.\n"
                      + "Rules:\n"
                      + "- One behaviour per test, with a name that states the behaviour.\n"
                      + "- Real assertions on real expected values. Never write a placeholder.\n"
                      + "- Include the imports needed to compile.\n"
                      + "- After the code block, list in one line per item any behaviour you "
                      + "could not test and why.\n"
                      + "Return the test code in a single fenced code block.");

        return LLMClient.getInstance()
                .sendPrompt(builder.buildSystemPrompt(), builder.build(), "test-generation");
    }

    /**
     * Picks a test framework from the file's language, defaulting to the standard library
     * framework when the project does not reveal one - the fallback use case UC03 specifies.
     */
    private String detectTestFramework(String filePath) {
        String configured = AppConfig.get("TEST_FRAMEWORK", "");
        if (!configured.isBlank()) {
            return configured;
        }
        String lower = filePath == null ? "" : filePath.toLowerCase();
        if (lower.endsWith(".py")) {
            return "pytest";
        }
        if (lower.endsWith(".js") || lower.endsWith(".ts")) {
            return "Jest";
        }
        if (lower.endsWith(".cs")) {
            return "NUnit";
        }
        if (lower.endsWith(".go")) {
            return "the Go testing package";
        }
        return "JUnit 5";
    }

    // ------------------------------------------------------------------ F04: refactoring

    /**
     * Feature F04. Proposes a rewritten version of the code, optionally targeting one
     * specific finding.
     *
     * @param finding the finding to address, or null to improve the code generally
     * @return the model's reply, containing the rewritten code in a fenced block
     */
    public String suggestRefactor(String filePath, String code, ReviewItem finding)
            throws LLMException {
        if (code == null || code.isBlank()) {
            return "There is nothing to refactor: the selection is empty.";
        }
        PromptBuilder builder = new PromptBuilder()
                .role("You are a refactoring specialist. You change structure without changing "
                    + "behaviour, and you never silently alter what the code does.")
                .code(filePath, code)
                .conventions(repoManager.readRulesFile());

        if (finding != null) {
            builder.instruction("Address this specific finding:\n"
                    + "  Severity: " + finding.getSeverity().getLabel() + "\n"
                    + "  Issue: " + finding.getTitle() + "\n"
                    + "  Detail: " + finding.getDescription() + "\n"
                    + "  Line: " + finding.getLineNumber() + "\n");
        }
        builder.instruction(
                "Rewrite the code to fix the problem"
              + (finding == null ? "s you find" : " above")
              + " while preserving its observable behaviour.\n"
              + "Rules:\n"
              + "- Return the complete rewritten version in one fenced code block, not a diff "
              + "and not a fragment.\n"
              + "- Keep the existing public signatures unless changing one is the point of the fix.\n"
              + "- After the code block, write 'WHY:' followed by two to four bullets explaining "
              + "each change.\n"
              + "- If a change would alter behaviour, say so explicitly in those bullets.");

        return LLMClient.getInstance()
                .sendPrompt(builder.buildSystemPrompt(), builder.build(), "refactor-suggestion");
    }

    // ------------------------------------------------------------------ F10: chat

    /**
     * Feature F10. Answers a question about the codebase.
     *
     * <p>The question is not forwarded as typed. It is first used to retrieve the most
     * relevant regions of the repository, those regions are attached as context, and the
     * running conversation is included so follow-up questions work. That retrieval step is
     * what lets the assistant answer about a codebase far larger than any context window.</p>
     *
     * @param question    the developer's question
     * @param openFileCode the file currently open in the editor, attached as extra context
     */
    public String chat(String question, String openFileCode) throws LLMException {
        if (question == null || question.isBlank()) {
            return "Please type a question.";
        }

        List<CodeRetriever.Snippet> snippets = retriever.retrieve(question, 4, 14);
        String retrieved = retriever.renderForPrompt(snippets, 7_000);

        StringBuilder user = new StringBuilder();
        if (!retrieved.isEmpty()) {
            user.append("CONTEXT RETRIEVED FROM THE REPOSITORY FOR THIS QUESTION\n")
                .append(retrieved).append('\n');
        }
        if (openFileCode != null && !openFileCode.isBlank()) {
            user.append("THE FILE CURRENTLY OPEN IN THE EDITOR\n```\n")
                .append(openFileCode.length() > 6_000
                        ? openFileCode.substring(0, 6_000) + "\n... (truncated)"
                        : openFileCode)
                .append("\n```\n\n");
        }
        user.append("QUESTION\n").append(question.trim()).append('\n');
        if (!retrieved.isEmpty()) {
            user.append("\nAnswer using the context above. Cite the file and line numbers you "
                    + "relied on. If the context does not contain the answer, say which file you "
                    + "would need to see.");
        }

        conversation.add(ChatMessage.user(user.toString()));
        trimConversation();

        String answer = LLMClient.getInstance()
                .sendConversation(conversation, "chat-assistant", 0.3);
        conversation.add(ChatMessage.assistant(answer));

        if (!snippets.isEmpty()) {
            StringBuilder sources = new StringBuilder("\n\nSources consulted:\n");
            for (CodeRetriever.Snippet snippet : snippets) {
                sources.append("  - ").append(repoManager.relativise(snippet.getFilePath()))
                       .append(" (from line ").append(snippet.getStartLine()).append(")\n");
            }
            return answer + sources;
        }
        return answer;
    }

    /** Keeps the conversation inside the context budget by dropping the oldest exchanges. */
    private void trimConversation() {
        final int maxTurns = 13; // one system message plus six exchanges
        while (conversation.size() > maxTurns) {
            conversation.remove(1); // index 0 is the system message and is always kept
        }
    }

    /** Clears the conversation, keeping the system instruction. */
    public void clearConversation() {
        ChatMessage system = conversation.get(0);
        conversation.clear();
        conversation.add(system);
    }

    /** The conversation so far, for the GUI transcript and the exported report. */
    public List<ChatMessage> getConversation() {
        return new ArrayList<>(conversation);
    }

    // ------------------------------------------------------------------ F11: autonomous audit

    /**
     * Feature F11. Runs the autonomous multi-step audit.
     *
     * @param goal         what to achieve, in plain language
     * @param maxSteps     tool-invocation budget
     * @param stepListener receives each reasoning step as it happens, for live display
     */
    public String runAudit(String goal, int maxSteps, Consumer<AgentStep> stepListener)
            throws LLMException {
        planner.setStepListener(stepListener);
        return planner.run(goal, maxSteps);
    }

    /** Requests cancellation of a running audit. */
    public void cancelAudit() {
        planner.cancel();
    }

    /** The reasoning trace of the most recent audit. */
    public List<AgentStep> getAuditTrace() {
        return planner.getTrace();
    }

    // ------------------------------------------------------------------ F08: version delta

    /** Feature F08. Asks the model to explain a computed quality delta in prose. */
    public String summariseDelta(DeltaReport delta) throws LLMException {
        StringBuilder user = new StringBuilder();
        user.append("A code review tool compared two versions of a project and computed this "
                + "delta deterministically:\n\n").append(delta.render()).append('\n');
        if (!delta.getChangedFilesDiff().isBlank()) {
            user.append("Git reported these changes between the two versions:\n")
                .append(delta.getChangedFilesDiff()).append('\n');
        }
        user.append("\nIn no more than six sentences, explain what this says about the direction "
                + "of code quality, which single change matters most, and what the team should "
                + "do next. Do not restate the lists; interpret them.");

        return LLMClient.getInstance().sendPrompt(
                "You are a technical lead assessing whether a change improved a codebase.",
                user.toString(), "delta-summary");
    }

    // ------------------------------------------------------------------ shared helpers

    /** Retrieves related code from elsewhere in the repository to accompany an analysis. */
    private String retrieveRelatedContext(String code, String filePath) {
        if (!retriever.isIndexed() || code == null || code.isBlank()) {
            return "";
        }
        String query = extractIdentifiers(code);
        if (query.isBlank()) {
            return "";
        }
        List<CodeRetriever.Snippet> snippets = retriever.retrieve(query, 2, 10).stream()
                .filter(s -> !s.getFilePath().equals(filePath))
                .toList();
        return retriever.renderForPrompt(snippets, 2_500);
    }

    /** Pulls the most distinctive identifiers out of a file to use as a retrieval query. */
    private String extractIdentifiers(String code) {
        StringBuilder sb = new StringBuilder();
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("\\b([A-Z][a-zA-Z0-9]{3,})\\b").matcher(code);
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        while (matcher.find() && seen.size() < 12) {
            seen.add(matcher.group(1));
        }
        for (String identifier : seen) {
            sb.append(identifier).append(' ');
        }
        return sb.toString().trim();
    }

    /** Records the team's triage decision in long-term memory (supports F07 and F12). */
    public void rememberDecision(ReviewItem item) {
        memory.recordDecision(item);
    }

    // ------------------------------------------------------------------ accessors

    public StrategyRegistry getStrategies() {
        return strategies;
    }

    public ReviewStrategy getActiveStrategy() {
        return activeStrategy;
    }

    /** Selects the strategy used when none is named explicitly. */
    public void setActiveStrategy(String name) {
        strategies.find(name).ifPresent(s -> this.activeStrategy = s);
    }

    public AgentMemory getMemory() {
        return memory;
    }

    public CodeRetriever getRetriever() {
        return retriever;
    }

    public ToolRegistry getTools() {
        return tools;
    }

    public StandardsChecker getStandardsChecker() {
        return standardsChecker;
    }

    /** True when no API key is configured, so the views can show the offline banner. */
    public boolean isOfflineMode() {
        return LLMClient.getInstance().isOfflineMode();
    }

    /** The active provider chain, for the status bar. */
    public String describeProvider() {
        return LLMClient.getInstance().describeProvider();
    }
}
