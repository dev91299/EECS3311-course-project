package com.eecs3311.codereview.export;

import com.eecs3311.codereview.model.ReviewItem;
import com.eecs3311.codereview.model.ReviewReport;
import com.eecs3311.codereview.model.Severity;

import java.awt.Color;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Exports the review as a PDF (feature F09, the {@code exportPDF()} operation from the
 * Stage 1 design).
 *
 * <p>Follows the same template as the text exporters, but instead of appending markup to a
 * string it drives a {@link SimplePdfWriter}, and overrides the
 * {@link #writeToDisk(String, Path)} hook to emit binary rather than text. This is the
 * clearest payoff of the Template Method structure: a format whose output is not text at all
 * still reuses the document order, the grouping, the sorting and the error handling.</p>
 */
public class PdfExporter extends AbstractReportExporter {

    private SimplePdfWriter writer;

    @Override
    public String getExtension() {
        return "pdf";
    }

    @Override
    public String getDescription() {
        return "PDF document (for sharing and archiving)";
    }

    @Override
    protected void writeHeader(StringBuilder out, ReviewReport report) {
        writer = new SimplePdfWriter();
        writer.addTitle("AI Code Review Report");
        writer.addMeta("Repository: " + (report.getRepositoryPath().isEmpty()
                ? "(not recorded)" : report.getRepositoryPath()));
        writer.addMeta("Generated: " + TIMESTAMP.format(java.time.Instant.now()));
        writer.addMeta("Session started: " + TIMESTAMP.format(report.getCreatedAt()));
        writer.addSpacer(8f);
        writer.addRule(new Color(0x2D, 0x6C, 0xDF));
        writer.addSpacer(6f);
        writer.addParagraph(report.getHealthVerdict());
    }

    @Override
    protected void writeSummary(StringBuilder out, ReviewReport report,
                                Map<String, Integer> summary) {
        writer.addHeading("Summary");

        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"Total findings", String.valueOf(summary.getOrDefault("total", 0))});
        rows.add(new String[]{"Pending triage", String.valueOf(summary.getOrDefault("pending", 0))});
        rows.add(new String[]{"Accepted by reviewer", String.valueOf(summary.getOrDefault("accepted", 0))});
        rows.add(new String[]{"Rejected by reviewer", String.valueOf(summary.getOrDefault("rejected", 0))});
        writer.addKeyValueTable(rows);

        writer.addSubHeading("Findings by severity");
        List<String[]> severityRows = new ArrayList<>();
        for (Map.Entry<Severity, Integer> entry : severityCounts(report).entrySet()) {
            severityRows.add(new String[]{entry.getKey().getLabel(),
                    String.valueOf(entry.getValue())});
        }
        writer.addKeyValueTable(severityRows);
    }

    @Override
    protected void writeFindings(StringBuilder out, Map<String, List<ReviewItem>> findingsByFile) {
        writer.addHeading("Findings");
        if (findingsByFile.isEmpty()) {
            writer.addParagraph("No findings were recorded in this session.");
            return;
        }
        for (Map.Entry<String, List<ReviewItem>> entry : findingsByFile.entrySet()) {
            writer.addSubHeading(entry.getKey().replace('\\', '/'));
            for (ReviewItem item : entry.getValue()) {
                writer.addBadgedHeadline(item.getSeverity().getLabel().toUpperCase(),
                        item.getSeverity().getColor(), item.getTitle());
                writer.addMeta(item.getIssueId()
                        + "   line " + (item.getLineNumber() == 0
                                ? "file-wide" : String.valueOf(item.getLineNumber()))
                        + "   " + item.getCategory()
                        + "   status: " + item.getStatus().getLabel()
                        + "   found by: " + item.getProducedBy());
                writer.addSpacer(3f);
                if (!item.getDescription().isEmpty()) {
                    writer.addParagraph(item.getDescription());
                }
                if (!item.getSuggestion().isEmpty()) {
                    writer.addBullet("Suggested fix: " + item.getSuggestion());
                }
                if (!item.getFeedback().isEmpty()) {
                    writer.addBullet("Reviewer comment: " + item.getFeedback());
                }
                writer.addSpacer(7f);
            }
        }
    }

    @Override
    protected void writeDecisionRecord(StringBuilder out, ReviewReport report) {
        List<ReviewItem> resolved = resolvedItems(report);
        writer.addHeading("Review decisions");
        if (resolved.isEmpty()) {
            writer.addParagraph("No findings have been triaged yet.");
            return;
        }
        for (ReviewItem item : resolved) {
            writer.addBullet(item.getIssueId() + "  " + item.getStatus().getLabel().toUpperCase()
                    + "  " + item.getTitle()
                    + (item.getFeedback().isEmpty() ? "" : "  -  " + item.getFeedback()));
        }
        writer.addSpacer(5f);
    }

    @Override
    protected void writeExtraSections(StringBuilder out) {
        if (!getAgentTrace().isBlank()) {
            writer.addHeading("Agent reasoning trace");
            writer.addMonospaced(getAgentTrace());
        }
        if (!getActionLog().isBlank()) {
            writer.addHeading("Review action log");
            writer.addMonospaced(getActionLog());
        }
        if (!getUsageSummary().isBlank()) {
            writer.addHeading("Model usage");
            writer.addMonospaced(getUsageSummary());
        }
    }

    @Override
    protected void writeFooter(StringBuilder out, ReviewReport report) {
        writer.addSpacer(12f);
        writer.addRule(new Color(0xD8, 0xDD, 0xE3));
        writer.addMeta("Generated by the AI Code Review Agent (EECS3311 course project).");
        writer.addMeta("Findings produced by a language model are suggestions for a human "
                + "reviewer, not a substitute for one.");
    }

    /**
     * TEMPLATE METHOD hook override: the assembled string is unused for this format, because
     * the document was built directly into the PDF writer by the steps above.
     */
    @Override
    protected void writeToDisk(String document, Path destination) throws IOException {
        writer.write(destination);
    }
}
