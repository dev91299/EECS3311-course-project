package com.eecs3311.codereview.app;

import com.eecs3311.codereview.agent.AgentStep;
import com.eecs3311.codereview.agent.ReviewAgent;
import com.eecs3311.codereview.command.AcceptCommand;
import com.eecs3311.codereview.command.BulkTriageCommand;
import com.eecs3311.codereview.command.ChangeSeverityCommand;
import com.eecs3311.codereview.command.CommandHistory;
import com.eecs3311.codereview.command.RejectCommand;
import com.eecs3311.codereview.command.ReviewCommand;
import com.eecs3311.codereview.export.AbstractReportExporter;
import com.eecs3311.codereview.export.ExporterFactory;
import com.eecs3311.codereview.llm.LLMClient;
import com.eecs3311.codereview.llm.LLMException;
import com.eecs3311.codereview.model.DeltaReport;
import com.eecs3311.codereview.model.FileNode;
import com.eecs3311.codereview.model.ReviewItem;
import com.eecs3311.codereview.model.ReviewReport;
import com.eecs3311.codereview.model.ReviewSnapshot;
import com.eecs3311.codereview.model.Severity;
import com.eecs3311.codereview.observer.Observer;
import com.eecs3311.codereview.repo.RepoManager;
import com.eecs3311.codereview.util.Diff;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The application controller: the single place where a user action becomes a sequence of
 * calls on the model, the agent and the command history.
 *
 * <p>Both user interfaces are thin by design. {@code CodeReviewGUI} translates a button click
 * into one call on this class and then redraws when the report tells it to;
 * {@code CLIView} parses a typed command into the same call. Because there is exactly one
 * implementation of each operation, the two interfaces cannot disagree about what "reject a
 * finding" means - which is the requirement that the GUI and the CLI both expose the same
 * functionality.</p>
 *
 * <p>Methods here are synchronous and may block on the network. Keeping them off the event
 * thread is the responsibility of the caller; the GUI uses a {@code SwingWorker} for exactly
 * that reason.</p>
 */
public class AgentController {

    private final RepoManager repoManager = new RepoManager();
    private final ReviewReport report = new ReviewReport();
    private final ReviewAgent agent;
    private final CommandHistory history = new CommandHistory();
    private final Map<String, ReviewSnapshot> snapshots = new LinkedHashMap<>();

    public AgentController() {
        this.agent = new ReviewAgent(repoManager, report);
        LLMClient.getInstance().setStatusListener(message ->
                report.publish(Observer.UpdateEvent.TASK_STARTED, message));
    }

    // ------------------------------------------------------------------ F01: import

    /**
     * Feature F01. Imports a repository and prepares the agent for it.
     *
     * @param pathOrUrl a local directory, or a Git URL when {@code clone} is true
     * @param clone     whether to clone a remote repository first
     * @return the root of the file tree
     * @throws IOException when the path is invalid or unreadable
     */
    public FileNode handleImport(String pathOrUrl, boolean clone) throws IOException {
        FileNode root = clone ? repoManager.cloneAndLoad(pathOrUrl) : repoManager.loadFiles(pathOrUrl);

        report.clear();
        report.setRepositoryPath(repoManager.getRootPath().toString());
        history.clear();
        snapshots.clear();

        String preparation = agent.onRepositoryImported();
        report.publish(Observer.UpdateEvent.REPOSITORY_LOADED, root);
        report.publish(Observer.UpdateEvent.TASK_FINISHED,
                "Imported " + root.getTotalFiles() + " file(s). " + preparation);
        return root;
    }

    /** Reads a file's text for display in the editor. */
    public String readFile(String filePath) throws IOException {
        return repoManager.readFile(filePath);
    }

    // ------------------------------------------------------------------ F02-F06: analysis

    /**
     * Features F03 and F06. Runs one strategy over a file or selection and records findings.
     *
     * @param filePath     the file the code came from
     * @param code         the code to review; when null the file is read from disk
     * @param strategyName which analysis to run
     */
    public List<ReviewItem> requestReview(String filePath, String code, String strategyName)
            throws LLMException, IOException {
        String source = code != null ? code : repoManager.readFile(filePath);
        report.publish(Observer.UpdateEvent.TASK_STARTED,
                "Running the " + strategyName + " review on " + shortName(filePath) + "...");
        try {
            List<ReviewItem> findings = agent.analyzeCode(filePath, source, strategyName);
            report.publish(Observer.UpdateEvent.TASK_FINISHED,
                    "Found " + findings.size() + " issue(s) in " + shortName(filePath) + ".");
            return findings;
        } catch (LLMException e) {
            report.publish(Observer.UpdateEvent.ERROR, e.getUserMessage());
            throw e;
        }
    }

