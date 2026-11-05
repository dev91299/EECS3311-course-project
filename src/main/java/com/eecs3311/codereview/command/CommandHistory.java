package com.eecs3311.codereview.command;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

/**
 * COMMAND PATTERN - the invoker, and the implementation of feature F12 (undo and history).
 *
 * <p>Holds two stacks. Executing a command pushes it onto the undo stack and clears the redo
 * stack, which is the standard behaviour: once you take a new action after undoing, the
 * branch you undid is no longer reachable. Undo pops from one stack and pushes onto the
 * other; redo does the reverse.</p>
 *
 * <p>A separate append-only log records every action with a timestamp, so the history remains
 * complete even after an undo. That log is the "saved feedback log" feature F07 specifies and
 * it is included in every exported report.</p>
 */
public class CommandHistory {

    /** One entry in the append-only action log. */
    public static class LogEntry {
        private final Instant at;
        private final String description;
        private final String action;

        LogEntry(String action, String description) {
            this.at = Instant.now();
            this.action = action;
            this.description = description;
        }

        public Instant getAt() {
            return at;
        }

        public String getDescription() {
            return description;
        }

        public String getAction() {
            return action;
        }

        /** Renders the entry as "HH:mm:ss  ACTION  description". */
        public String render() {
            return DateTimeFormatter.ofPattern("HH:mm:ss")
                    .withZone(ZoneId.systemDefault()).format(at)
                    + "  " + String.format("%-8s", action) + "  " + description;
        }
    }

    private static final int MAX_DEPTH = 100;

    private final Deque<ReviewCommand> undoStack = new ArrayDeque<>();
    private final Deque<ReviewCommand> redoStack = new ArrayDeque<>();
    private final List<LogEntry> log = new ArrayList<>();

    /** Runs a command and records it. */
    public void execute(ReviewCommand command) {
        if (command == null) {
            return;
        }
        command.execute();
        undoStack.push(command);
        redoStack.clear();
        while (undoStack.size() > MAX_DEPTH) {
            undoStack.removeLast();
        }
        log.add(new LogEntry("DO", command.getDescription()));
    }

    /**
     * Reverses the most recent command.
     *
     * @return the command that was undone, or empty when there was nothing to undo
     */
    public Optional<ReviewCommand> undo() {
        if (undoStack.isEmpty()) {
            return Optional.empty();
        }
        ReviewCommand command = undoStack.pop();
        command.undo();
        redoStack.push(command);
        log.add(new LogEntry("UNDO", command.getDescription()));
        return Optional.of(command);
    }

    /**
     * Re-applies the most recently undone command.
     *
     * @return the command that was redone, or empty when there was nothing to redo
     */
    public Optional<ReviewCommand> redo() {
        if (redoStack.isEmpty()) {
            return Optional.empty();
        }
        ReviewCommand command = redoStack.pop();
        command.execute();
        undoStack.push(command);
        log.add(new LogEntry("REDO", command.getDescription()));
        return Optional.of(command);
    }

    /** True when there is something to undo; drives the toolbar button's enabled state. */
    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    /** True when there is something to redo. */
    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    /** Description of the next undo, used for the button tooltip. */
    public String peekUndoDescription() {
        return undoStack.isEmpty() ? "" : undoStack.peek().getDescription();
    }

    /** Description of the next redo. */
    public String peekRedoDescription() {
        return redoStack.isEmpty() ? "" : redoStack.peek().getDescription();
    }

    /** The complete action log, oldest first. */
    public List<LogEntry> getLog() {
        return new ArrayList<>(log);
    }

    /** The action log rendered as text, for the CLI and for exported reports. */
    public String renderLog() {
        if (log.isEmpty()) {
            return "No review actions have been taken in this session.";
        }
        StringBuilder sb = new StringBuilder();
        for (LogEntry entry : log) {
            sb.append(entry.render()).append('\n');
        }
        return sb.toString();
    }

    /** Number of actions taken, shown in the status bar. */
    public int size() {
        return log.size();
    }

    /** Clears both stacks and the log, for example when a new repository is imported. */
    public void clear() {
        undoStack.clear();
        redoStack.clear();
        log.clear();
    }
}
