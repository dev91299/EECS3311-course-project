package com.eecs3311.codereview.model;

import java.awt.Color;

/**
 * Severity classification for a review finding.
 *
 * <p>Stage 1 described severity as a free-text {@code String} field. It is implemented as an
 * enum so that sorting, filtering and colouring are type-safe and a malformed value returned
 * by the language model cannot corrupt the report. {@link #fromText} performs the tolerant
 * mapping from whatever the model produces onto this fixed set.</p>
 */
public enum Severity {

    /** Security vulnerability or defect that will cause data loss or compromise. */
    CRITICAL("Critical", 0, new Color(0xB3, 0x1C, 0x1C)),
    /** Defect that will cause incorrect behaviour in common cases. */
    HIGH("High", 1, new Color(0xD9, 0x53, 0x00)),
    /** Defect that causes incorrect behaviour in edge cases, or a significant smell. */
    MEDIUM("Medium", 2, new Color(0xB8, 0x86, 0x00)),
    /** Minor maintainability or style concern. */
    LOW("Low", 3, new Color(0x2E, 0x6D, 0x33)),
    /** Informational observation, no action strictly required. */
    INFO("Info", 4, new Color(0x33, 0x5B, 0x8C));

    private final String label;
    private final int rank;
    private final Color color;

    Severity(String label, int rank, Color color) {
        this.label = label;
        this.rank = rank;
        this.color = color;
    }

    /** Human-readable label used in the UI and in exported reports. */
    public String getLabel() {
        return label;
    }

    /** Sort order: 0 is the most severe. */
    public int getRank() {
        return rank;
    }

    /** Colour used by the GUI findings table. */
    public Color getColor() {
        return color;
    }

    /** ANSI colour escape used by the CLI view. */
    public String getAnsiColor() {
        switch (this) {
            case CRITICAL: return "\u001B[1;31m";
            case HIGH:     return "\u001B[31m";
            case MEDIUM:   return "\u001B[33m";
            case LOW:      return "\u001B[32m";
            default:       return "\u001B[36m";
        }
    }

    /**
     * Maps arbitrary model output onto a severity. Unrecognised values become
     * {@link #MEDIUM} rather than throwing, because a single malformed finding must not
     * abort an entire analysis run.
     */
    public static Severity fromText(String text) {
        if (text == null) {
            return MEDIUM;
        }
        String normalised = text.trim().toUpperCase();
        for (Severity s : values()) {
            if (normalised.startsWith(s.name())) {
                return s;
            }
        }
        if (normalised.contains("BLOCK") || normalised.contains("SEVERE") || normalised.contains("FATAL")) {
            return CRITICAL;
        }
        if (normalised.contains("MAJOR") || normalised.contains("ERROR") || normalised.contains("BUG")) {
            return HIGH;
        }
        if (normalised.contains("WARN")) {
            return MEDIUM;
        }
        if (normalised.contains("MINOR") || normalised.contains("STYLE") || normalised.contains("NIT")) {
            return LOW;
        }
        if (normalised.contains("INFO") || normalised.contains("NOTE") || normalised.contains("SUGGEST")) {
            return INFO;
        }
        return MEDIUM;
    }
}
