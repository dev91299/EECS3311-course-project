package com.eecs3311.codereview.agent.tools;

import com.eecs3311.codereview.model.FileNode;
import com.eecs3311.codereview.repo.RepoManager;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Agent tool: inventory the imported repository.
 *
 * <p>Almost every audit begins here, because the agent cannot choose what to examine until
 * it knows what exists. The output is capped and summarised rather than complete: a listing
 * of two thousand files would consume the context budget the agent needs for actually
 * reading code.</p>
 */
public class ListFilesTool implements AgentTool {

    private static final int MAX_LISTED = 60;

    private final RepoManager repoManager;

    public ListFilesTool(RepoManager repoManager) {
        this.repoManager = repoManager;
    }

    @Override
    public String getName() {
        return "list_files";
    }

    @Override
    public String getDescription() {
        return "Lists the source files in the imported repository, with sizes. "
             + "Use this first to decide what is worth examining.";
    }

    @Override
    public String getInputDescription() {
        return "Optional: a file extension such as 'java' or a path fragment to filter by. "
             + "Leave empty to list everything.";
    }

    @Override
    public String execute(String input) {
        if (repoManager == null || !repoManager.isLoaded()) {
            return "ERROR: no repository has been imported. Nothing can be listed.";
        }
        String filter = input == null ? "" : input.trim().toLowerCase().replace("*", "").replace(".", "");

        List<FileNode> files = repoManager.allFiles();
        List<FileNode> matching = filter.isEmpty()
                ? files
                : files.stream()
                       .filter(f -> f.getExtension().equals(filter)
                                 || f.getPath().toLowerCase().contains(filter))
                       .collect(Collectors.toList());

        if (matching.isEmpty()) {
            return "No files matched '" + input + "'. The repository contains "
                    + files.size() + " source files in total.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Repository contains ").append(files.size()).append(" source files");
        if (!filter.isEmpty()) {
            sb.append("; ").append(matching.size()).append(" match '").append(filter).append('\'');
        }
        sb.append(".\n");

        matching.stream()
                .sorted((a, b) -> Long.compare(b.getTotalSizeBytes(), a.getTotalSizeBytes()))
                .limit(MAX_LISTED)
                .forEach(f -> sb.append("  ")
                        .append(repoManager.relativise(f.getPath()))
                        .append("  (").append(FileNode.humanSize(f.getTotalSizeBytes())).append(")\n"));

        if (matching.size() > MAX_LISTED) {
            sb.append("  ... and ").append(matching.size() - MAX_LISTED)
              .append(" more (listed largest first).\n");
        }
        return sb.toString();
    }
}
