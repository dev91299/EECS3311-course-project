package com.eecs3311.codereview.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A line-based diff, used to render the side-by-side comparison feature F04 promises.
 *
 * <p>Implemented with the classic longest-common-subsequence dynamic program: the length of
 * the LCS of the two line sequences is computed, then the table is walked backwards to
 * recover which lines are shared, which were removed and which were added. A simple
 * line-by-line comparison would mark everything after an inserted line as changed, which
 * makes a refactoring suggestion impossible to read.</p>
 *
 * <p>Written here rather than taken from a library because the project carries no runtime
 * dependencies.</p>
 */
public final class Diff {

    /** What happened to one line. */
    public enum Operation {
        /** Present in both versions. */
        UNCHANGED,
        /** Present only in the original. */
        REMOVED,
        /** Present only in the revision. */
        ADDED
    }

    /** One line of the diff. */
    public static class Line {
        private final Operation operation;
        private final String text;
        private final int leftNumber;
        private final int rightNumber;

        Line(Operation operation, String text, int leftNumber, int rightNumber) {
            this.operation = operation;
            this.text = text;
            this.leftNumber = leftNumber;
            this.rightNumber = rightNumber;
        }

        public Operation getOperation() {
            return operation;
        }

        public String getText() {
            return text;
        }

        /** Line number in the original, or 0 when the line was added. */
        public int getLeftNumber() {
            return leftNumber;
        }

        /** Line number in the revision, or 0 when the line was removed. */
        public int getRightNumber() {
            return rightNumber;
        }

        /** The marker used in unified output. */
        public char marker() {
            switch (operation) {
                case ADDED:   return '+';
                case REMOVED: return '-';
                default:      return ' ';
            }
        }
    }

    private Diff() {
    }

    /**
     * Computes the diff between two texts.
     *
     * @param original the left-hand text
     * @param revised  the right-hand text
     * @return the merged sequence of lines, in reading order
     */
    public static List<Line> compute(String original, String revised) {
        String[] left = splitLines(original);
        String[] right = splitLines(revised);

        int[][] lcs = new int[left.length + 1][right.length + 1];
        for (int i = left.length - 1; i >= 0; i--) {
            for (int j = right.length - 1; j >= 0; j--) {
                lcs[i][j] = left[i].equals(right[j])
                        ? lcs[i + 1][j + 1] + 1
                        : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
            }
        }

        List<Line> result = new ArrayList<>();
        int i = 0;
        int j = 0;
        while (i < left.length && j < right.length) {
            if (left[i].equals(right[j])) {
                result.add(new Line(Operation.UNCHANGED, left[i], i + 1, j + 1));
                i++;
                j++;
            } else if (lcs[i + 1][j] >= lcs[i][j + 1]) {
                result.add(new Line(Operation.REMOVED, left[i], i + 1, 0));
                i++;
            } else {
                result.add(new Line(Operation.ADDED, right[j], 0, j + 1));
                j++;
            }
        }
        while (i < left.length) {
            result.add(new Line(Operation.REMOVED, left[i], i + 1, 0));
            i++;
        }
        while (j < right.length) {
            result.add(new Line(Operation.ADDED, right[j], 0, j + 1));
            j++;
        }
        return result;
    }

    /** Renders a unified diff, which is what the CLI prints. */
    public static String renderUnified(String original, String revised) {
        StringBuilder sb = new StringBuilder();
        for (Line line : compute(original, revised)) {
            sb.append(line.marker()).append(' ').append(line.getText()).append('\n');
        }
        return sb.toString();
    }

    /**
     * Renders a two-column side-by-side view, which is what feature F04 specifies and what
     * the GUI shows in its comparison dialog.
     *
     * @param columnWidth characters per column
     */
    public static String renderSideBySide(String original, String revised, int columnWidth) {
        int width = Math.max(30, columnWidth);
        StringBuilder sb = new StringBuilder();
        sb.append(pad("ORIGINAL", width)).append(" | ").append("SUGGESTED").append('\n');
        sb.append(String.join("", Collections.nCopies(width, "-"))).append("-+-")
          .append(String.join("", Collections.nCopies(width, "-"))).append('\n');

        for (Line line : compute(original, revised)) {
            switch (line.getOperation()) {
                case UNCHANGED:
                    sb.append(pad(trim(line.getText(), width), width)).append(" | ")
                      .append(trim(line.getText(), width)).append('\n');
                    break;
                case REMOVED:
                    sb.append(pad("- " + trim(line.getText(), width - 2), width))
                      .append(" | ").append('\n');
                    break;
                case ADDED:
                    sb.append(pad("", width)).append(" | + ")
                      .append(trim(line.getText(), width - 2)).append('\n');
                    break;
                default:
                    break;
            }
        }
        return sb.toString();
    }

    /** Counts added and removed lines, for the summary line above a diff. */
    public static int[] countChanges(String original, String revised) {
        int added = 0;
        int removed = 0;
        for (Line line : compute(original, revised)) {
            if (line.getOperation() == Operation.ADDED) {
                added++;
            } else if (line.getOperation() == Operation.REMOVED) {
                removed++;
            }
        }
        return new int[]{added, removed};
    }

    private static String[] splitLines(String text) {
        if (text == null || text.isEmpty()) {
            return new String[0];
        }
        return text.replace("\r\n", "\n").split("\n", -1);
    }

    private static String pad(String text, int width) {
        if (text.length() >= width) {
            return text.substring(0, width);
        }
        StringBuilder sb = new StringBuilder(text);
        while (sb.length() < width) {
            sb.append(' ');
        }
        return sb.toString();
    }

    private static String trim(String text, int width) {
        String expanded = text.replace("\t", "    ");
        return expanded.length() <= width ? expanded : expanded.substring(0, Math.max(0, width - 1)) + ">";
    }
}