    /** Runs every strategy over one file, which is the GUI's "Full review" action. */
    public List<ReviewItem> requestFullReview(String filePath, String code)
            throws LLMException, IOException {
        String source = code != null ? code : repoManager.readFile(filePath);
        report.publish(Observer.UpdateEvent.TASK_STARTED,
                "Running all " + agent.getStrategies().all().size()
                + " review strategies on " + shortName(filePath) + "...");
        try {
            List<ReviewItem> findings = agent.analyzeWithAllStrategies(filePath, source);
            report.publish(Observer.UpdateEvent.TASK_FINISHED,
                    "Full review complete: " + findings.size() + " finding(s).");
            return findings;
        } catch (LLMException e) {
            report.publish(Observer.UpdateEvent.ERROR, e.getUserMessage());
            throw e;
        }
    }

    /** Feature F02. */
    public String summarise(String filePath, String code) throws LLMException, IOException {
        String source = code != null ? code : repoManager.readFile(filePath);
        report.publish(Observer.UpdateEvent.TASK_STARTED, "Summarising " + shortName(filePath) + "...");
        try {
            String summary = agent.summarise(filePath, source);
            report.publish(Observer.UpdateEvent.TASK_FINISHED, "Summary ready.");
            return summary;
        } catch (LLMException e) {
            report.publish(Observer.UpdateEvent.ERROR, e.getUserMessage());
            throw e;
        }
    }

    /** Feature F05. */
    public String generateTests(String filePath, String code) throws LLMException, IOException {
        String source = code != null ? code : repoManager.readFile(filePath);
        report.publish(Observer.UpdateEvent.TASK_STARTED,
                "Generating tests for " + shortName(filePath) + "...");
        try {
            String tests = agent.generateTests(filePath, source);
            report.publish(Observer.UpdateEvent.TASK_FINISHED, "Tests generated.");
            return tests;
        } catch (LLMException e) {
            report.publish(Observer.UpdateEvent.ERROR, e.getUserMessage());
            throw e;
        }
    }

    /**
     * Feature F04. Produces a refactoring and the side-by-side comparison of it.
     *
     * @param issueId the finding to address, or null for a general improvement
     * @return the original code, the rewritten code, the model's explanation, and the
     *         rendered side-by-side view
     */
    public RefactorResult suggestRefactor(String filePath, String code, String issueId)
            throws LLMException, IOException {
        String source = code != null ? code : repoManager.readFile(filePath);
        ReviewItem finding = issueId == null ? null : report.findById(issueId).orElse(null);

        report.publish(Observer.UpdateEvent.TASK_STARTED,
                "Generating a refactoring for " + shortName(filePath) + "...");
        try {
            String reply = agent.suggestRefactor(filePath, source, finding);
            String revised = extractCodeBlock(reply);
            if (revised.isBlank()) {
                // The error case feature F04 names: a malformed reply means ask again, once.
                report.publish(Observer.UpdateEvent.TASK_STARTED,
                        "The model returned no usable code block; requesting it again...");
                reply = agent.suggestRefactor(filePath, source, finding);
                revised = extractCodeBlock(reply);
            }
            if (revised.isBlank()) {
                throw new LLMException("The model did not return a usable code block after two "
                        + "attempts. Try selecting a smaller piece of code.", false, 0);
            }
            int[] counts = Diff.countChanges(source, revised);
            report.publish(Observer.UpdateEvent.TASK_FINISHED,
                    "Refactoring ready: +" + counts[0] + " / -" + counts[1] + " lines.");
            return new RefactorResult(source, revised, reply,
                    Diff.renderSideBySide(source, revised, 58), counts[0], counts[1]);
        } catch (LLMException e) {
            report.publish(Observer.UpdateEvent.ERROR, e.getUserMessage());
            throw e;
        }
    }

    /** The result of a refactoring request. */
    public static class RefactorResult {
        private final String original;
        private final String revised;
        private final String explanation;
        private final String sideBySide;
        private final int linesAdded;
        private final int linesRemoved;

        RefactorResult(String original, String revised, String explanation, String sideBySide,
                       int linesAdded, int linesRemoved) {
            this.original = original;
            this.revised = revised;
            this.explanation = explanation;
            this.sideBySide = sideBySide;
            this.linesAdded = linesAdded;
            this.linesRemoved = linesRemoved;
        }

        public String getOriginal() {
            return original;
        }

        public String getRevised() {
            return revised;
        }

        /** The model's full reply, including its "WHY" bullets. */
        public String getExplanation() {
            return explanation;
        }

        public String getSideBySide() {
            return sideBySide;
        }

        public int getLinesAdded() {
            return linesAdded;
        }

