package com.eecs3311.codereview.command;

import com.eecs3311.codereview.model.ReviewItem;
import com.eecs3311.codereview.model.ReviewReport;
import com.eecs3311.codereview.model.ReviewStatus;

/**
 * COMMAND PATTERN - concrete command: the developer dismisses a finding (feature F07).
 *
 * <p>The reason the developer gives is the valuable part. It is stored on the finding and
 * then written into the agent's long-term memory, so the same objection does not have to be
 * raised twice on the same project. Capturing that reason is why reject is a separate command
 * from accept rather than one command with a status parameter.</p>
 */
public class RejectCommand implements ReviewCommand {

    private final ReviewItem item;
    private final ReviewReport report;
    private final String feedback;
    private ReviewItem.Memento previousState;

    /**
     * @param item     the finding being dismissed
     * @param report   the report to notify
     * @param feedback why the team disagrees; fed back into agent memory
     */
    public RejectCommand(ReviewItem item, ReviewReport report, String feedback) {
        this.item = item;
        this.report = report;
        this.feedback = feedback == null || feedback.isBlank()
                ? "No reason given" : feedback.trim();
    }

    @Override
    public void execute() {
        previousState = item.createMemento();
        item.updateStatus(ReviewStatus.REJECTED, feedback);
        report.itemChanged(item);
    }

    @Override
    public void undo() {
        item.restore(previousState);
        report.itemChanged(item);
    }

    @Override
    public String getDescription() {
        return "Rejected " + item.getIssueId() + " (" + item.getTitle() + ") - " + feedback;
    }

    public ReviewItem getItem() {
        return item;
    }

    /** The reason given, read by the controller when updating agent memory. */
    public String getFeedback() {
        return feedback;
    }
}
