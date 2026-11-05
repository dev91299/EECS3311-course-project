package com.eecs3311.codereview.export;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * A minimal PDF generator, written against the PDF 1.4 file format.
 *
 * <p>The Stage 1 design promised a PDF export. Every Java PDF library (iText, PDFBox,
 * OpenPDF) is a large third-party dependency, and this project is deliberately built with
 * none, so that it compiles and runs from a clean checkout with only a JDK. Producing the
 * handful of PDF constructs actually needed - a page tree, three standard fonts, text
 * positioning and coloured rectangles - is about two hundred lines, which is a better trade
 * than a dependency.</p>
 *
 * <p>The file is assembled as numbered objects, a cross-reference table of their byte offsets,
 * and a trailer pointing at the catalogue. Text is laid out top-down with automatic page
 * breaks and word wrapping; widths are estimated from the metrics of the standard fonts,
 * which is accurate enough for a report and avoids embedding a font program.</p>
 */
public class SimplePdfWriter {

    // A4 at 72 points per inch.
    private static final float PAGE_WIDTH = 595f;
    private static final float PAGE_HEIGHT = 842f;
    private static final float MARGIN = 50f;
    private static final float CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN;

    // Object numbers 1-5 are fixed; pages are allocated from 6 upwards.
    private static final int OBJ_CATALOG = 1;
    private static final int OBJ_PAGES = 2;
    private static final int OBJ_FONT_REGULAR = 3;
    private static final int OBJ_FONT_BOLD = 4;
    private static final int OBJ_FONT_MONO = 5;

    private final List<StringBuilder> pages = new ArrayList<>();
    private StringBuilder current;
    private float cursorY;

    public SimplePdfWriter() {
        newPage();
    }

    // ------------------------------------------------------------------ layout

    private void newPage() {
        current = new StringBuilder();
        pages.add(current);
        cursorY = PAGE_HEIGHT - MARGIN;
    }

    /** Starts a new page when the next block of the given height would not fit. */
    private void ensureSpace(float needed) {
        if (cursorY - needed < MARGIN) {
            newPage();
        }
    }

    /** Moves the cursor down without drawing. */
    public void addSpacer(float points) {
        ensureSpace(points);
        cursorY -= points;
    }

    /** Draws a document title. */
    public void addTitle(String text) {
        addText(text, 20f, OBJ_FONT_BOLD, new Color(0x1C, 0x1F, 0x23), 26f);
        addSpacer(4f);
    }

    /** Draws a section heading with a rule beneath it. */
    public void addHeading(String text) {
        addSpacer(10f);
        ensureSpace(34f);
        addText(text, 14f, OBJ_FONT_BOLD, new Color(0x1C, 0x1F, 0x23), 18f);
        addRule(new Color(0xD8, 0xDD, 0xE3));
        addSpacer(6f);
    }

    /** Draws a sub-heading, used for file names. */
    public void addSubHeading(String text) {
        addSpacer(8f);
        addText(text, 11f, OBJ_FONT_MONO, new Color(0x33, 0x3A, 0x42), 15f);
        addSpacer(2f);
    }

    /** Draws a paragraph of body text with word wrapping. */
    public void addParagraph(String text) {
        addText(text, 10f, OBJ_FONT_REGULAR, new Color(0x2A, 0x2E, 0x34), 14f);
        addSpacer(5f);
    }

    /** Draws a smaller line of metadata. */
    public void addMeta(String text) {
        addText(text, 8.5f, OBJ_FONT_REGULAR, new Color(0x66, 0x6E, 0x78), 12f);
    }

    /** Draws a bulleted line. */
    public void addBullet(String text) {
        addText("•  " + text, 10f, OBJ_FONT_REGULAR, new Color(0x2A, 0x2E, 0x34), 14f);
    }

    /** Draws monospaced text, used for the agent trace and the action log. */
    public void addMonospaced(String text) {
        for (String line : text.split("\n")) {
            addText(line.isEmpty() ? " " : line, 8f, OBJ_FONT_MONO,
                    new Color(0x3A, 0x40, 0x48), 11f);
        }
        addSpacer(4f);
    }

    /**
     * Draws a coloured severity badge followed by a bold headline on the same line.
     *
     * @param badge      the severity label
     * @param badgeColor the severity colour
     * @param headline   the finding title
     */
    public void addBadgedHeadline(String badge, Color badgeColor, String headline) {
        ensureSpace(20f);
        float badgeWidth = estimateWidth(badge, 8f, true) + 10f;
        float badgeHeight = 12f;
        float badgeY = cursorY - 9f;

        current.append(String.format("%.3f %.3f %.3f rg%n",
                badgeColor.getRed() / 255f, badgeColor.getGreen() / 255f,
                badgeColor.getBlue() / 255f));
        current.append(String.format("%.2f %.2f %.2f %.2f re f%n",
                MARGIN, badgeY, badgeWidth, badgeHeight));

        current.append("1 1 1 rg\n");
        current.append("BT\n/F2 8 Tf\n");
        current.append(String.format("1 0 0 1 %.2f %.2f Tm%n", MARGIN + 5f, badgeY + 3.2f));
        current.append('(').append(escape(badge)).append(") Tj\nET\n");

        float textX = MARGIN + badgeWidth + 8f;
        float available = PAGE_WIDTH - MARGIN - textX;
        List<String> wrapped = wrap(headline, available, 10.5f, true);

        current.append("0.11 0.12 0.14 rg\n");
        float y = cursorY;
        for (int i = 0; i < wrapped.size(); i++) {
            current.append("BT\n/F2 10.5 Tf\n");
            current.append(String.format("1 0 0 1 %.2f %.2f Tm%n",
                    i == 0 ? textX : MARGIN, y));
            current.append('(').append(escape(wrapped.get(i))).append(") Tj\nET\n");
            y -= 13f;
        }
        cursorY = y - 2f;
    }

