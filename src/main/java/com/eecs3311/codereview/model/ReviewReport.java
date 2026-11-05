package com.eecs3311.codereview.model;

import com.eecs3311.codereview.observer.Observer;
import com.eecs3311.codereview.observer.Subject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The aggregate of every finding in the current review session.
 *
 * <p><strong>OBSERVER PATTERN - Concrete Subject role.</strong> This class owns the
 * authoritative finding list. Whenever findings are added, cleared or triaged it notifies
 * its observers, which is how the Swing window and the console view stay in step without
 * either of them knowing the other exists, and without any polling loop.</p>
 *
 * <p>It is also the source of the exported report (feature F09) and of the per-version
 * snapshots used for cross-version comparison (feature F08).</p>
 */
public class ReviewReport extends Subject {

    private final List<ReviewItem> items = new ArrayList<>();
    private final Instant createdAt = Instant.now();
    private String repositoryPath = "";
    private String sessionLabel = "session";

    /** Adds one finding and notifies views. Exact duplicates are discarded. */
    public synchronized void addItem(ReviewItem item) {
        if (item == null || isDuplicate(item)) {
            return;
        }
        items.add(item);
        notifyObservers(Observer.UpdateEvent.FINDINGS_ADDED, List.of(item));
    }

    /**
     * Adds a batch of findings and notifies views once. Batching matters because an
     * analysis run can produce dozens of findings and repainting the table per finding
     * would make the GUI stutter.
     *
     * <p>Duplicates are discarded. This matters in practice: the autonomous audit often
     * analyses a file the developer has already reviewed by hand, and without this check the
     * findings table would show every issue twice, each with a different identifier, so
     * triaging one would leave the other outstanding.</p>
     */
    public synchronized void addItems(List<ReviewItem> newItems) {
        if (newItems == null || newItems.isEmpty()) {
            return;
        }
        List<ReviewItem> accepted = new ArrayList<>();
        for (ReviewItem item : newItems) {
            if (item != null && !isDuplicate(item)) {
                items.add(item);
                accepted.add(item);
            }
        }
        if (accepted.isEmpty()) {
            return;
        }
        notifyObservers(Observer.UpdateEvent.FINDINGS_ADDED, accepted);
    }

    /**
     * True when the same problem at the same place is already recorded.
     *
     * <p>Identity is file, category, headline and line number together. The line number is
     * part of the key here, unlike in {@link ReviewItem#getFingerprint()}, because two
     * occurrences of the same defect on different lines of one file are two real findings
     * that each need triaging.</p>
     */
    private boolean isDuplicate(ReviewItem candidate) {
        for (ReviewItem existing : items) {
            if (existing.getLineNumber() == candidate.getLineNumber()
                    && existing.getFingerprint().equals(candidate.getFingerprint())) {
                return true;
            }
        }
        return false;
    }

    /** Removes every finding, for example when a different repository is imported. */
    public synchronized void clear() {
        items.clear();
        notifyObservers(Observer.UpdateEvent.REPORT_CLEARED, null);
    }

    /** Announces that one finding changed, after a command mutated it. */
    public void itemChanged(ReviewItem item) {
        notifyObservers(Observer.UpdateEvent.ITEM_CHANGED, item);
    }

    /** Returns a defensive copy of all findings. */
    public synchronized List<ReviewItem> getItems() {
        return new ArrayList<>(items);
    }

    /** Total number of findings; the {@code totalIssues} attribute from the Stage 1 design. */
    public synchronized int getTotalIssues() {
        return items.size();
    }

    /** Finds a finding by its identifier, which is how the CLI addresses findings. */
    public synchronized Optional<ReviewItem> findById(String issueId) {
        if (issueId == null) {
            return Optional.empty();
        }
        String wanted = issueId.trim();
        return items.stream()
                .filter(i -> i.getIssueId().equalsIgnoreCase(wanted)
                        || i.getIssueId().equalsIgnoreCase("ISS-" + wanted))
                .findFirst();
    }

    /** Returns findings matching a severity, sorted most severe first. */
    public synchronized List<ReviewItem> getBySeverity(Severity severity) {
        return items.stream()
                .filter(i -> i.getSeverity() == severity)
                .collect(Collectors.toList());
    }

