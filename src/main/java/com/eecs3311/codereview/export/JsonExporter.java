package com.eecs3311.codereview.export;

import com.eecs3311.codereview.json.Json;
import com.eecs3311.codereview.model.ReviewItem;
import com.eecs3311.codereview.model.ReviewReport;
import com.eecs3311.codereview.model.Severity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Exports the review as JSON (feature F09).
 *
 * <p>This is the machine-readable format: a continuous-integration job can read it to decide
 * whether to fail a build, and the application itself can read it back to restore a session,
 * which is what makes cross-version comparison across separate runs possible.</p>
 *
 * <p>Because the whole document is one JSON value, this exporter builds the structure in the
 * header step and lets the remaining template steps contribute to it, then serialises once.</p>
 */
public class JsonExporter extends AbstractReportExporter {

    private final Map<String, Object> document = new LinkedHashMap<>();

    @Override
    public String getExtension() {
        return "json";
    }

    @Override
    public String getDescription() {
        return "JSON data (machine-readable, for CI pipelines)";
    }

    @Override
    protected void writeHeader(StringBuilder out, ReviewReport report) {
        document.clear();
        document.put("tool", "AI Code Review Agent");
        document.put("schemaVersion", "2.0");
        document.put("repository", report.getRepositoryPath());
        document.put("sessionStarted", report.getCreatedAt().toString());
        document.put("generated", java.time.Instant.now().toString());
        document.put("verdict", report.getHealthVerdict());
    }

    @Override
    protected void writeSummary(StringBuilder out, ReviewReport report,
                                Map<String, Integer> summary) {
        document.put("summary", new LinkedHashMap<>(summary));
        Map<String, Object> bySeverity = new LinkedHashMap<>();
        for (Map.Entry<Severity, Integer> entry : severityCounts(report).entrySet()) {
            bySeverity.put(entry.getKey().name(), entry.getValue());
        }
        document.put("countsBySeverity", bySeverity);
    }

    @Override
    protected void writeFindings(StringBuilder out, Map<String, List<ReviewItem>> findingsByFile) {
        List<Object> findings = new ArrayList<>();
        for (List<ReviewItem> items : findingsByFile.values()) {
            for (ReviewItem item : items) {
                findings.add(item.toMap());
            }
        }
        document.put("findings", findings);
    }

    @Override
    protected void writeDecisionRecord(StringBuilder out, ReviewReport report) {
        List<Object> decisions = new ArrayList<>();
        for (ReviewItem item : resolvedItems(report)) {
            Map<String, Object> decision = new LinkedHashMap<>();
            decision.put("issueId", item.getIssueId());
            decision.put("decision", item.getStatus().name());
            decision.put("title", item.getTitle());
            decision.put("comment", item.getFeedback());
            decisions.add(decision);
        }
        document.put("decisions", decisions);
    }

    @Override
    protected void writeExtraSections(StringBuilder out) {
        if (!getAgentTrace().isBlank()) {
            document.put("agentTrace", getAgentTrace());
        }
        if (!getActionLog().isBlank()) {
            document.put("actionLog", getActionLog());
        }
        if (!getUsageSummary().isBlank()) {
            document.put("modelUsage", getUsageSummary());
        }
    }

    @Override
    protected void writeFooter(StringBuilder out, ReviewReport report) {
        // The whole document is serialised here, once the preceding steps have filled it.
        out.setLength(0);
        out.append(Json.stringify(document));
    }
}
