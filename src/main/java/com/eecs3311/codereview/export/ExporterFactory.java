package com.eecs3311.codereview.export;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * FACTORY METHOD PATTERN - second application, this time selecting an export format.
 *
 * <p>The GUI's export dialog and the CLI's {@code export} command both name a format as a
 * string; this factory turns that string into the right {@link ReportExporter}. Neither
 * interface references a concrete exporter class, so adding a format - CSV, SARIF for CI
 * tooling - means adding one class and one line here.</p>
 */
public final class ExporterFactory {

    private static final Map<String, java.util.function.Supplier<AbstractReportExporter>> FORMATS =
            new LinkedHashMap<>();

    static {
        FORMATS.put("pdf", PdfExporter::new);
        FORMATS.put("md", MarkdownExporter::new);
        FORMATS.put("markdown", MarkdownExporter::new);
        FORMATS.put("html", HtmlExporter::new);
        FORMATS.put("json", JsonExporter::new);
    }

    private ExporterFactory() {
    }

    /**
     * FACTORY METHOD: creates the exporter for a format name.
     *
     * @param format one of pdf, md, html, json; case-insensitive
     * @return the exporter, or empty when the format is unknown
     */
    public static Optional<AbstractReportExporter> create(String format) {
        if (format == null) {
            return Optional.empty();
        }
        String key = format.trim().toLowerCase().replace(".", "");
        java.util.function.Supplier<AbstractReportExporter> supplier = FORMATS.get(key);
        return supplier == null ? Optional.empty() : Optional.of(supplier.get());
    }

    /** Infers the format from a file name's extension, so "report.md" selects Markdown. */
    public static Optional<AbstractReportExporter> createForFileName(String fileName) {
        if (fileName == null) {
            return Optional.empty();
        }
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? Optional.empty() : create(fileName.substring(dot + 1));
    }

    /** The distinct format names offered in the UI. */
    public static List<String> supportedFormats() {
        List<String> formats = new ArrayList<>();
        formats.add("pdf");
        formats.add("md");
        formats.add("html");
        formats.add("json");
        return formats;
    }

    /** Format names with descriptions, for the CLI help text. */
    public static String describe() {
        StringBuilder sb = new StringBuilder();
        for (String format : supportedFormats()) {
            create(format).ifPresent(exporter ->
                    sb.append(String.format("  %-6s %s%n", format, exporter.getDescription())));
        }
        return sb.toString();
    }
}
