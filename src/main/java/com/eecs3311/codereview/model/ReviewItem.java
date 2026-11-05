package com.eecs3311.codereview.model;

import com.eecs3311.codereview.json.Json;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A single review finding: one problem the agent reported in one place in the code.
 *
 * <p>This is the central domain object. It is produced by a review strategy, displayed by
 * both views, mutated by the Accept/Reject commands, aggregated into a {@link ReviewReport},
 * and compared across versions to build a delta report.</p>
 *
 * <p><strong>MEMENTO PATTERN - Originator role.</strong> {@link #createMemento()} captures
 * the mutable triage state (status and feedback) without exposing the field layout, and
 * {@link #restore(Memento)} puts it back. The commands in {@code com.eecs3311.codereview.command}
 * hold a memento taken before they run, which is what makes undo possible without the
 * commands knowing anything about this class's internals.</p>
 */
public class ReviewItem {

    private static final AtomicInteger COUNTER = new AtomicInteger(1000);

    private final String issueId;
    private final String filePath;
    private final int lineNumber;
    private final String title;
    private final String description;
    private final String suggestion;
    private final String category;
    private final String producedBy;
    private final Instant createdAt;

    private Severity severity;
    private ReviewStatus status;
    private String feedback;

    /**
     * Creates a finding with a generated identifier.
     *
     * @param filePath    file the finding refers to
     * @param lineNumber  1-based line number, or 0 when the finding is file-wide
     * @param severity    assessed severity
     * @param title       one-line headline shown in the findings table
     * @param description explanation of why this is a problem
     * @param suggestion  recommended fix, may be empty
     * @param category    grouping label such as "Security" or "Style"
     * @param producedBy  name of the strategy or checker that produced it
     */
    public ReviewItem(String filePath, int lineNumber, Severity severity, String title,
                      String description, String suggestion, String category, String producedBy) {
        this.issueId = "ISS-" + COUNTER.incrementAndGet();
        this.filePath = filePath == null ? "(unknown)" : filePath;
        this.lineNumber = Math.max(0, lineNumber);
        this.severity = severity == null ? Severity.MEDIUM : severity;
        this.title = title == null || title.isBlank() ? "Unnamed finding" : title.trim();
        this.description = description == null ? "" : description.trim();
        this.suggestion = suggestion == null ? "" : suggestion.trim();
        this.category = category == null || category.isBlank() ? "General" : category.trim();
        this.producedBy = producedBy == null ? "unknown" : producedBy;
        this.status = ReviewStatus.PENDING;
        this.feedback = "";
        this.createdAt = Instant.now();
    }

    // ------------------------------------------------------------------ accessors

    public String getIssueId() {
        return issueId;
    }

    public String getFilePath() {
        return filePath;
    }

    /** Returns just the file name, for compact display in tables. */
    public String getFileName() {
        int slash = Math.max(filePath.lastIndexOf('/'), filePath.lastIndexOf('\\'));
        return slash >= 0 ? filePath.substring(slash + 1) : filePath;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public Severity getSeverity() {
        return severity;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getSuggestion() {
        return suggestion;
    }

    public String getCategory() {
        return category;
    }

    public String getProducedBy() {
        return producedBy;
    }

    public ReviewStatus getStatus() {
        return status;
    }

    public String getFeedback() {
        return feedback;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** True when the developer has triaged this finding either way. */
    public boolean isResolved() {
        return status != ReviewStatus.PENDING;
    }

    // ------------------------------------------------------------------ mutation

    /**
     * Updates the triage state. Called only by command objects, which capture a memento
     * first so the change can be undone.
     *
     * @param newStatus the new lifecycle state
     * @param reason    the developer's comment; stored as feedback and later fed back into
     *                  the agent's long-term memory so the same finding is not re-raised
     */
    public void updateStatus(ReviewStatus newStatus, String reason) {
        this.status = newStatus == null ? ReviewStatus.PENDING : newStatus;
        this.feedback = reason == null ? "" : reason.trim();
    }

    /** Re-classifies the finding; used by the Change Severity command. */
    public void setSeverity(Severity newSeverity) {
        if (newSeverity != null) {
            this.severity = newSeverity;
        }
    }

    /**
     * Identity used for cross-version comparison (feature F08). Two findings from different
     * analysis runs are "the same issue" when they share file, category and headline. The
     * line number is deliberately excluded because inserting a line above a defect must not
     * make it look like the old issue was resolved and a new one appeared.
     */
    public String getFingerprint() {
        return (filePath + "|" + category + "|" + title).toLowerCase();
    }

    // ------------------------------------------------------------------ memento

    /**
     * MEMENTO PATTERN - Memento role.
     *
     * <p>An immutable snapshot of the fields a command is allowed to change. The command
     * acts as the caretaker: it stores the memento but cannot read or alter the captured
     * values, because the fields are private to this nested class.</p>
     */
    public static final class Memento {
        private final ReviewStatus status;
        private final String feedback;
        private final Severity severity;

        private Memento(ReviewStatus status, String feedback, Severity severity) {
            this.status = status;
            this.feedback = feedback;
            this.severity = severity;
        }

        /** Describes the captured state, for the action-history log. */
        @Override
        public String toString() {
            return status + (feedback.isEmpty() ? "" : " (" + feedback + ")");
        }
    }

    /** Captures the current triage state so a command can undo itself later. */
    public Memento createMemento() {
        return new Memento(status, feedback, severity);
    }

    /** Restores previously captured triage state. */
    public void restore(Memento memento) {
        if (memento == null) {
            return;
        }
        this.status = memento.status;
        this.feedback = memento.feedback;
        this.severity = memento.severity;
    }

    // ------------------------------------------------------------------ serialisation

    /** Converts the finding to a map, used when exporting JSON and persisting snapshots. */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("issueId", issueId);
        map.put("filePath", filePath);
        map.put("lineNumber", lineNumber);
        map.put("severity", severity.name());
        map.put("category", category);
        map.put("title", title);
        map.put("description", description);
        map.put("suggestion", suggestion);
        map.put("status", status.name());
        map.put("feedback", feedback);
        map.put("producedBy", producedBy);
        map.put("createdAt", createdAt.toString());
        return map;
    }

    /** Rebuilds a finding from a map produced by {@link #toMap()}. */
    public static ReviewItem fromMap(Map<String, Object> map) {
        ReviewItem item = new ReviewItem(
                Json.getString(map, "(unknown)", "filePath"),
                Json.getInt(map, 0, "lineNumber"),
                Severity.fromText(Json.getString(map, "MEDIUM", "severity")),
                Json.getString(map, "Unnamed finding", "title"),
                Json.getString(map, "", "description"),
                Json.getString(map, "", "suggestion"),
                Json.getString(map, "General", "category"),
                Json.getString(map, "unknown", "producedBy"));
        item.updateStatus(ReviewStatus.fromText(Json.getString(map, "PENDING", "status")),
                Json.getString(map, "", "feedback"));
        return item;
    }

    @Override
    public String toString() {
        return String.format("[%s] %s:%d %s - %s",
                severity.getLabel(), getFileName(), lineNumber, title, status.getLabel());
    }
}
