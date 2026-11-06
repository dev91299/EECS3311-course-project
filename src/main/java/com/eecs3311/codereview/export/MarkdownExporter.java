package com.eecs3311.codereview.export;

import com.eecs3311.codereview.model.ReviewItem;
import com.eecs3311.codereview.model.ReviewReport;
import com.eecs3311.codereview.model.Severity;

import java.util.List;
import java.util.Map;

/**
 * Exports the review as a Markdown document (feature F09).
 *
 * <p>Markdown is the format a developer is most likely to paste into a pull request or a
 * team wiki, so this exporter keeps the structure flat and the tables narrow enough to stay
 * readable as raw text.</p>
 */
public class MarkdownExporter extends AbstractReportExporter {

    @Override
    public String getExtension() {
        return "md";
    }

    @Override
    public String getDescription() {
        return "Markdown document (for pull requests and wikis)";
    }

    @Override
    protected void writeHeader(StringBuilder out, ReviewReport report) {
        out.append("# AI Code Review Report\n\n");
        out.append("**Repository:** `")
           .append(report.getRepositoryPath().isEmpty() ? "(not recorded)" : report.getRepositoryPath())
           .append("`  \n");
        out.append("**Generated:** ").append(TIMESTAMP.format(java.time.Instant.now())).append("  \n");
        out.append("**Session started:** ").append(TIMESTAMP.format(report.getCreatedAt())).append("\n\n");
        out.append("> ").append(report.getHealthVerdict()).append("\n\n");
    }

    @Override
    protected void writeSummary(StringBuilder out, ReviewReport report,
                                Map<String, Integer> summary) {
        out.append("## Summary\n\n");
        out.append("| Metric | Count |\n|---|---|\n");
        out.append("| Total findings | ").append(summary.getOrDefault("total", 0)).append(" |\n");
        out.append("| Pending triage | ").append(summary.getOrDefault("pending", 0)).append(" |\n");
        out.append("| Accepted | ").append(summary.getOrDefault("accepted", 0)).append(" |\n");
        out.append("| Rejected | ").append(summary.getOrDefault("rejected", 0)).append(" |\n\n");

        out.append("### By severity\n\n| Severity | Count |\n|---|---|\n");
        for (Map.Entry<Severity, Integer> entry : severityCounts(report).entrySet()) {
            out.append("| ").append(entry.getKey().getLabel()).append(" | ")
               .append(entry.getValue()).append(" |\n");
        }
        out.append('\n');
    }

    @Override
    protected void writeFindings(StringBuilder out, Map<String, List<ReviewItem>> findingsByFile) {
        out.append("## Findings\n\n");
        if (findingsByFile.isEmpty()) {
            out.append("No findings were recorded in this session.\n\n");
            return;
        }
        for (Map.Entry<String, List<ReviewItem>> entry : findingsByFile.entrySet()) {
            out.append("### `").append(shortPath(entry.getKey())).append("`\n\n");
            for (ReviewItem item : entry.getValue()) {
                out.append("#### ").append(item.getSeverity().getLabel()).append(" - ")
                   .append(escape(item.getTitle())).append('\n');
                out.append("*").append(item.getIssueId()).append(" | line ")
                   .append(item.getLineNumber() == 0 ? "file-wide" : String.valueOf(item.getLineNumber()))
                   .append(" | ").append(item.getCategory())
                   .append(" | status: ").append(item.getStatus().getLabel())
                   .append(" | found by: ").append(item.getProducedBy()).append("*\n\n");
                if (!item.getDescription().isEmpty()) {
                    out.append(escape(item.getDescription())).append("\n\n");
                }
                if (!item.getSuggestion().isEmpty()) {
                    out.append("**Suggested fix:** ").append(escape(item.getSuggestion())).append("\n\n");
                }
                if (!item.getFeedback().isEmpty()) {
                    out.append("**Reviewer comment:** ").append(escape(item.getFeedback())).append("\n\n");
                }
            }
        }
    }

    @Override
    protected void writeDecisionRecord(StringBuilder out, ReviewReport report) {
        List<ReviewItem> resolved = resolvedItems(report);
        out.append("## Review decisions\n\n");
        if (resolved.isEmpty()) {
            out.append("No findings have been triaged yet.\n\n");
            return;
        }
        out.append("| ID | Decision | Finding | Reviewer comment |\n|---|---|---|---|\n");
        for (ReviewItem item : resolved) {
            out.append("| ").append(item.getIssueId())
               .append(" | ").append(item.getStatus().getLabel())
               .append(" | ").append(escape(item.getTitle()))
               .append(" | ").append(item.getFeedback().isEmpty() ? "-" : escape(item.getFeedback()))
               .append(" |\n");
        }
        out.append('\n');
    }

    @Override
    protected void writeExtraSections(StringBuilder out) {
        if (!getAgentTrace().isBlank()) {
            out.append("## Agent reasoning trace\n\n```\n").append(getAgentTrace()).append("\n```\n\n");
        }
        if (!getActionLog().isBlank()) {
            out.append("## Review action log\n\n```\n").append(getActionLog()).append("\n```\n\n");
        }
        if (!getUsageSummary().isBlank()) {
            out.append("## Model usage\n\n```\n").append(getUsageSummary()).append("\n```\n\n");
        }
    }

    @Override
    protected void writeFooter(StringBuilder out, ReviewReport report) {
        out.append("---\n\n");
        out.append("Generated by the AI Code Review Agent (EECS3311 course project).\n");
    }

    private String shortPath(String path) {
        return path.replace('\\', '/');
    }

    /** Escapes the characters that would break a Markdown table cell. */
    private String escape(String text) {
        return text.replace("|", "\\|").replace("\n", " ");
    }
}
