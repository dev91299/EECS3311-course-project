package com.eecs3311.codereview.agent.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The set of tools available to the autonomous agent.
 *
 * <p>{@link #describeForPrompt()} renders the catalogue into the planner's prompt. Because
 * the description is generated from the registry rather than written by hand, a tool that is
 * added or removed is automatically reflected in what the model is told it can do - there is
 * no second list to keep in step.</p>
 */
public class ToolRegistry {

    private final Map<String, AgentTool> tools = new LinkedHashMap<>();

    /** Adds a tool, replacing any existing one with the same name. */
    public void register(AgentTool tool) {
        if (tool != null) {
            tools.put(tool.getName().toLowerCase(), tool);
        }
    }

    /** Looks a tool up by name. */
    public Optional<AgentTool> find(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(tools.get(name.trim().toLowerCase()));
    }

    /** Every registered tool. */
    public List<AgentTool> all() {
        return new ArrayList<>(tools.values());
    }

    /** Registered tool names. */
    public List<String> names() {
        return new ArrayList<>(tools.keySet());
    }

    /** The catalogue as the planner sees it. */
    public String describeForPrompt() {
        StringBuilder sb = new StringBuilder();
        for (AgentTool tool : tools.values()) {
            sb.append("- ").append(tool.getName()).append(": ").append(tool.getDescription())
              .append("\n    input: ").append(tool.getInputDescription()).append('\n');
        }
        return sb.toString();
    }

    /**
     * Runs a tool by name, returning a readable error rather than throwing when the name is
     * unknown. The agent then sees that error as an observation and can correct itself,
     * which is a large part of why a wrong tool name does not end a run.
     */
    public String execute(String toolName, String input) {
        Optional<AgentTool> tool = find(toolName);
        if (tool.isEmpty()) {
            return "ERROR: there is no tool called '" + toolName + "'. Available tools: "
                    + String.join(", ", names()) + ".";
        }
        try {
            return tool.get().execute(input);
        } catch (RuntimeException e) {
            return "ERROR: the tool '" + toolName + "' failed unexpectedly: " + e.getMessage();
        }
    }
}
