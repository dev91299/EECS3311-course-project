package com.eecs3311.codereview;

import com.eecs3311.codereview.model.DeltaReport;
import com.eecs3311.codereview.model.ReviewItem;
import com.eecs3311.codereview.model.ReviewReport;
import com.eecs3311.codereview.model.ReviewSnapshot;
import com.eecs3311.codereview.model.ReviewStatus;
import com.eecs3311.codereview.model.Severity;
import com.eecs3311.codereview.observer.Observer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the Observer pattern and the aggregation and comparison logic that features F08
 * and F09 depend on.
 */
class ObserverAndReportTest {

    /** A test double standing in for a view. */
    private static class RecordingObserver implements Observer {
        private final List<UpdateEvent> events = new ArrayList<>();
        private int callCount;

        @Override
        public void updateUI(UpdateEvent event, Object payload) {
            events.add(event);
            callCount++;
        }
    }

    /** An observer that always throws, to prove one bad view cannot block the others. */
    private static class FaultyObserver implements Observer {
        @Override
        public void updateUI(UpdateEvent event, Object payload) {
            throw new IllegalStateException("this view is broken");
        }
    }

    private ReviewReport report;

    @BeforeEach
    void setUp() {
        report = new ReviewReport();
    }

    private ReviewItem item(String file, Severity severity, String title) {
        return new ReviewItem(file, 10, severity, title, "description", "fix", "Security", "security");
    }

    @Test
    void observersAreNotifiedWhenFindingsAreAdded() {
        RecordingObserver observer = new RecordingObserver();
        report.addObserver(observer);

        report.addItem(item("/A.java", Severity.HIGH, "Leak"));

        assertEquals(1, observer.callCount);
        assertEquals(Observer.UpdateEvent.FINDINGS_ADDED, observer.events.get(0));
    }

    @Test
    void aBatchNotifiesOnceNotOncePerFinding() {
        RecordingObserver observer = new RecordingObserver();
        report.addObserver(observer);

        report.addItems(List.of(
                item("/A.java", Severity.HIGH, "One"),
                item("/A.java", Severity.LOW, "Two"),
                item("/B.java", Severity.INFO, "Three")));

        assertEquals(1, observer.callCount, "batching must collapse to a single repaint");
        assertEquals(3, report.getTotalIssues());
    }

    @Test
    void everyRegisteredObserverIsNotified() {
        RecordingObserver first = new RecordingObserver();
        RecordingObserver second = new RecordingObserver();
        report.addObserver(first);
        report.addObserver(second);

        report.addItem(item("/A.java", Severity.HIGH, "Leak"));

        assertEquals(1, first.callCount);
        assertEquals(1, second.callCount);
        assertEquals(2, report.getObserverCount());
    }

    @Test
    void aFailingObserverDoesNotPreventTheOthersFromUpdating() {
        report.addObserver(new FaultyObserver());
        RecordingObserver healthy = new RecordingObserver();
        report.addObserver(healthy);

        report.addItem(item("/A.java", Severity.HIGH, "Leak"));

        assertEquals(1, healthy.callCount, "the healthy view must still be updated");
    }

    @Test
    void removedObserversStopReceivingEvents() {
        RecordingObserver observer = new RecordingObserver();
        report.addObserver(observer);
        report.removeObserver(observer);

        report.addItem(item("/A.java", Severity.HIGH, "Leak"));
        assertEquals(0, observer.callCount);
    }

    @Test
    void duplicateRegistrationIsIgnored() {
        RecordingObserver observer = new RecordingObserver();
        report.addObserver(observer);
        report.addObserver(observer);
        assertEquals(1, report.getObserverCount());
    }

    @Test
    void aggregateItemsCountsStatusesAndSeverities() {
        ReviewItem critical = item("/A.java", Severity.CRITICAL, "Injection");
        ReviewItem low = item("/B.java", Severity.LOW, "Naming");
        report.addItems(List.of(critical, low));
        critical.updateStatus(ReviewStatus.ACCEPTED, "will fix");

        Map<String, Integer> summary = report.aggregateItems();
        assertEquals(2, summary.get("total"));
        assertEquals(1, summary.get("accepted"));
        assertEquals(1, summary.get("pending"));
        assertEquals(1, summary.get("Critical"));
        assertEquals(1, summary.get("Low"));
    }

