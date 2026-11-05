package com.eecs3311.codereview.export;

import com.eecs3311.codereview.model.ReviewReport;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Writes a {@link ReviewReport} to a file in one output format (feature F09).
 *
 * <p>Four formats are implemented - PDF, Markdown, HTML and JSON - and the choice is made at
 * run time by {@link ExporterFactory}. Keeping them behind one interface means the GUI's
 * export dialog and the CLI's {@code export} command both just name a format.</p>
 */
public interface ReportExporter {

    /**
     * Writes the report.
     *
     * @param report      the findings to write
     * @param destination the file to create, overwritten if it exists
     * @throws IOException when the file cannot be written, for example when the disk is full
     *                     or the file is locked by another program
     */
    void export(ReviewReport report, Path destination) throws IOException;

    /** The file extension this exporter produces, without the dot. */
    String getExtension();

    /** A label for the format picker, for example "PDF document". */
    String getDescription();
}
