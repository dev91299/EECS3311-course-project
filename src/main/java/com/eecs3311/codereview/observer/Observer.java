package com.eecs3311.codereview.observer;

/**
 * OBSERVER PATTERN - Observer role.
 *
 * <p>Implemented by every view that must refresh when the review state changes
 * ({@code CodeReviewGUI} and {@code CLIView}). Views register themselves with a
 * {@link Subject} and are pushed updates; they never poll.</p>
 *
 * <p><strong>Change from Stage 1:</strong> the Stage 1 design declared
 * {@code updateUI(): void} with no parameters, which would have forced every observer to
 * reach back into the subject to discover what changed. The implemented signature passes
 * the event that occurred and its payload, so a view can repaint only the affected region
 * (for example, appending one agent step instead of rebuilding the whole findings table).</p>
 */
public interface Observer {

    /**
     * Called by the subject after its state changes.
     *
     * @param event   the kind of change that occurred
     * @param payload event-specific data, may be {@code null}
     */
    void updateUI(UpdateEvent event, Object payload);

    /** The kinds of state change a subject can broadcast. */
    enum UpdateEvent {
        /** A new set of findings was added to the report. */
        FINDINGS_ADDED,
        /** A single finding changed status (accepted, rejected, severity changed). */
        ITEM_CHANGED,
        /** All findings were cleared. */
        REPORT_CLEARED,
        /** A repository was imported and the file tree is available. */
        REPOSITORY_LOADED,
        /** The autonomous agent completed one reasoning step. */
        AGENT_STEP,
        /** A long-running operation started; payload is a status message. */
        TASK_STARTED,
        /** A long-running operation finished; payload is a status message. */
        TASK_FINISHED,
        /** An error occurred; payload is the message to show the user. */
        ERROR,
        /** A chat message was added to the conversation. */
        CHAT_UPDATED,
        /** A report file was exported; payload is the path. */
        EXPORT_COMPLETE
    }
}
