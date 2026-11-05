package com.eecs3311.codereview.command;

import java.util.ArrayList;
import java.util.List;

/**
 * COMMAND PATTERN - composite command that groups several commands into one undoable action.
 *
 * <p>Triaging a long findings list one entry at a time is tedious, so both interfaces offer
 * "reject all Info findings" and similar bulk operations. Implementing that as a list of
 * commands rather than a loop in the controller means the whole batch undoes as a single
 * step, which is what a user expects after one click.</p>
 *
 * <p>Undo runs in reverse order, so each command restores the state the one before it saw.</p>
 */
public class BulkTriageCommand implements ReviewCommand {

    private final List<ReviewCommand> commands;
    private final String label;

    /**
     * @param label    description of the batch, such as "Reject all Info findings"
     * @param commands the commands to run, in order
     */
    public BulkTriageCommand(String label, List<ReviewCommand> commands) {
        this.label = label == null ? "Bulk action" : label;
        this.commands = commands == null ? new ArrayList<>() : new ArrayList<>(commands);
    }

    @Override
    public void execute() {
        for (ReviewCommand command : commands) {
            command.execute();
        }
    }

    @Override
    public void undo() {
        for (int i = commands.size() - 1; i >= 0; i--) {
            commands.get(i).undo();
        }
    }

    @Override
    public String getDescription() {
        return label + " (" + commands.size() + " finding"
                + (commands.size() == 1 ? "" : "s") + ")";
    }

    /** How many commands the batch contains. */
    public int size() {
        return commands.size();
    }

    /** The individual commands, so the controller can update memory for each. */
    public List<ReviewCommand> getCommands() {
        return new ArrayList<>(commands);
    }
}
