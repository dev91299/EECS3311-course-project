package com.eecs3311.codereview.agent.tools;

/**
 * One capability the autonomous agent can invoke during a multi-step run.
 *
 * <p>This is the "tool use" half of the agent requirement. The planner does not call methods
 * on the application directly; it chooses a tool by name and passes it a string, exactly as
 * it would call an external API. That indirection is what makes the agent's behaviour
 * inspectable - every action it takes appears in the trace as a named tool and its input -
 * and what keeps the model's output from being executed as code.</p>
 *
 * <p>The string-in, string-out contract is deliberate. Everything the model produces is
 * untrusted text, so a tool must parse and validate its own input; the file tools in
 * particular resolve paths through {@code RepoManager}, which refuses anything outside the
 * imported repository.</p>
 */
public interface AgentTool {

    /** The name the planner uses to select this tool, for example {@code read_file}. */
    String getName();

    /** What the tool does; included in the prompt so the planner can choose correctly. */
    String getDescription();

    /** What the tool expects as input; also included in the prompt. */
    String getInputDescription();

    /**
     * Runs the tool.
     *
     * <p>Implementations never throw: a failure is returned as a readable message so the
     * agent can observe it, reason about it and try something else, which is the behaviour a
     * thrown exception would prevent.</p>
     *
     * @param input the planner's argument, possibly empty
     * @return the observation to feed back into the reasoning loop
     */
    String execute(String input);
}
