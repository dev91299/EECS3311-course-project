package com.eecs3311.codereview.model;

/**
 * Lifecycle state of a single review finding.
 *
 * <p>A finding starts {@link #PENDING}. The developer resolves it with the Accept or Reject
 * commands (feature F07), and either decision can be undone (feature F12), which restores
 * the previous status from a memento.</p>
 */
public enum ReviewStatus {

    /** Reported by the agent, not yet triaged by a human. */
    PENDING("Pending"),
    /** The developer agreed with the finding. */
    ACCEPTED("Accepted"),
    /** The developer dismissed the finding; the reason is captured as feedback. */
    REJECTED("Rejected");

    private final String label;

    ReviewStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** Tolerant parsing used when loading a persisted session from disk. */
    public static ReviewStatus fromText(String text) {
        if (text == null) {
            return PENDING;
        }
        String normalised = text.trim().toUpperCase();
        for (ReviewStatus s : values()) {
            if (normalised.startsWith(s.name())) {
                return s;
            }
        }
        return PENDING;
    }
}
