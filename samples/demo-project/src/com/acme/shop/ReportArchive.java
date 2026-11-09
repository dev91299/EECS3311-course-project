package com.acme.shop;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Sample code for demonstrating the AI Code Review Agent.
 *
 * WARNING: this file contains deliberate defects. It was added to give the review features
 * a class of bug the other sample files do not have - resources that are never closed, and
 * a path-handling flaw - so the analysis can be seen finding something new. Do not copy it.
 */
public class ReportArchive {

    private String archiveDirectory;

    public ReportArchive(String archiveDirectory) {
        this.archiveDirectory = archiveDirectory;
    }

    /**
     * Deliberate defect: the reader is never closed, so the file handle leaks on every call.
     * After enough calls the process runs out of file descriptors.
     */
    public List<String> readArchivedReport(String fileName) throws IOException {
        List<String> lines = new ArrayList<>();
        BufferedReader reader = new BufferedReader(
                new FileReader(archiveDirectory + "/" + fileName));
        String line = reader.readLine();
        while (line != null) {
            lines.add(line);
            line = reader.readLine();
        }
        return lines;
    }

    /**
     * Deliberate defect: the writer is not closed on the exception path, so a failed write
     * leaves a partially written file and a leaked handle.
     */
    public void writeReport(String fileName, String contents) {
        try {
            FileWriter writer = new FileWriter(archiveDirectory + "/" + fileName);
            writer.write(contents);
            writer.close();
        } catch (IOException e) {
            System.out.println("Could not write the report");
        }
    }

    /**
     * Deliberate defect: the file name is concatenated into a path with no validation, so a
     * caller passing "../../etc/passwd" reads outside the archive directory entirely.
     */
    public String loadByUserRequest(String userSuppliedName) throws IOException {
        StringBuilder result = new StringBuilder();
        for (String line : readArchivedReport(userSuppliedName)) {
            result.append(line).append("\n");
        }
        return result.toString();
    }

    /**
     * Deliberate defect: compares strings with == instead of equals, so this is almost
     * always false even when the names match.
     */
    public boolean isSameArchive(String otherDirectory) {
        return archiveDirectory == otherDirectory;
    }
}
