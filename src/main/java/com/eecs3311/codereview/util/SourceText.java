package com.eecs3311.codereview.util;

/**
 * Small helpers for reading source text safely.
 *
 * <p>Exists because of a real defect found during verification: the regular expression that
 * detects a type declaration also matched the word "class" inside a comment, so a file whose
 * documentation read "This class is intentionally small" was reported as declaring a type
 * named {@code is}. Any pattern matching over source has to see code only, so comments and
 * string literals are blanked out first.</p>
 */
public final class SourceText {

    private SourceText() {
    }

    /**
     * Replaces the contents of comments and string literals with spaces, preserving every
     * line break and every character position.
     *
     * <p>Positions are preserved so that a line number computed against the stripped text is
     * still correct for the original file.</p>
     */
    public static String stripCommentsAndStrings(String code) {
        if (code == null || code.isEmpty()) {
            return "";
        }
        char[] source = code.toCharArray();
        char[] result = new char[source.length];

        boolean inLineComment = false;
        boolean inBlockComment = false;
        boolean inString = false;
        boolean inChar = false;

        for (int i = 0; i < source.length; i++) {
            char c = source[i];
            char next = i + 1 < source.length ? source[i + 1] : '\0';

            if (c == '\n') {
                inLineComment = false;
                inString = false;
                inChar = false;
                result[i] = c;
                continue;
            }

            if (inLineComment || inBlockComment) {
                if (inBlockComment && c == '*' && next == '/') {
                    inBlockComment = false;
                    result[i] = ' ';
                    result[++i] = ' ';
                    continue;
                }
                result[i] = ' ';
                continue;
            }

            if (inString) {
                if (c == '\\' && i + 1 < source.length) {
                    result[i] = ' ';
                    result[++i] = ' ';
                    continue;
                }
                if (c == '"') {
                    inString = false;
                    result[i] = '"';
                    continue;
                }
                result[i] = ' ';
                continue;
            }

            if (inChar) {
                if (c == '\\' && i + 1 < source.length) {
                    result[i] = ' ';
                    result[++i] = ' ';
                    continue;
                }
                if (c == '\'') {
                    inChar = false;
                    result[i] = '\'';
                    continue;
                }
                result[i] = ' ';
                continue;
            }

            if (c == '/' && next == '/') {
                inLineComment = true;
                result[i] = ' ';
                result[++i] = ' ';
                continue;
            }
            if (c == '/' && next == '*') {
                inBlockComment = true;
                result[i] = ' ';
                result[++i] = ' ';
                continue;
            }
            if (c == '"') {
                inString = true;
                result[i] = '"';
                continue;
            }
            if (c == '\'') {
                inChar = true;
                result[i] = '\'';
                continue;
            }
            result[i] = c;
        }
        return new String(result);
    }

    /** True when a line is entirely a comment or blank. */
    public static boolean isCommentOrBlank(String line) {
        if (line == null) {
            return true;
        }
        String trimmed = line.trim();
        return trimmed.isEmpty()
                || trimmed.startsWith("//")
                || trimmed.startsWith("*")
                || trimmed.startsWith("/*");
    }

    /** Converts a character offset into a 1-based line number. */
    public static int lineOfOffset(String text, int offset) {
        int line = 1;
        int limit = Math.min(offset, text == null ? 0 : text.length());
        for (int i = 0; i < limit; i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }
}