        public int getLinesRemoved() {
            return linesRemoved;
        }

        /** The unified diff, used by the CLI. */
        public String getUnifiedDiff() {
            return Diff.renderUnified(original, revised);
        }
    }

    /** Pulls the first fenced code block out of a model reply. */
    private String extractCodeBlock(String reply) {
        if (reply == null) {
            return "";
        }
        int open = reply.indexOf("```");
        if (open < 0) {
            return "";
        }
        int bodyStart = reply.indexOf('\n', open);
        int close = reply.indexOf("```", open + 3);
        if (bodyStart < 0 || close < bodyStart) {
            return "";
        }
        return reply.substring(bodyStart + 1, close);
    }

    // ------------------------------------------------------------------ F10: chat

    /** Feature F10. */
    public String submitChat(String question, String openFileCode) throws LLMException {
        report.publish(Observer.UpdateEvent.TASK_STARTED, "Thinking...");
        try {
            String answer = agent.chat(question, openFileCode);
            report.publish(Observer.UpdateEvent.CHAT_UPDATED, answer);
            return answer;
        } catch (LLMException e) {
            report.publish(Observer.UpdateEvent.ERROR, e.getUserMessage());
            throw e;
        }
    }

    // ------------------------------------------------------------------ F11: autonomous audit

    /**
     * Feature F11. Runs the autonomous audit, streaming each reasoning step to the views.
     *
     * @param goal         what to achieve
     * @param maxSteps     tool-invocation budget
     * @param stepListener extra listener for the calling view; may be null
     */
    public String runAudit(String goal, int maxSteps, Consumer<AgentStep> stepListener)
            throws LLMException {
        report.publish(Observer.UpdateEvent.TASK_STARTED, "Starting autonomous audit: " + goal);
        try {
            String conclusion = agent.runAudit(goal, maxSteps, step -> {
                report.publish(Observer.UpdateEvent.AGENT_STEP, step);
                if (stepListener != null) {
                    stepListener.accept(step);
                }
            });
            report.publish(Observer.UpdateEvent.TASK_FINISHED, "Audit complete.");
            return conclusion;
        } catch (LLMException e) {
            report.publish(Observer.UpdateEvent.ERROR, e.getUserMessage());
            throw e;
        }
    }

    /** Requests cancellation of a running audit. */
    public void cancelAudit() {
        agent.cancelAudit();
    }

    // ------------------------------------------------------------------ F07, F12: triage

    /** Feature F07. Accepts a finding, through the command history so it can be undone. */
    public boolean acceptFinding(String issueId, String note) {
        Optional<ReviewItem> item = report.findById(issueId);
        if (item.isEmpty()) {
            return false;
        }
        AcceptCommand command = new AcceptCommand(item.get(), report, note);
        history.execute(command);
        agent.rememberDecision(item.get());
        return true;
    }

    /** Feature F07. Rejects a finding and feeds the reason into the agent's memory. */
    public boolean rejectFinding(String issueId, String reason) {
        Optional<ReviewItem> item = report.findById(issueId);
        if (item.isEmpty()) {
            return false;
        }
        RejectCommand command = new RejectCommand(item.get(), report, reason);
        history.execute(command);
        agent.rememberDecision(item.get());
        return true;
    }

    /** Re-grades a finding's severity. */
    public boolean changeSeverity(String issueId, Severity severity) {
        Optional<ReviewItem> item = report.findById(issueId);
        if (item.isEmpty() || severity == null) {
            return false;
        }
        history.execute(new ChangeSeverityCommand(item.get(), report, severity));
        return true;
    }

    /**
     * Rejects every pending finding at or below a severity, as one undoable batch.
     *
     * @return how many findings were affected
     */
    public int bulkRejectAtOrBelow(Severity threshold, String reason) {
        List<ReviewCommand> commands = new ArrayList<>();
        List<ReviewItem> affected = new ArrayList<>();
        for (ReviewItem item : report.getPending()) {
            if (item.getSeverity().getRank() >= threshold.getRank()) {
                commands.add(new RejectCommand(item, report, reason));
                affected.add(item);
            }
        }
        if (commands.isEmpty()) {
            return 0;
        }
        history.execute(new BulkTriageCommand(
                "Rejected all pending findings at or below " + threshold.getLabel(), commands));
        affected.forEach(agent::rememberDecision);
        return commands.size();
    }

    /** Feature F12. Undoes the most recent review action. */
    public Optional<String> undo() {
        return history.undo().map(ReviewCommand::getDescription);
    }

    /** Feature F12. Re-applies the most recently undone action. */
    public Optional<String> redo() {
        return history.redo().map(ReviewCommand::getDescription);
    }

    // ------------------------------------------------------------------ F08: version compare

