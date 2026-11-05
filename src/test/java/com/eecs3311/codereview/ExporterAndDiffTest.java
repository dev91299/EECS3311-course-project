package com.eecs3311.codereview;

import com.eecs3311.codereview.export.AbstractReportExporter;
import com.eecs3311.codereview.export.ExporterFactory;
import com.eecs3311.codereview.model.ReviewItem;
import com.eecs3311.codereview.model.ReviewReport;
import com.eecs3311.codereview.model.ReviewStatus;
import com.eecs3311.codereview.model.Severity;
import com.eecs3311.codereview.util.Diff;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the Template Method export hierarchy, the Factory that selects an exporter, and
 * the diff used by the refactoring view.
 */
class ExporterAndDiffTest {

    private ReviewReport sampleReport() {
        ReviewReport report = new ReviewReport();
        report.setRepositoryPath("/project/demo");
        ReviewItem critical = new ReviewItem("/project/demo/LoginService.java", 31,
                Severity.CRITICAL, "SQL query built by string concatenation",
                "User input is concatenated into the statement.",
                "Use a PreparedStatement.", "Security", "security");
        ReviewItem style = new ReviewItem("/project/demo/OrderProcessor.java", 12,
                Severity.LOW, "Method name starts with a capital",
                "Java convention is lowerCamelCase.", "Rename it.", "Naming", "style");
        report.addItems(List.of(critical, style));
        style.updateStatus(ReviewStatus.REJECTED, "matches our legacy API naming");
        return report;
    }

    @Test
    void factoryResolvesEverySupportedFormat() {
        for (String format : ExporterFactory.supportedFormats()) {
            assertTrue(ExporterFactory.create(format).isPresent(), format + " must be supported");
        }
        assertTrue(ExporterFactory.create("markdown").isPresent());
        assertTrue(ExporterFactory.create("PDF").isPresent(), "format names are case-insensitive");
        assertTrue(ExporterFactory.create("docx").isEmpty());
        assertTrue(ExporterFactory.create(null).isEmpty());
    }

    @Test
    void factoryInfersTheFormatFromAFileName() {
        assertEquals("md", ExporterFactory.createForFileName("report.md").orElseThrow()
                .getExtension());
        assertEquals("pdf", ExporterFactory.createForFileName("out/review.pdf").orElseThrow()
                .getExtension());
        assertTrue(ExporterFactory.createForFileName("noextension").isEmpty());
    }

    @Test
    void markdownExportContainsTheFindingsAndDecisions(@TempDir Path directory) throws IOException {
        Path destination = directory.resolve("report.md");
        ExporterFactory.create("md").orElseThrow().export(sampleReport(), destination);

        String written = Files.readString(destination, StandardCharsets.UTF_8);
        assertTrue(written.contains("# AI Code Review Report"));
        assertTrue(written.contains("SQL query built by string concatenation"));
        assertTrue(written.contains("Critical"));
        assertTrue(written.contains("matches our legacy API naming"));
        assertTrue(written.contains("## Review decisions"));
    }

    @Test
    void htmlExportIsWellFormedAndEscapesMarkup(@TempDir Path directory) throws IOException {
        ReviewReport report = new ReviewReport();
        report.addItem(new ReviewItem("/A.java", 1, Severity.HIGH,
                "Unescaped <script> in output", "Contains <b>markup</b> & an ampersand.",
                "Escape it.", "Security", "security"));

        Path destination = directory.resolve("report.html");
        ExporterFactory.create("html").orElseThrow().export(report, destination);

        String written = Files.readString(destination, StandardCharsets.UTF_8);
        assertTrue(written.startsWith("<!DOCTYPE html>"));
        assertTrue(written.trim().endsWith("</html>"));
        assertTrue(written.contains("&lt;script&gt;"), "markup must be escaped");
        assertFalse(written.contains("<script>"), "raw markup must never reach the page");
    }

    @Test
    void jsonExportIsParseableAndCarriesTheSchemaVersion(@TempDir Path directory)
            throws IOException {
        Path destination = directory.resolve("report.json");
        ExporterFactory.create("json").orElseThrow().export(sampleReport(), destination);

        Object parsed = com.eecs3311.codereview.json.Json.parse(
                Files.readString(destination, StandardCharsets.UTF_8));
        assertEquals("2.0", com.eecs3311.codereview.json.Json.getString(parsed, "", "schemaVersion"));
        assertEquals(2, com.eecs3311.codereview.json.Json.getInt(parsed, 0, "summary", "total"));
    }