    /** Draws a horizontal rule. */
    public void addRule(Color color) {
        ensureSpace(8f);
        current.append(String.format("%.3f %.3f %.3f RG%n",
                color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f));
        current.append("0.8 w\n");
        current.append(String.format("%.2f %.2f m %.2f %.2f l S%n",
                MARGIN, cursorY - 3f, PAGE_WIDTH - MARGIN, cursorY - 3f));
        cursorY -= 8f;
    }

    /**
     * Draws a simple two-column table.
     *
     * @param rows each entry is {label, value}
     */
    public void addKeyValueTable(List<String[]> rows) {
        float labelWidth = 220f;
        for (String[] row : rows) {
            ensureSpace(16f);
            current.append("0.25 0.28 0.32 rg\n");
            current.append("BT\n/F1 9.5 Tf\n");
            current.append(String.format("1 0 0 1 %.2f %.2f Tm%n", MARGIN, cursorY));
            current.append('(').append(escape(row[0])).append(") Tj\nET\n");

            current.append("0.11 0.12 0.14 rg\n");
            current.append("BT\n/F2 9.5 Tf\n");
            current.append(String.format("1 0 0 1 %.2f %.2f Tm%n", MARGIN + labelWidth, cursorY));
            current.append('(').append(escape(row[1])).append(") Tj\nET\n");
            cursorY -= 15f;
        }
        addSpacer(4f);
    }

    /** Core text drawing with wrapping. */
    private void addText(String text, float size, int fontObject, Color color, float leading) {
        if (text == null) {
            return;
        }
        String fontName = fontName(fontObject);
        boolean bold = fontObject == OBJ_FONT_BOLD;
        boolean mono = fontObject == OBJ_FONT_MONO;

        for (String rawLine : text.split("\n")) {
            List<String> wrapped = mono
                    ? wrapMono(rawLine, size)
                    : wrap(rawLine, CONTENT_WIDTH, size, bold);
            for (String line : wrapped) {
                ensureSpace(leading);
                current.append(String.format("%.3f %.3f %.3f rg%n",
                        color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f));
                current.append("BT\n/").append(fontName).append(' ').append(trimFloat(size))
                       .append(" Tf\n");
                current.append(String.format("1 0 0 1 %.2f %.2f Tm%n", MARGIN, cursorY));
                current.append('(').append(escape(line)).append(") Tj\nET\n");
                cursorY -= leading;
            }
        }
    }

    private String fontName(int fontObject) {
        if (fontObject == OBJ_FONT_BOLD) {
            return "F2";
        }
        if (fontObject == OBJ_FONT_MONO) {
            return "F3";
        }
        return "F1";
    }

    // ------------------------------------------------------------------ text metrics

    /**
     * Estimates rendered width. Courier is exactly 0.6 em per character; Helvetica averages
     * close to 0.5 em for mixed-case text, and 0.54 is used for bold to stay conservative so
     * a line never overruns the margin.
     */
    private float estimateWidth(String text, float size, boolean bold) {
        return text.length() * size * (bold ? 0.54f : 0.5f);
    }