    /**
     * Feature F08. Stores the current findings under a label so they can be compared later.
     */
    public ReviewSnapshot takeSnapshot(String label, String commitHash) {
        ReviewSnapshot snapshot = report.snapshot(label, commitHash);
        snapshots.put(label.toLowerCase(), snapshot);
        return snapshot;
    }

    /** The labels of all stored snapshots. */
    public List<String> getSnapshotLabels() {
        return new ArrayList<>(snapshots.keySet());
    }

    /**
     * Feature F08. Compares two stored snapshots and asks the model to interpret the delta.
     *
     * @throws IOException when a label is unknown
     */
    public DeltaReport compareSnapshots(String labelA, String labelB) throws IOException {
        ReviewSnapshot before = snapshots.get(labelA == null ? "" : labelA.toLowerCase());
        ReviewSnapshot after = snapshots.get(labelB == null ? "" : labelB.toLowerCase());
        if (before == null || after == null) {
            throw new IOException("Unknown snapshot label. Available: "
                    + String.join(", ", snapshots.keySet()));
        }
        DeltaReport delta = new DeltaReport(before, after);

        if (repoManager.isGitRepository()
                && !before.getCommitHash().isEmpty() && !after.getCommitHash().isEmpty()) {
            try {
                delta.setChangedFilesDiff(
                        repoManager.getDiff(before.getCommitHash(), after.getCommitHash()));
            } catch (IOException e) {
                delta.setChangedFilesDiff("(Git comparison unavailable: " + e.getMessage() + ")");
            }
        }
        try {
            delta.setAiSummary(agent.summariseDelta(delta));
        } catch (LLMException e) {
            delta.setAiSummary("(The AI assessment was unavailable: " + e.getUserMessage()
                    + " The deterministic comparison above is unaffected.)");
        }
        return delta;
    }

    /** Lists recent commits, for the commit picker in the comparison dialog. */
    public List<String> listCommits(int limit) throws IOException {
        return repoManager.listCommits(limit);
    }

    // ------------------------------------------------------------------ F09: export

    /**
     * Feature F09. Aggregates the session and writes it to a file.
     *
     * @param destination output path; the format is taken from its extension unless
     *                    {@code format} is given
     * @param format      pdf, md, html or json; may be null to infer from the file name
     * @return the path written
     * @throws IOException when the format is unknown or the file cannot be written
     */
    public Path exportReport(String destination, String format) throws IOException {
        Path path = Paths.get(destination);
        Optional<AbstractReportExporter> exporter = format == null || format.isBlank()
                ? ExporterFactory.createForFileName(path.getFileName().toString())
                : ExporterFactory.create(format);

        if (exporter.isEmpty()) {
            throw new IOException("Unknown export format. Supported formats:\n"
                    + ExporterFactory.describe());
        }

        AbstractReportExporter chosen = exporter.get();
        chosen.setActionLog(history.renderLog());
        chosen.setUsageSummary(LLMClient.getInstance().getUsageStats().render());
        if (!agent.getAuditTrace().isEmpty()) {
            StringBuilder trace = new StringBuilder();
            agent.getAuditTrace().forEach(step -> trace.append(step.renderFull()).append('\n'));
            chosen.setAgentTrace(trace.toString());
        }

        report.aggregateItems();
        chosen.export(report, path);
        report.publish(Observer.UpdateEvent.EXPORT_COMPLETE, path.toAbsolutePath().toString());
        return path.toAbsolutePath();
    }

    // ------------------------------------------------------------------ accessors

    /** Registers a view with the report, which is the subject both views observe. */
    public void addObserver(Observer observer) {
        report.addObserver(observer);
    }

    public void removeObserver(Observer observer) {
        report.removeObserver(observer);
    }

    public ReviewReport getReport() {
        return report;
    }

    public ReviewAgent getAgent() {
        return agent;
    }

    public RepoManager getRepoManager() {
        return repoManager;
    }

    public CommandHistory getHistory() {
        return history;
    }

    /** True when a repository has been imported. */
    public boolean isRepositoryLoaded() {
        return repoManager.isLoaded();
    }

    /** True when no API key is configured. */
    public boolean isOfflineMode() {
        return LLMClient.getInstance().isOfflineMode();
    }

    /** A one-line description of the active model configuration, for the status bar. */
    public String describeConfiguration() {
        if (isOfflineMode()) {
            return "OFFLINE MODE - no API key configured. AI features are limited. See README.";
        }
        return "Model: " + LLMClient.getInstance().describeProvider()
                + "   key " + AppConfig.redactedApiKey();
    }

    private String shortName(String path) {
        if (path == null) {
            return "(selection)";
        }
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return slash >= 0 ? path.substring(slash + 1) : path;
    }
}
