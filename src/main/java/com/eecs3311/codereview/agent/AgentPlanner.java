package com.eecs3311.codereview.agent;

import com.eecs3311.codereview.agent.tools.ToolRegistry;
import com.eecs3311.codereview.app.AppConfig;
import com.eecs3311.codereview.json.Json;
import com.eecs3311.codereview.llm.LLMClient;
import com.eecs3311.codereview.llm.LLMException;
import com.eecs3311.codereview.model.ChatMessage;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The autonomous reasoning loop (feature F11) - the component that makes this an agent
 * rather than a prompt forwarder.
 *
 * <p>Given a goal in plain language, such as "find the security problems in this project",
 * the planner runs a sense-decide-act cycle:</p>
 * <ol>
 *   <li><strong>Plan.</strong> It asks the model to turn the goal into an ordered strategy,
 *       given the tools that exist and an inventory of the repository.</li>
 *   <li><strong>Decide.</strong> On each iteration it sends the goal, the plan and everything
 *       observed so far, and the model replies with the single next action to take.</li>
 *   <li><strong>Act.</strong> The named tool is executed. The agent never executes model
 *       output as code; it only selects from a fixed tool catalogue.</li>
 *   <li><strong>Observe.</strong> The tool's result is written into short-term memory, and the
 *       cycle repeats with that new knowledge available.</li>
 *   <li><strong>Conclude.</strong> When the model judges the goal met, or the step budget is
 *       exhausted, it writes a synthesis over everything gathered.</li>
 * </ol>
 *
 * <p>What makes this more than a loop of API calls: the agent chooses which files to examine
 * based on what it found in earlier steps, it recovers from tool errors by trying something
 * else, it is prevented from repeating an action it has already taken, and its decisions are
 * conditioned on memory of how this team triaged previous findings.</p>
 */
public class AgentPlanner {

    private final ToolRegistry tools;
    private final AgentMemory memory;
    private final List<AgentStep> trace = new ArrayList<>();
    private Consumer<AgentStep> stepListener;
    private volatile boolean cancelled;

    public AgentPlanner(ToolRegistry tools, AgentMemory memory) {
        this.tools = tools;
        this.memory = memory;
    }

    /** Registers a listener notified as each step completes, so views can stream the trace. */
    public void setStepListener(Consumer<AgentStep> listener) {
        this.stepListener = listener;
    }

    /** Requests that the current run stop after the step in progress. */
    public void cancel() {
        this.cancelled = true;
    }

    /**
     * Runs an autonomous audit.
     *
     * @param goal     what the user wants achieved, in plain language
     * @param maxSteps hard ceiling on tool invocations, so a run always terminates
     * @return the agent's closing synthesis
     */
    public String run(String goal, int maxSteps) throws LLMException {
        cancelled = false;
        trace.clear();
        memory.clearShortTerm();

        int budget = maxSteps > 0 ? maxSteps : AppConfig.getInt(AppConfig.KEY_AGENT_MAX_STEPS, 8);
        int stepNumber = 0;

        // ---------------------------------------------------------------- 1. plan
        stepNumber++;
        String plan = createPlan(goal);
        emit(new AgentStep(stepNumber, AgentStep.Type.PLAN, "Strategy for: " + goal, plan));
        memory.remember("Plan established: " + plan);

        Set<String> alreadyPerformed = new LinkedHashSet<>();
        String conclusion = null;

        // ---------------------------------------------------------- 2-4. decide/act/observe
        while (stepNumber < budget && !cancelled) {
            stepNumber++;

            Map<String, Object> decision = decideNextAction(goal, plan, stepNumber, budget);

            String thought = Json.getString(decision, "", "thought");
            String toolName = Json.getString(decision, "", "tool");
            String toolInput = Json.getString(decision, "", "input");
            String finalAnswer = Json.getString(decision, "", "final_answer");

            if (!thought.isBlank()) {
                emit(new AgentStep(stepNumber, AgentStep.Type.THOUGHT, "", thought));
            }

            if (!finalAnswer.isBlank() || toolName.isBlank() || "none".equalsIgnoreCase(toolName)) {
                conclusion = finalAnswer.isBlank() ? null : finalAnswer;
                break;
            }

            String signature = toolName.toLowerCase() + "|" + toolInput.toLowerCase().trim();
            if (!alreadyPerformed.add(signature)) {
                // The model is looping. Tell it so, as an observation it can act on.
                String note = "That exact action was already performed and produced the result "
                        + "above. Choose a different file, different search terms, or finish.";
                emit(new AgentStep(stepNumber, AgentStep.Type.OBSERVATION, "repeat blocked", note));
                memory.remember("Blocked a repeated action: " + signature);
                continue;
            }

            emit(new AgentStep(stepNumber, AgentStep.Type.ACTION, toolName, toolInput));
            String observation = tools.execute(toolName, toolInput);
            emit(new AgentStep(stepNumber, AgentStep.Type.OBSERVATION, toolName, observation));
            memory.remember("Ran " + toolName + "('" + toolInput + "') and observed: "
                    + truncate(observation, 1_200));
        }

        // ---------------------------------------------------------------- 5. conclude
        stepNumber++;
        if (cancelled) {
            String message = "The audit was cancelled after " + trace.size() + " steps. "
                    + "Any findings recorded before cancellation are in the report.";
            emit(new AgentStep(stepNumber, AgentStep.Type.CONCLUSION, "Cancelled", message));
            return message;
        }
        if (conclusion == null) {
            conclusion = synthesise(goal);
        }
        emit(new AgentStep(stepNumber, AgentStep.Type.CONCLUSION, "Audit complete", conclusion));
        return conclusion;
    }

