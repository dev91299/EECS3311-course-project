package com.eecs3311.codereview.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The result of comparing two {@link ReviewSnapshot}s (feature F08).
 *
 * <p>Splits the two finding sets into three buckets - resolved, newly introduced, and
 * still present - and carries an optional natural-language verdict produced by the model
 * describing whether code quality improved or regressed. The deterministic set arithmetic
 * is done here; only the prose summary comes from the LLM, which is why the feature is
 * classified as hybrid.</p>
 */
public class DeltaReport {

    private final ReviewSnapshot before;
    private final ReviewSnapshot after;
    private final List<ReviewItem> resolved = new ArrayList<>();
    private final List<ReviewItem> introduced = new ArrayList<>();
    private final List<ReviewItem> persisting = new ArrayList<>();
    private String changedFilesDiff = "";
    private String aiSummary = "";

    /**
     * Computes the delta between two snapshots.
     *
     * @param before the earlier snapshot (for example commit A)
     * @param after  the later snapshot (for example commit B)
     */
    public DeltaReport(ReviewSnapshot before, ReviewSnapshot after) {
        this.before = before;
        this.after = after;

        Set<String> beforeFingerprints = before.fingerprints();
        Set<String> afterFingerprints = after.fingerprints();

        Set<String> seen = new HashSet<>();
        for (ReviewItem item : before.getItems()) {
            if (!afterFingerprints.contains(item.getFingerprint()) && seen.add(item.getFingerprint())) {
                resolved.add(item);
            }
        }
        seen.clear();
        for (ReviewItem item : after.getItems()) {
            String fingerprint = item.getFingerprint();
            if (!seen.add(fingerprint)) {
                continue;
            }
            if (beforeFingerprints.contains(fingerprint)) {
                persisting.add(item);
            } else {
                introduced.add(item);
            }
        }
    }

    public ReviewSnapshot getBefore() {
        return before;
    }

    public ReviewSnapshot getAfter() {
        return after;
    }

    /** Findings present in the earlier snapshot that are gone in the later one. */
    public List<ReviewItem> getResolved() {
        return new ArrayList<>(resolved);
    }

    /** Findings that appear only in the later snapshot. */
    public List<ReviewItem> getIntroduced() {
        return new ArrayList<>(introduced);
    }

    /** Findings present in both snapshots. */
    public List<ReviewItem> getPersisting() {
        return new ArrayList<>(persisting);
    }

    public String getChangedFilesDiff() {
        return changedFilesDiff;
    }

    public void setChangedFilesDiff(String changedFilesDiff) {
        this.changedFilesDiff = changedFilesDiff == null ? "" : changedFilesDiff;
    }

    public String getAiSummary() {
        return aiSummary;
    }

    /** Stores the model's prose verdict on the quality shift. */
    public void setAiSummary(String aiSummary) {
        this.aiSummary = aiSummary == null ? "" : aiSummary;
    }

    /**
     * Net change in finding count. Negative means the later version has fewer findings,
     * which is an improvement.
     */
    public int getNetChange() {
        return after.size() - before.size();
    }

    /** A deterministic one-line verdict, available even when the model is unreachable. */
    public String getVerdict() {
        int net = getNetChange();
        if (net < 0) {
            return "Improved: " + resolved.size() + " finding(s) resolved, "
                    + introduced.size() + " introduced (net " + net + ").";
        }
        if (net > 0) {
            return "Regressed: " + introduced.size() + " new finding(s) against "
                    + resolved.size() + " resolved (net +" + net + ").";
        }
        return "Unchanged in volume: " + resolved.size() + " resolved and "
                + introduced.size() + " introduced.";
    }

    /** Renders the full delta as plain text for the CLI and for exports. */
    public String render() {
        StringBuilder sb = new StringBuilder();
        sb.append("Comparison: ").append(before).append("  ->  ").append(after).append('\n');
        sb.append(getVerdict()).append("\n\n");

        sb.append("RESOLVED (").append(resolved.size()).append(")\n");
        if (resolved.isEmpty()) {
            sb.append("  (none)\n");
        } else {
            for (ReviewItem item : resolved) {
                sb.append("  + [").append(item.getSeverity().getLabel()).append("] ")
                  .append(item.getFileName()).append(": ").append(item.getTitle()).append('\n');
            }
        }

        sb.append("\nNEWLY INTRODUCED (").append(introduced.size()).append(")\n");
        if (introduced.isEmpty()) {
            sb.append("  (none)\n");
        } else {
            for (ReviewItem item : introduced) {
                sb.append("  - [").append(item.getSeverity().getLabel()).append("] ")
                  .append(item.getFileName()).append(":").append(item.getLineNumber())
                  .append(' ').append(item.getTitle()).append('\n');
            }
        }

        sb.append("\nSTILL PRESENT (").append(persisting.size()).append(")\n");
        if (persisting.isEmpty()) {
            sb.append("  (none)\n");
        } else {
            for (ReviewItem item : persisting) {
                sb.append("  = [").append(item.getSeverity().getLabel()).append("] ")
                  .append(item.getFileName()).append(": ").append(item.getTitle()).append('\n');
            }
        }

        if (!aiSummary.isEmpty()) {
            sb.append("\nAI QUALITY ASSESSMENT\n").append(aiSummary).append('\n');
        }
        return sb.toString();
    }
}
