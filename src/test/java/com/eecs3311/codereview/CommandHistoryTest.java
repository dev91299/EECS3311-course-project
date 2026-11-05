package com.eecs3311.codereview;

import com.eecs3311.codereview.command.AcceptCommand;
import com.eecs3311.codereview.command.BulkTriageCommand;
import com.eecs3311.codereview.command.ChangeSeverityCommand;
import com.eecs3311.codereview.command.CommandHistory;
import com.eecs3311.codereview.command.RejectCommand;
import com.eecs3311.codereview.command.ReviewCommand;
import com.eecs3311.codereview.model.ReviewItem;
import com.eecs3311.codereview.model.ReviewReport;
import com.eecs3311.codereview.model.ReviewStatus;
import com.eecs3311.codereview.model.Severity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the Command pattern together with the Memento that backs its undo.
 *
 * <p>The key property under test is that undo restores the <em>exact</em> prior state,
 * including a feedback comment that was set by an earlier command, rather than resetting the
 * finding to a default.</p>
 */
class CommandHistoryTest {

    private ReviewReport report;
    private ReviewItem finding;
    private CommandHistory history;

    @BeforeEach
    void setUp() {
        report = new ReviewReport();
        finding = new ReviewItem("/project/App.java", 42, Severity.HIGH,
                "Possible null dereference", "The reference is used before the null test.",
                "Reorder the condition.", "Correctness", "security");
        report.addItem(finding);
        history = new CommandHistory();
    }

    @Test
    void acceptChangesStatusAndIsUndoable() {
        history.execute(new AcceptCommand(finding, report, "fixed in commit a1b2c3"));

        assertEquals(ReviewStatus.ACCEPTED, finding.getStatus());
        assertEquals("fixed in commit a1b2c3", finding.getFeedback());
        assertTrue(history.canUndo());

        history.undo();
        assertEquals(ReviewStatus.PENDING, finding.getStatus());
        assertEquals("", finding.getFeedback());
    }

    @Test
    void rejectStoresTheReason() {
        history.execute(new RejectCommand(finding, report, "this path is unreachable"));
        assertEquals(ReviewStatus.REJECTED, finding.getStatus());
        assertEquals("this path is unreachable", finding.getFeedback());
    }

    @Test
    void rejectWithoutAReasonStillRecordsSomething() {
        history.execute(new RejectCommand(finding, report, "   "));
        assertEquals("No reason given", finding.getFeedback());
    }

    @Test
    void undoRestoresThePreviousCommandsStateNotTheDefault() {
        history.execute(new AcceptCommand(finding, report, "first decision"));
        history.execute(new RejectCommand(finding, report, "changed my mind"));

        assertEquals(ReviewStatus.REJECTED, finding.getStatus());

        history.undo();

        // The memento must restore the ACCEPTED state with its original note.
        assertEquals(ReviewStatus.ACCEPTED, finding.getStatus());
        assertEquals("first decision", finding.getFeedback());
    }

    @Test
    void redoReappliesTheUndoneCommand() {
        history.execute(new AcceptCommand(finding, report, "note"));
        history.undo();
        assertEquals(ReviewStatus.PENDING, finding.getStatus());

        history.redo();
        assertEquals(ReviewStatus.ACCEPTED, finding.getStatus());
        assertEquals("note", finding.getFeedback());
    }

    @Test
    void executingAfterUndoClearsTheRedoBranch() {
        history.execute(new AcceptCommand(finding, report, "a"));
        history.undo();
        assertTrue(history.canRedo());

        history.execute(new RejectCommand(finding, report, "b"));
        assertFalse(history.canRedo(), "a new action must discard the redo branch");
    }

    @Test
    void severityChangeIsUndoable() {
        history.execute(new ChangeSeverityCommand(finding, report, Severity.LOW));
        assertEquals(Severity.LOW, finding.getSeverity());

        history.undo();
        assertEquals(Severity.HIGH, finding.getSeverity());
    }

    @Test
    void bulkTriageUndoesAsASingleStep() {
        List<ReviewItem> items = new ArrayList<>();
        List<ReviewCommand> commands = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            ReviewItem item = new ReviewItem("/project/File" + i + ".java", i, Severity.INFO,
                    "Informational finding " + i, "", "", "Style", "style");
            report.addItem(item);
            items.add(item);
            commands.add(new RejectCommand(item, report, "bulk dismissed"));
        }

        history.execute(new BulkTriageCommand("Reject all Info findings", commands));
        items.forEach(item -> assertEquals(ReviewStatus.REJECTED, item.getStatus()));

        history.undo();
        items.forEach(item -> assertEquals(ReviewStatus.PENDING, item.getStatus()));
        assertFalse(history.canUndo(), "the batch counted as exactly one undo step");
    }

    @Test
    void undoOnAnEmptyHistoryIsSafe() {
        assertTrue(history.undo().isEmpty());
        assertTrue(history.redo().isEmpty());
    }

    @Test
    void logRecordsEveryActionIncludingUndo() {
        history.execute(new AcceptCommand(finding, report, "note"));
        history.undo();

        assertEquals(2, history.size());
        String rendered = history.renderLog();
        assertTrue(rendered.contains("DO"));
        assertTrue(rendered.contains("UNDO"));
        assertTrue(rendered.contains(finding.getIssueId()));
    }
}
