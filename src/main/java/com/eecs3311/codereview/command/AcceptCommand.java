package com.eecs3311.codereview.command;

import com.eecs3311.codereview.model.ReviewItem;
import com.eecs3311.codereview.model.ReviewReport;
import com.eecs3311.codereview.model.ReviewStatus;

/**
 * COMMAND PATTERN - concrete command: the developer agrees with a finding (feature F07).
 *
 * <p>Acts as the Memento caretaker: it takes a snapshot of the finding's triage state before
 * changing it, holds that snapshot without being able to read inside it, and hands it back on
 * undo.</p>
 */
public class AcceptCommand implements ReviewCommand {

    private final ReviewItem item;
    private final ReviewReport report;
    private final String note;
    private ReviewItem.Memento previousState;

    /**
     * @param item   the finding being accepted
     * @param report the report to notify, so both views refresh
     * @param note   an optional comment, such as which commit fixed it
     */
    public AcceptCommand(ReviewItem item, ReviewReport report, String note) {
        this.item = item;
        this.report = report;
        this.note = note == null ? "" : note.trim();
    }

    @Override
    public void execute() {
        previousState = item.createMemento();
        item.updateStatus(ReviewStatus.ACCEPTED, note);
        report.itemChanged(item);
    }

    @Override
    public void undo() {
        item.restore(previousState);
        report.itemChanged(item);
    }

    @Override
    public String getDescription() {
        return "Accepted " + item.getIssueId() + " (" + item.getTitle() + ")"
                + (note.isEmpty() ? "" : " - " + note);
    }

    /** The finding this command acted on, so the history can show it. */
    public ReviewItem getItem() {
        return item;
    }
}
