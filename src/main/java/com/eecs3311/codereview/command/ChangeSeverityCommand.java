package com.eecs3311.codereview.command;

import com.eecs3311.codereview.model.ReviewItem;
import com.eecs3311.codereview.model.ReviewReport;
import com.eecs3311.codereview.model.Severity;

/**
 * COMMAND PATTERN - concrete command: re-grade a finding's severity.
 *
 * <p>Added during implementation. In practice the model's severity judgement is the thing
 * reviewers most often disagree with - a finding is real but is not critical for this
 * project. Before this command the only options were to accept a severity the team
 * considered wrong, or to reject a finding the team considered valid.</p>
 *
 * <p>It demonstrates what the Command pattern is worth: a new action needed one class and
 * one menu entry, and it inherited logging and undo for free.</p>
 */
public class ChangeSeverityCommand implements ReviewCommand {

    private final ReviewItem item;
    private final ReviewReport report;
    private final Severity newSeverity;
    private final Severity oldSeverity;
    private ReviewItem.Memento previousState;

    public ChangeSeverityCommand(ReviewItem item, ReviewReport report, Severity newSeverity) {
        this.item = item;
        this.report = report;
        this.newSeverity = newSeverity;
        this.oldSeverity = item.getSeverity();
    }

    @Override
    public void execute() {
        previousState = item.createMemento();
        item.setSeverity(newSeverity);
        report.itemChanged(item);
    }

    @Override
    public void undo() {
        item.restore(previousState);
        report.itemChanged(item);
    }

    @Override
    public String getDescription() {
        return "Re-graded " + item.getIssueId() + " from " + oldSeverity.getLabel()
                + " to " + newSeverity.getLabel();
    }

    public ReviewItem getItem() {
        return item;
    }
}
