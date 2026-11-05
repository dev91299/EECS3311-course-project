package com.eecs3311.codereview.agent;

/**
 * One step in the agent's reasoning trace.
 *
 * <p>Every decision the agent makes is recorded as one of these and pushed to the views as
 * it happens, so the user watches the agent work rather than waiting at a progress bar.
 * The trace is also what makes the agent auditable: a reader of the demonstration video can
 * see exactly which file was read, which tool was chosen and why.</p>
 */
public class AgentStep {

    /** What kind of step this is. */
    public enum Type {
        /** The agent's initial plan for reaching the goal. */
        PLAN,
        /** The reasoning that preceded an action. */
        THOUGHT,
        /** A tool invocation. */
        ACTION,
        /** The result the tool returned. */
        OBSERVATION,
        /** The agent's closing synthesis. */
        CONCLUSION,
        /** Something went wrong at this step. */
        ERROR
    }

    private final int stepNumber;
    private final Type type;
    private final String title;
    private final String detail;

    public AgentStep(int stepNumber, Type type, String title, String detail) {
        this.stepNumber = stepNumber;
        this.type = type;
        this.title = title == null ? "" : title;
        this.detail = detail == null ? "" : detail;
    }

    public int getStepNumber() {
        return stepNumber;
    }

    public Type getType() {
        return type;
    }

    /** Short headline, for example the tool name. */
    public String getTitle() {
        return title;
    }

    /** The full text of the thought, the tool input, or the observation. */
    public String getDetail() {
        return detail;
    }

    /** A one-line rendering used by the CLI trace. */
    public String renderLine() {
        String prefix = String.format("[step %d] %-11s ", stepNumber, type);
        String body = title.isEmpty() ? detail : title + (detail.isEmpty() ? "" : ": " + detail);
        return prefix + collapse(body, 160);
    }

    /** The full rendering used by the GUI trace panel and the exported report. */
    public String renderFull() {
        StringBuilder sb = new StringBuilder();
        sb.append("[step ").append(stepNumber).append("] ").append(type);
        if (!title.isEmpty()) {
            sb.append(" - ").append(title);
        }
        sb.append('\n');
        if (!detail.isEmpty()) {
            sb.append(indent(detail)).append('\n');
        }
        return sb.toString();
    }

    private static String indent(String text) {
        StringBuilder sb = new StringBuilder();
        for (String line : text.split("\n")) {
            sb.append("    ").append(line).append('\n');
        }
        return sb.toString().stripTrailing();
    }

    private static String collapse(String text, int max) {
        String single = text.replaceAll("\\s+", " ").trim();
        return single.length() <= max ? single : single.substring(0, max) + "...";
    }

    @Override
    public String toString() {
        return renderLine();
    }
}