    @Test
    void pdfExportProducesAValidFileHeaderAndTrailer(@TempDir Path directory) throws IOException {
        Path destination = directory.resolve("report.pdf");
        ExporterFactory.create("pdf").orElseThrow().export(sampleReport(), destination);

        byte[] bytes = Files.readAllBytes(destination);
        assertTrue(bytes.length > 800, "a real PDF is not a stub");

        String asText = new String(bytes, StandardCharsets.ISO_8859_1);
        assertTrue(asText.startsWith("%PDF-1.4"));
        assertTrue(asText.contains("/Type /Catalog"));
        assertTrue(asText.contains("/Type /Pages"));
        assertTrue(asText.contains("/BaseFont /Helvetica"));
        assertTrue(asText.contains("xref"));
        assertTrue(asText.trim().endsWith("%%EOF"));
    }

    @Test
    void extraSectionsAppearInTheExport(@TempDir Path directory) throws IOException {
        AbstractReportExporter exporter = ExporterFactory.create("md").orElseThrow();
        exporter.setActionLog("12:00:00  DO        Accepted ISS-1001");
        exporter.setAgentTrace("[step 1] PLAN - inventory the repository");
        exporter.setUsageSummary("Total tokens: 4210");

        Path destination = directory.resolve("report.md");
        exporter.export(sampleReport(), destination);

        String written = Files.readString(destination, StandardCharsets.UTF_8);
        assertTrue(written.contains("Accepted ISS-1001"));
        assertTrue(written.contains("[step 1] PLAN"));
        assertTrue(written.contains("Total tokens: 4210"));
    }

    @Test
    void exportingAnEmptyReportStillProducesAValidDocument(@TempDir Path directory)
            throws IOException {
        Path destination = directory.resolve("empty.md");
        ExporterFactory.create("md").orElseThrow().export(new ReviewReport(), destination);
        String written = Files.readString(destination, StandardCharsets.UTF_8);
        assertTrue(written.contains("No findings were recorded"));
    }

    @Test
    void exportingToAnImpossiblePathReportsAReadableError() {
        Path impossible = Path.of("/this/path/cannot/exist/\u0000bad.md");
        assertThrows(IOException.class,
                () -> ExporterFactory.create("md").orElseThrow()
                        .export(sampleReport(), impossible));
    }

    // ------------------------------------------------------------------ diff

    @Test
    void identicalTextProducesNoChanges() {
        String text = "line one\nline two\nline three";
        int[] counts = Diff.countChanges(text, text);
        assertEquals(0, counts[0]);
        assertEquals(0, counts[1]);
    }

    @Test
    void anInsertedLineIsReportedAsOneAdditionNotAWholesaleRewrite() {
        String original = "a\nb\nc";
        String revised = "a\nNEW\nb\nc";
        int[] counts = Diff.countChanges(original, revised);
        assertEquals(1, counts[0], "exactly one line added");
        assertEquals(0, counts[1], "no lines removed");
    }

    @Test
    void aChangedLineIsOneAdditionAndOneRemoval() {
        int[] counts = Diff.countChanges("a\nb\nc", "a\nCHANGED\nc");
        assertEquals(1, counts[0]);
        assertEquals(1, counts[1]);
    }

    @Test
    void unifiedDiffUsesStandardMarkers() {
        String unified = Diff.renderUnified("a\nb", "a\nc");
        assertTrue(unified.contains("- b"));
        assertTrue(unified.contains("+ c"));
        assertTrue(unified.contains("  a"));
    }

    @Test
    void sideBySideRenderingHasBothColumnHeaders() {
        String rendered = Diff.renderSideBySide("a\nb", "a\nc", 40);
        assertTrue(rendered.contains("ORIGINAL"));
        assertTrue(rendered.contains("SUGGESTED"));
    }

    @Test
    void emptyInputsAreHandledWithoutThrowing() {
        assertEquals(0, Diff.compute("", "").size());
        assertEquals(2, Diff.countChanges("", "a\nb")[0]);
    }
}
