package com.eecs3311.codereview.export;

import com.eecs3311.codereview.model.ReviewItem;
import com.eecs3311.codereview.model.ReviewReport;
import com.eecs3311.codereview.model.Severity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * TEMPLATE METHOD PATTERN - second application, this time over report generation.
 *
 * <p>Every export produces the same document in the same order: a header, a summary of the
 * counts, the findings grouped by file, the record of what the team decided, and a footer.
 * Only the markup differs. {@link #export} fixes that order and the surrounding file
 * handling; subclasses supply the markup for each section.</p>
 *
 * <p>This is a separate application of the pattern from
 * {@code AbstractReviewStrategy}: different hierarchy, different invariant, different varying
 * steps. It is described here because it is the clearest demonstration of why the invariant
 * belongs in a base class - the four exporters between them would otherwise repeat the same
 * grouping, sorting and counting logic four times.</p>
 */
public abstract class AbstractReportExporter implements ReportExporter {

    /** Date format used in every export. */
    protected static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    /** Optional extra sections the controller can attach before exporting. */
    private String agentTrace = "";
    private String actionLog = "";
    private String usageSummary = "";

    /**
     * TEMPLATE METHOD. Fixes the document structure and the file handling; subclasses fill
     * in the markup. Final so the section order cannot be changed by a subclass.
     */
    @Override
    public final void export(ReviewReport report, Path destination) throws IOException {
        if (report == null) {
            throw new IOException("There is no report to export.");
        }
        if (destination == null) {
            throw new IOException("No destination file was given.");
        }

        StringBuilder document = new StringBuilder();
        Map<String, Integer> summary = report.aggregateItems();

        writeHeader(document, report);
        writeSummary(document, report, summary);
        writeFindings(document, groupByFile(report.getSorted(), report.getRepositoryPath()));
        writeDecisionRecord(document, report);
        writeExtraSections(document);
        writeFooter(document, report);

        Path parent = destination.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        try {
            writeToDisk(document.toString(), destination);
        } catch (IOException e) {
            // Translate the low-level failure into the guidance use case UC04 calls for.
            throw new IOException("Could not write " + destination.getFileName()
                    + ". Check that the file is not open in another program and that there is "
                    + "free space on the disk. (" + e.getMessage() + ")", e);
        }
    }

    // ------------------------------------------------------------------ abstract steps

    /** Writes the document title and metadata. */
    protected abstract void writeHeader(StringBuilder out, ReviewReport report);

    /** Writes the counts table. */
    protected abstract void writeSummary(StringBuilder out, ReviewReport report,
                                         Map<String, Integer> summary);

    /** Writes the findings, already grouped by file and sorted by severity. */
    protected abstract void writeFindings(StringBuilder out,
                                          Map<String, List<ReviewItem>> findingsByFile);

    /** Writes the accepted/rejected record with the team's reasons. */
    protected abstract void writeDecisionRecord(StringBuilder out, ReviewReport report);

    /** Writes the closing section. */
    protected abstract void writeFooter(StringBuilder out, ReviewReport report);

    // ------------------------------------------------------------------ hooks

    /**
     * Hook for optional sections such as the agent trace. The default writes them as plain
     * blocks; {@link HtmlExporter} overrides it to wrap them in markup.
     */
    protected void writeExtraSections(StringBuilder out) {
        if (!agentTrace.isBlank()) {
            out.append("\nAGENT REASONING TRACE\n").append(agentTrace).append('\n');
        }
        if (!actionLog.isBlank()) {
            out.append("\nREVIEW ACTION LOG\n").append(actionLog).append('\n');
        }
        if (!usageSummary.isBlank()) {
            out.append("\nMODEL USAGE\n").append(usageSummary).append('\n');
        }
    }

    /**
     * Hook for how the assembled document reaches the disk. Text formats write the string;
     * {@link PdfExporter} overrides this to build a binary PDF from it.
     */
    protected void writeToDisk(String document, Path destination) throws IOException {
        Files.writeString(destination, document, StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------ shared helpers

    /**
     * Groups findings by file, preserving the severity ordering inside each group.
     *
     * <p>Paths are shortened to be relative to the repository root. An absolute path is
     * noise in a document that will be read by someone else, and it leaks the directory
     * layout of the machine the review was run on.</p>
     */
    protected Map<String, List<ReviewItem>> groupByFile(List<ReviewItem> items,
                                                        String repositoryRoot) {
        Map<String, List<ReviewItem>> grouped = new LinkedHashMap<>();
        for (ReviewItem item : items) {
            grouped.computeIfAbsent(relativise(item.getFilePath(), repositoryRoot),
                    key -> new java.util.ArrayList<>()).add(item);
        }
        return grouped;
    }

    /** Strips the repository root prefix from a path, leaving a portable relative path. */
    protected String relativise(String path, String repositoryRoot) {
        if (path == null) {
            return "(unknown)";
        }
        String normalised = path.replace('\\', '/');
        if (repositoryRoot == null || repositoryRoot.isEmpty()) {
            return normalised;
        }
        String root = repositoryRoot.replace('\\', '/');
        if (!root.endsWith("/")) {
            root = root + "/";
        }
        return normalised.startsWith(root) ? normalised.substring(root.length()) : normalised;
    }

    /** Findings the team resolved, for the decision record. */
    protected List<ReviewItem> resolvedItems(ReviewReport report) {
        return report.getItems().stream().filter(ReviewItem::isResolved)
                .collect(Collectors.toList());
    }

    /** Counts per severity in display order. */
    protected Map<Severity, Integer> severityCounts(ReviewReport report) {
        return report.countBySeverity();
    }

    /** Attaches the agent reasoning trace to the next export. */
    public void setAgentTrace(String agentTrace) {
        this.agentTrace = agentTrace == null ? "" : agentTrace;
    }

    /** Attaches the review action log to the next export. */
    public void setActionLog(String actionLog) {
        this.actionLog = actionLog == null ? "" : actionLog;
    }

    /** Attaches the measured model usage to the next export. */
    public void setUsageSummary(String usageSummary) {
        this.usageSummary = usageSummary == null ? "" : usageSummary;
    }

    protected String getAgentTrace() {
        return agentTrace;
    }

    protected String getActionLog() {
        return actionLog;
    }

    protected String getUsageSummary() {
        return usageSummary;
    }
}
