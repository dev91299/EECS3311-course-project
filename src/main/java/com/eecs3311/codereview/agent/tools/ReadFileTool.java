package com.eecs3311.codereview.agent.tools;

import com.eecs3311.codereview.model.FileNode;
import com.eecs3311.codereview.repo.RepoManager;

import java.util.Optional;

/**
 * Agent tool: read one file's contents.
 *
 * <p>Line numbers are prefixed onto every line. That is not cosmetic: the agent is asked to
 * report findings against specific lines, and a model shown unnumbered text reliably
 * miscounts. Prefixing the numbers removes the need for it to count at all.</p>
 *
 * <p>The path is resolved by {@code RepoManager}, which refuses anything outside the
 * imported repository, so a traversal path produced by the model cannot read arbitrary files.</p>
 */
public class ReadFileTool implements AgentTool {

    private static final int MAX_CHARACTERS = 8_000;

    private final RepoManager repoManager;

    public ReadFileTool(RepoManager repoManager) {
        this.repoManager = repoManager;
    }

    @Override
    public String getName() {
        return "read_file";
    }

    @Override
    public String getDescription() {
        return "Reads one source file and returns its contents with line numbers.";
    }

    @Override
    public String getInputDescription() {
        return "The file path, as shown by list_files (for example src/main/java/App.java).";
    }

    @Override
    public String execute(String input) {
        if (repoManager == null || !repoManager.isLoaded()) {
            return "ERROR: no repository has been imported.";
        }
        if (input == null || input.isBlank()) {
            return "ERROR: no file path was given. Call list_files first to see what exists.";
        }

        Optional<FileNode> node = repoManager.findFile(input.trim());
        if (node.isEmpty()) {
            return "ERROR: no file named '" + input.trim()
                    + "' is in this repository. Call list_files to see the available paths.";
        }

        String content = repoManager.readFileQuietly(node.get().getPath());
        if (content.isEmpty()) {
            return "The file " + input.trim() + " is empty or could not be read.";
        }

        String[] lines = content.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        sb.append("File: ").append(repoManager.relativise(node.get().getPath()))
          .append(" (").append(lines.length).append(" lines)\n");

        for (int i = 0; i < lines.length; i++) {
            String entry = (i + 1) + ": " + lines[i] + "\n";
            if (sb.length() + entry.length() > MAX_CHARACTERS) {
                sb.append("... truncated at line ").append(i + 1).append(" of ")
                  .append(lines.length).append(" to stay within the context budget.\n");
                break;
            }
            sb.append(entry);
        }
        return sb.toString();
    }
}
