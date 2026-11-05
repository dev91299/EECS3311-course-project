package com.eecs3311.codereview.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * An immutable record of the findings that existed at one point in a project's history.
 *
 * <p>Feature F08 compares two snapshots - typically one taken at commit A and one at
 * commit B - to show which issues were resolved, which are new, and which persist.
 * Snapshots are compared by {@link ReviewItem#getFingerprint()} rather than by identifier,
 * because two separate analysis runs generate different identifiers for the same defect.</p>
 */
public class ReviewSnapshot {

    private final String label;
    private final String commitHash;
    private final Instant takenAt;
    private final List<ReviewItem> items;

    public ReviewSnapshot(String label, String commitHash, List<ReviewItem> items) {
        this.label = label == null ? "snapshot" : label;
        this.commitHash = commitHash == null ? "" : commitHash;
        this.takenAt = Instant.now();
        this.items = items == null ? new ArrayList<>() : new ArrayList<>(items);
    }

    public String getLabel() {
        return label;
    }

    public String getCommitHash() {
        return commitHash;
    }

    public Instant getTakenAt() {
        return takenAt;
    }

    public List<ReviewItem> getItems() {
        return new ArrayList<>(items);
    }

    public int size() {
        return items.size();
    }

    /** The identity set used for comparison. */
    public Set<String> fingerprints() {
        return items.stream().map(ReviewItem::getFingerprint).collect(Collectors.toSet());
    }

    /** Counts per severity, used in the delta report's summary table. */
    public Map<Severity, Long> countBySeverity() {
        return items.stream().collect(Collectors.groupingBy(ReviewItem::getSeverity, Collectors.counting()));
    }

    @Override
    public String toString() {
        return label + (commitHash.isEmpty() ? "" : " @" + commitHash) + " (" + items.size() + " findings)";
    }
}