    /** Returns findings for one file. */
    public synchronized List<ReviewItem> getByFile(String filePath) {
        return items.stream()
                .filter(i -> i.getFilePath().equals(filePath))
                .collect(Collectors.toList());
    }

    /** Returns only findings the developer has not yet triaged. */
    public synchronized List<ReviewItem> getPending() {
        return items.stream()
                .filter(i -> i.getStatus() == ReviewStatus.PENDING)
                .collect(Collectors.toList());
    }

    /**
     * Aggregates the session into summary statistics. This is the {@code aggregateItems()}
     * operation from the Stage 1 design; it is called before any export and by the GUI
     * status bar.
     *
     * @return counts keyed by "total", "pending", "accepted", "rejected" and by severity label
     */
    public synchronized Map<String, Integer> aggregateItems() {
        Map<String, Integer> summary = new LinkedHashMap<>();
        summary.put("total", items.size());
        summary.put("pending", (int) items.stream().filter(i -> i.getStatus() == ReviewStatus.PENDING).count());
        summary.put("accepted", (int) items.stream().filter(i -> i.getStatus() == ReviewStatus.ACCEPTED).count());
        summary.put("rejected", (int) items.stream().filter(i -> i.getStatus() == ReviewStatus.REJECTED).count());
        for (Severity severity : Severity.values()) {
            summary.put(severity.getLabel(),
                    (int) items.stream().filter(i -> i.getSeverity() == severity).count());
        }
        return summary;
    }

    /** Counts findings per severity, used by the GUI severity chips and the PDF summary table. */
    public synchronized Map<Severity, Integer> countBySeverity() {
        Map<Severity, Integer> counts = new EnumMap<>(Severity.class);
        for (Severity severity : Severity.values()) {
            counts.put(severity, 0);
        }
        for (ReviewItem item : items) {
            counts.merge(item.getSeverity(), 1, Integer::sum);
        }
        return counts;
    }

    /** Returns findings ordered by severity then by file, which is the display order. */
    public synchronized List<ReviewItem> getSorted() {
        return items.stream()
                .sorted(Comparator.comparingInt((ReviewItem i) -> i.getSeverity().getRank())
                        .thenComparing(ReviewItem::getFilePath)
                        .thenComparingInt(ReviewItem::getLineNumber))
                .collect(Collectors.toList());
    }

    /**
     * Produces an immutable snapshot of the current findings, tagged with a label such as a
     * commit hash. Snapshots are what feature F08 compares against each other.
     */
    public synchronized ReviewSnapshot snapshot(String label, String commitHash) {
        return new ReviewSnapshot(label, commitHash, getItems());
    }

    /** Replaces all findings, used when restoring a persisted session. */
    public synchronized void replaceAll(List<ReviewItem> replacement) {
        items.clear();
        if (replacement != null) {
            items.addAll(replacement);
        }
        notifyObservers(Observer.UpdateEvent.FINDINGS_ADDED, getItems());
    }

    public String getRepositoryPath() {
        return repositoryPath;
    }

    public void setRepositoryPath(String repositoryPath) {
        this.repositoryPath = repositoryPath == null ? "" : repositoryPath;
    }

    public String getSessionLabel() {
        return sessionLabel;
    }

    public void setSessionLabel(String sessionLabel) {
        this.sessionLabel = sessionLabel;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** A one-line health verdict shown in the GUI status bar and at the top of exports. */
    public synchronized String getHealthVerdict() {
        Map<Severity, Integer> counts = countBySeverity();
        int critical = counts.getOrDefault(Severity.CRITICAL, 0);
        int high = counts.getOrDefault(Severity.HIGH, 0);
        if (items.isEmpty()) {
            return "No findings yet - run an analysis to populate this report.";
        }
        if (critical > 0) {
            return "Action required: " + critical + " critical finding(s) must be resolved before merge.";
        }
        if (high > 0) {
            return "Needs attention: " + high + " high-severity finding(s) outstanding.";
        }
        return "Healthy: no critical or high-severity findings in this review.";
    }
}
