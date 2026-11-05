package com.eecs3311.codereview.export;

import com.eecs3311.codereview.model.ReviewItem;
import com.eecs3311.codereview.model.ReviewReport;
import com.eecs3311.codereview.model.Severity;

import java.awt.Color;
import java.util.List;
import java.util.Map;

/**
 * Exports the review as a self-contained HTML page (feature F09).
 *
 * <p>All styling is inlined, so the file can be emailed or opened from a file system with no
 * network access and still render correctly. Severity colours come from the {@link Severity}
 * enum, which means the exported document and the GUI table always agree.</p>
 */
public class HtmlExporter extends AbstractReportExporter {

    @Override
    public String getExtension() {
        return "html";
    }

    @Override
    public String getDescription() {
        return "HTML page (self-contained, opens in any browser)";
    }

    @Override
    protected void writeHeader(StringBuilder out, ReviewReport report) {
        out.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n");
        out.append("<meta charset=\"utf-8\">\n");
        out.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n");
        out.append("<title>AI Code Review Report</title>\n");
        out.append("<style>\n");
        out.append("  body{font-family:-apple-system,Segoe UI,Roboto,Helvetica,Arial,sans-serif;");
        out.append("margin:0;padding:2rem;background:#f7f8fa;color:#1c1f23;line-height:1.55}\n");
        out.append("  .wrap{max-width:960px;margin:0 auto}\n");
        out.append("  h1{margin:0 0 .25rem;font-size:1.8rem}\n");
        out.append("  h2{margin-top:2.5rem;padding-bottom:.4rem;border-bottom:2px solid #e3e6ea}\n");
        out.append("  h3{margin-top:1.8rem;font-family:ui-monospace,Menlo,Consolas,monospace;");
        out.append("font-size:1rem;background:#eceff3;padding:.45rem .7rem;border-radius:5px}\n");
        out.append("  .meta{color:#5b6570;font-size:.9rem}\n");
        out.append("  .verdict{margin:1.2rem 0;padding:.9rem 1.1rem;background:#fff;");
        out.append("border-left:5px solid #2d6cdf;border-radius:4px}\n");
        out.append("  table{border-collapse:collapse;width:100%;background:#fff;margin:1rem 0;");
        out.append("border-radius:6px;overflow:hidden;box-shadow:0 1px 2px rgba(0,0,0,.07)}\n");
        out.append("  th,td{padding:.6rem .8rem;text-align:left;border-bottom:1px solid #e8ebef;");
        out.append("font-size:.92rem;vertical-align:top}\n");
        out.append("  th{background:#eef1f5;font-weight:600}\n");
        out.append("  .finding{background:#fff;border-radius:6px;padding:1rem 1.2rem;margin:.9rem 0;");
        out.append("box-shadow:0 1px 2px rgba(0,0,0,.07)}\n");
        out.append("  .sev{display:inline-block;padding:.15rem .55rem;border-radius:3px;color:#fff;");
        out.append("font-size:.75rem;font-weight:700;letter-spacing:.03em}\n");
        out.append("  .tag{color:#5b6570;font-size:.8rem;margin-left:.6rem}\n");
        out.append("  .fix{background:#f2f7f2;border-left:3px solid #2e6d33;padding:.5rem .8rem;");
        out.append("margin-top:.7rem;border-radius:3px}\n");
        out.append("  .cmt{background:#fdf6ec;border-left:3px solid #b88600;padding:.5rem .8rem;");
        out.append("margin-top:.5rem;border-radius:3px}\n");
        out.append("  pre{background:#1e2228;color:#e6e9ee;padding:1rem;border-radius:6px;");
        out.append("overflow-x:auto;font-size:.82rem;line-height:1.5}\n");
        out.append("  footer{margin-top:3rem;color:#6b7580;font-size:.85rem;");
        out.append("border-top:1px solid #e3e6ea;padding-top:1rem}\n");
        out.append("</style>\n</head>\n<body>\n<div class=\"wrap\">\n");

        out.append("<h1>AI Code Review Report</h1>\n");
        out.append("<p class=\"meta\">Repository: <code>")
           .append(escape(report.getRepositoryPath().isEmpty()
                   ? "(not recorded)" : report.getRepositoryPath()))
           .append("</code><br>Generated ")
           .append(TIMESTAMP.format(java.time.Instant.now())).append("</p>\n");
        out.append("<div class=\"verdict\">").append(escape(report.getHealthVerdict()))
           .append("</div>\n");
    }