    // ------------------------------------------------------------------ phases

    /** Phase 1: turn the goal into an ordered strategy, grounded in what the repository holds. */
    private String createPlan(String goal) throws LLMException {
        String inventory = tools.execute("list_files", "");

        String system = "You are the planning component of an autonomous code review agent. "
                + "You produce short, concrete plans that can be carried out with the available "
                + "tools. You never invent files that were not listed.";

        String user = "GOAL\n" + goal + "\n\n"
                + "AVAILABLE TOOLS\n" + tools.describeForPrompt() + "\n"
                + "REPOSITORY INVENTORY\n" + truncate(inventory, 3_000) + "\n\n"
                + "Write a plan of three to five steps that achieves the goal using those tools "
                + "and the files that actually exist above. Name specific files where you can. "
                + "Reply as a numbered list of short sentences, nothing else.";

        return LLMClient.getInstance().sendPrompt(system, user, "agent-planning");
    }

    /** Phase 2: ask for exactly one next action, given everything observed so far. */
    private Map<String, Object> decideNextAction(String goal, String plan,
                                                 int stepNumber, int budget) throws LLMException {
        String system = "You are the decision component of an autonomous code review agent. "
                + "You choose exactly one next action per turn and you reply with JSON only.";

        String user = "GOAL\n" + goal + "\n\n"
                + "YOUR PLAN\n" + plan + "\n\n"
                + "AVAILABLE TOOLS\n" + tools.describeForPrompt() + "\n"
                + "WHAT YOU HAVE OBSERVED SO FAR\n" + memory.renderShortTerm(12) + "\n\n"
                + "BUDGET\nThis is step " + stepNumber + " of at most " + budget
                + ". Findings are only recorded by analyze_file, so make sure you have called it "
                + "on the files that matter before you finish.\n\n"
                + "Decide the single next action. Reply with JSON only, in one of these shapes:\n"
                + "{\"thought\":\"<one sentence on why>\",\"tool\":\"<tool name>\",\"input\":\"<tool input>\"}\n"
                + "or, when the goal has been met:\n"
                + "{\"thought\":\"<why you are finished>\",\"final_answer\":\"<your findings summary>\"}\n\n"
                + "Rules: never repeat an action already listed in your observations; prefer "
                + "analyze_file over read_file when you intend to report problems; if a tool "
                + "returned an error, do something different rather than retrying it unchanged.";

        List<ChatMessage> messages = List.of(ChatMessage.system(system), ChatMessage.user(user));
        String raw = LLMClient.getInstance().sendConversation(messages, "agent-decision", 0.1);
        return Json.parseObject(raw);
    }

    /** Phase 5: write the closing synthesis over everything gathered. */
    private String synthesise(String goal) throws LLMException {
        String system = "You are the reporting component of an autonomous code review agent. "
                + "You summarise what was examined and what was found, without inventing "
                + "anything that is not in the observations.";

        String user = "GOAL\n" + goal + "\n\n"
                + "EVERYTHING YOU OBSERVED\n" + memory.renderShortTerm(20) + "\n\n"
                + "Write a short report for the developer covering:\n"
                + "1. What you examined and why you chose those files.\n"
                + "2. The most serious problems found, in priority order.\n"
                + "3. What you did not get to, if the step budget ran out.\n"
                + "4. The single thing you would fix first.\n"
                + "Use plain prose with short paragraphs. Do not use JSON.";

        return LLMClient.getInstance().sendPrompt(system, user, "agent-synthesis");
    }

    // ------------------------------------------------------------------ helpers

    private void emit(AgentStep step) {
        trace.add(step);
        Consumer<AgentStep> listener = stepListener;
        if (listener != null) {
            listener.accept(step);
        }
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "\n... (truncated)";
    }

    /** The full trace of the most recent run, included in the exported report. */
    public List<AgentStep> getTrace() {
        return new ArrayList<>(trace);
    }

    /** The trace rendered as text, for the CLI and for exports. */
    public String renderTrace() {
        StringBuilder sb = new StringBuilder();
        for (AgentStep step : trace) {
            sb.append(step.renderFull()).append('\n');
        }
        return sb.toString();
    }
}