    private List<String> wrap(String text, float maxWidth, float size, boolean bold) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            lines.add("");
            return lines;
        }
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (estimateWidth(candidate, size, bold) > maxWidth && line.length() > 0) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
            // A single word longer than the line is hard-split rather than overrunning.
            while (estimateWidth(line.toString(), size, bold) > maxWidth && line.length() > 1) {
                int fit = Math.max(1, (int) (maxWidth / (size * (bold ? 0.54f : 0.5f))) - 1);
                lines.add(line.substring(0, Math.min(fit, line.length())));
                line = new StringBuilder(line.substring(Math.min(fit, line.length())));
            }
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }
        return lines;
    }

    private List<String> wrapMono(String text, float size) {
        List<String> lines = new ArrayList<>();
        int charsPerLine = Math.max(10, (int) (CONTENT_WIDTH / (size * 0.6f)));
        String remaining = text;
        while (remaining.length() > charsPerLine) {
            lines.add(remaining.substring(0, charsPerLine));
            remaining = remaining.substring(charsPerLine);
        }
        lines.add(remaining);
        return lines;
    }

    /** Escapes the three characters that are special inside a PDF string literal. */
    private String escape(String text) {
        StringBuilder sb = new StringBuilder(text.length() + 8);
        for (char c : text.toCharArray()) {
            if (c == '(' || c == ')' || c == '\\') {
                sb.append('\\').append(c);
            } else if (c == '•') {
                sb.append("\\225"); // bullet in WinAnsiEncoding
            } else if (c == '→') {
                sb.append("->");
            } else if (c < 32 || c > 126) {
                sb.append('?');
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private String trimFloat(float value) {
        return value == Math.rint(value) ? String.valueOf((int) value) : String.valueOf(value);
    }

    // ------------------------------------------------------------------ file assembly

    /** Writes the assembled document to disk. */
    public void write(Path destination) throws IOException {
        Files.write(destination, build());
    }

    /** Builds the PDF file bytes. */
    public byte[] build() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        List<Integer> offsets = new ArrayList<>();
        int pageCount = pages.size();
        int totalObjects = 5 + pageCount * 2;

        // Object n -> byte offset; index 0 is unused (PDF objects are 1-based).
        int[] offsetTable = new int[totalObjects + 1];

        writeAscii(out, "%PDF-1.4\n%âãÏÓ\n");

        // 1: catalogue
        offsetTable[OBJ_CATALOG] = out.size();
        writeAscii(out, OBJ_CATALOG + " 0 obj\n<< /Type /Catalog /Pages "
                + OBJ_PAGES + " 0 R >>\nendobj\n");

        // 2: page tree
        StringBuilder kids = new StringBuilder();
        for (int i = 0; i < pageCount; i++) {
            kids.append(7 + i * 2).append(" 0 R ");
        }
        offsetTable[OBJ_PAGES] = out.size();
        writeAscii(out, OBJ_PAGES + " 0 obj\n<< /Type /Pages /Kids [" + kids.toString().trim()
                + "] /Count " + pageCount + " >>\nendobj\n");

        // 3-5: the three standard fonts, which every reader provides
        offsetTable[OBJ_FONT_REGULAR] = out.size();
        writeAscii(out, OBJ_FONT_REGULAR + " 0 obj\n<< /Type /Font /Subtype /Type1 "
                + "/BaseFont /Helvetica /Encoding /WinAnsiEncoding >>\nendobj\n");

        offsetTable[OBJ_FONT_BOLD] = out.size();
        writeAscii(out, OBJ_FONT_BOLD + " 0 obj\n<< /Type /Font /Subtype /Type1 "
                + "/BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>\nendobj\n");

        offsetTable[OBJ_FONT_MONO] = out.size();
        writeAscii(out, OBJ_FONT_MONO + " 0 obj\n<< /Type /Font /Subtype /Type1 "
                + "/BaseFont /Courier /Encoding /WinAnsiEncoding >>\nendobj\n");

        // Content streams and page objects, interleaved.
        for (int i = 0; i < pageCount; i++) {
            int contentObject = 6 + i * 2;
            int pageObject = 7 + i * 2;
            byte[] content = pages.get(i).toString().getBytes(StandardCharsets.ISO_8859_1);

            offsetTable[contentObject] = out.size();
            writeAscii(out, contentObject + " 0 obj\n<< /Length " + content.length + " >>\nstream\n");
            out.write(content);
            writeAscii(out, "\nendstream\nendobj\n");

            offsetTable[pageObject] = out.size();
            writeAscii(out, pageObject + " 0 obj\n<< /Type /Page /Parent " + OBJ_PAGES + " 0 R"
                    + " /MediaBox [0 0 " + (int) PAGE_WIDTH + " " + (int) PAGE_HEIGHT + "]"
                    + " /Resources << /Font << /F1 " + OBJ_FONT_REGULAR + " 0 R"
                    + " /F2 " + OBJ_FONT_BOLD + " 0 R"
                    + " /F3 " + OBJ_FONT_MONO + " 0 R >> >>"
                    + " /Contents " + contentObject + " 0 R >>\nendobj\n");
        }

        // Cross-reference table.
        int xrefOffset = out.size();
        StringBuilder xref = new StringBuilder();
        xref.append("xref\n0 ").append(totalObjects + 1).append('\n');
        xref.append("0000000000 65535 f \n");
        for (int i = 1; i <= totalObjects; i++) {
            xref.append(String.format("%010d 00000 n %n", offsetTable[i]));
        }
        writeAscii(out, xref.toString());

        writeAscii(out, "trailer\n<< /Size " + (totalObjects + 1) + " /Root "
                + OBJ_CATALOG + " 0 R >>\nstartxref\n" + xrefOffset + "\n%%EOF\n");

        offsets.clear();
        return out.toByteArray();
    }

    private void writeAscii(ByteArrayOutputStream out, String text) throws IOException {
        out.write(text.getBytes(StandardCharsets.ISO_8859_1));
    }

    /** Number of pages produced; used by tests. */
    public int getPageCount() {
        return pages.size();
    }
}
