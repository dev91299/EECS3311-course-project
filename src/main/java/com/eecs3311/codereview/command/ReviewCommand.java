package com.eecs3311.codereview.command;

/**
 * COMMAND PATTERN - the Command interface.
 *
 * <p>Every action a developer can take on a finding is represented as an object rather than
 * executed inline in a button handler. That buys three things the Stage 1 design wanted and
 * one it did not have:</p>
 * <ul>
 *   <li>the GUI button and the CLI command both construct the same object, so the two
 *       interfaces cannot drift in behaviour;</li>
 *   <li>the action can be logged with a description, which is the feedback history;</li>
 *   <li>the action can be undone, because each command captures the state it is about to
 *       change before changing it (feature F12, new in Stage 2).</li>
 * </ul>
 *
 * <p>Undo is what required adding {@link #undo()} to the Stage 1 signature, which declared
 * only {@code execute()}.</p>
 */
public interface ReviewCommand {

    /** Performs the action. */
    void execute();

    /**
     * Reverses the action, restoring exactly the state that existed before
     * {@link #execute()} ran.
     */
    void undo();

    /** A human-readable description of what this command did, shown in the action history. */
    String getDescription();
}