    @Override
    protected void writeSummary(StringBuilder out, ReviewReport report,
                                Map<String, Integer> summary) {
        out.append("<h2>Summary</h2>\n<table>\n");
        out.append("<tr><th>Total</th><th>Pending</th><th>Accepted</th><th>Rejected</th></tr>\n");
        out.append("<tr><td>").append(summary.getOrDefault("total", 0))
           .append("</td><td>").append(summary.getOrDefault("pending", 0))
           .append("</td><td>").append(summary.getOrDefault("accepted", 0))
           .append("</td><td>").append(summary.getOrDefault("rejected", 0))
           .append("</td></tr>\n</table>\n");

        out.append("<table>\n<tr><th>Severity</th><th>Count</th></tr>\n");
        for (Map.Entry<Severity, Integer> entry : severityCounts(report).entrySet()) {
            out.append("<tr><td><span class=\"sev\" style=\"background:")
               .append(toHex(entry.getKey().getColor())).append("\">")
               .append(entry.getKey().getLabel()).append("</span></td><td>")
               .append(entry.getValue()).append("</td></tr>\n");
        }
        out.append("</table>\n");
    }

    @Override
    protected void writeFindings(StringBuilder out, Map<String, List<ReviewItem>> findingsByFile) {
        out.append("<h2>Findings</h2>\n");
        if (findingsByFile.isEmpty()) {
            out.append("<p>No findings were recorded in this session.</p>\n");
            return;
        }
        for (Map.Entry<String, List<ReviewItem>> entry : findingsByFile.entrySet()) {
            out.append("<h3>").append(escape(entry.getKey().replace('\\', '/'))).append("</h3>\n");
            for (ReviewItem item : entry.getValue()) {
                out.append("<div class=\"finding\">\n");
                out.append("<span class=\"sev\" style=\"background:")
                   .append(toHex(item.getSeverity().getColor())).append("\">")
                   .append(item.getSeverity().getLabel()).append("</span>\n");
                out.append("<strong style=\"margin-left:.6rem\">").append(escape(item.getTitle()))
                   .append("</strong>\n");
                out.append("<span class=\"tag\">").append(item.getIssueId())
                   .append(" &middot; line ")
                   .append(item.getLineNumber() == 0 ? "file-wide" : String.valueOf(item.getLineNumber()))
                   .append(" &middot; ").append(escape(item.getCategory()))
                   .append(" &middot; ").append(item.getStatus().getLabel())
                   .append("</span>\n");
                if (!item.getDescription().isEmpty()) {
                    out.append("<p>").append(escape(item.getDescription())).append("</p>\n");
                }
                if (!item.getSuggestion().isEmpty()) {
                    out.append("<div class=\"fix\"><strong>Suggested fix:</strong> ")
                       .append(escape(item.getSuggestion())).append("</div>\n");
                }
                if (!item.getFeedback().isEmpty()) {
                    out.append("<div class=\"cmt\"><strong>Reviewer comment:</strong> ")
                       .append(escape(item.getFeedback())).append("</div>\n");
                }
                out.append("</div>\n");
            }
        }
    }

    @Override
    protected void writeDecisionRecord(StringBuilder out, ReviewReport report) {
        List<ReviewItem> resolved = resolvedItems(report);
        out.append("<h2>Review decisions</h2>\n");
        if (resolved.isEmpty()) {
            out.append("<p>No findings have been triaged yet.</p>\n");
            return;
        }
        out.append("<table>\n<tr><th>ID</th><th>Decision</th><th>Finding</th>")
           .append("<th>Reviewer comment</th></tr>\n");
        for (ReviewItem item : resolved) {
            out.append("<tr><td>").append(item.getIssueId())
               .append("</td><td>").append(item.getStatus().getLabel())
               .append("</td><td>").append(escape(item.getTitle()))
               .append("</td><td>").append(item.getFeedback().isEmpty()
                       ? "&mdash;" : escape(item.getFeedback()))
               .append("</td></tr>\n");
        }
        out.append("</table>\n");
    }

    @Override
    protected void writeExtraSections(StringBuilder out) {
        if (!getAgentTrace().isBlank()) {
            out.append("<h2>Agent reasoning trace</h2>\n<pre>")
               .append(escape(getAgentTrace())).append("</pre>\n");
        }
        if (!getActionLog().isBlank()) {
            out.append("<h2>Review action log</h2>\n<pre>")
               .append(escape(getActionLog())).append("</pre>\n");
        }
        if (!getUsageSummary().isBlank()) {
            out.append("<h2>Model usage</h2>\n<pre>")
               .append(escape(getUsageSummary())).append("</pre>\n");
        }
    }

    @Override
    protected void writeFooter(StringBuilder out, ReviewReport report) {
        out.append("<footer>Generated by the AI Code Review Agent (EECS3311 course project). ");
        out.append("Findings produced by a language model are suggestions for a human reviewer, ");
        out.append("not a substitute for one.</footer>\n");
        out.append("</div>\n</body>\n</html>\n");
    }

    private String toHex(Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }

    /** Escapes text so it cannot inject markup into the generated page. */
    private String escape(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;");
    }
}