    @Test
    void sortingPutsTheMostSevereFirst() {
        report.addItems(List.of(
                item("/A.java", Severity.INFO, "Note"),
                item("/A.java", Severity.CRITICAL, "Injection"),
                item("/A.java", Severity.MEDIUM, "Smell")));

        List<ReviewItem> sorted = report.getSorted();
        assertEquals(Severity.CRITICAL, sorted.get(0).getSeverity());
        assertEquals(Severity.INFO, sorted.get(2).getSeverity());
    }

    @Test
    void findByIdAcceptsTheIdWithOrWithoutItsPrefix() {
        ReviewItem finding = item("/A.java", Severity.HIGH, "Leak");
        report.addItem(finding);
        String id = finding.getIssueId();

        assertTrue(report.findById(id).isPresent());
        assertTrue(report.findById(id.substring(4)).isPresent());
        assertTrue(report.findById(id.toLowerCase()).isPresent());
        assertTrue(report.findById("ISS-000000").isEmpty());
    }

    @Test
    void healthVerdictEscalatesWithSeverity() {
        assertTrue(report.getHealthVerdict().contains("No findings"));

        report.addItem(item("/A.java", Severity.LOW, "Naming"));
        assertTrue(report.getHealthVerdict().contains("Healthy"));

        report.addItem(item("/A.java", Severity.CRITICAL, "Injection"));
        assertTrue(report.getHealthVerdict().contains("Action required"));
    }

    @Test
    void deltaSeparatesResolvedNewAndPersistingFindings() {
        ReviewReport before = new ReviewReport();
        before.addItems(List.of(
                item("/A.java", Severity.CRITICAL, "SQL injection"),
                item("/A.java", Severity.LOW, "Naming")));
        ReviewSnapshot snapshotA = before.snapshot("v1", "aaa111");

        ReviewReport after = new ReviewReport();
        after.addItems(List.of(
                item("/A.java", Severity.LOW, "Naming"),
                item("/B.java", Severity.HIGH, "Resource leak")));
        ReviewSnapshot snapshotB = after.snapshot("v2", "bbb222");

        DeltaReport delta = new DeltaReport(snapshotA, snapshotB);

        assertEquals(1, delta.getResolved().size());
        assertEquals("SQL injection", delta.getResolved().get(0).getTitle());
        assertEquals(1, delta.getIntroduced().size());
        assertEquals("Resource leak", delta.getIntroduced().get(0).getTitle());
        assertEquals(1, delta.getPersisting().size());
        assertEquals(0, delta.getNetChange());
    }

    @Test
    void deltaIgnoresLineNumberDriftWhenMatchingFindings() {
        ReviewReport before = new ReviewReport();
        before.addItem(new ReviewItem("/A.java", 10, Severity.HIGH, "Resource leak",
                "", "", "Security", "security"));

        ReviewReport after = new ReviewReport();
        after.addItem(new ReviewItem("/A.java", 57, Severity.HIGH, "Resource leak",
                "", "", "Security", "security"));

        DeltaReport delta = new DeltaReport(before.snapshot("v1", ""), after.snapshot("v2", ""));

        assertEquals(0, delta.getResolved().size(),
                "the same defect moved down the file is not a resolution");
        assertEquals(1, delta.getPersisting().size());
    }

    @Test
    void verdictReportsImprovementWhenFindingsFall() {
        ReviewReport before = new ReviewReport();
        before.addItems(List.of(
                item("/A.java", Severity.CRITICAL, "One"),
                item("/A.java", Severity.HIGH, "Two")));
        ReviewReport after = new ReviewReport();
        after.addItem(item("/A.java", Severity.HIGH, "Two"));

        DeltaReport delta = new DeltaReport(before.snapshot("v1", ""), after.snapshot("v2", ""));
        assertTrue(delta.getVerdict().startsWith("Improved"));
        assertTrue(delta.render().contains("RESOLVED"));
    }
}
