package com.eecs3311.codereview.agent.tools;

import com.eecs3311.codereview.repo.RepoManager;

import java.io.IOException;
import java.util.List;

/**
 * Agent tool: inspect the repository's Git history.
 *
 * <p>Gives the agent access to change history, so an audit can be scoped to what recently
 * changed rather than to the whole codebase - which is usually what a reviewer actually
 * wants before a merge.</p>
 */
public class GitHistoryTool implements AgentTool {

    private final RepoManager repoManager;

    public GitHistoryTool(RepoManager repoManager) {
        this.repoManager = repoManager;
    }

    @Override
    public String getName() {
        return "git_history";
    }

    @Override
    public String getDescription() {
        return "Shows recent commits, or the files changed between two commits. "
             + "Only works when the imported folder is a Git repository.";
    }

    @Override
    public String getInputDescription() {
        return "Empty for the recent commit list, or '<commitA>..<commitB>' for the files "
             + "changed between two commits.";
    }

    @Override
    public String execute(String input) {
        if (repoManager == null || !repoManager.isLoaded()) {
            return "ERROR: no repository has been imported.";
        }
        if (!repoManager.isGitRepository()) {
            return "The imported folder is not a Git repository, so there is no history to read. "
                 + "Continue the audit using the files themselves.";
        }

        try {
            String argument = input == null ? "" : input.trim();
            if (argument.contains("..")) {
                String[] parts = argument.split("\\.\\.", 2);
                List<String> changed = repoManager.getChangedFiles(parts[0].trim(), parts[1].trim());
                if (changed.isEmpty()) {
                    return "No files changed between " + parts[0].trim() + " and " + parts[1].trim() + ".";
                }
                StringBuilder sb = new StringBuilder();
                sb.append(changed.size()).append(" file(s) changed between ")
                  .append(parts[0].trim()).append(" and ").append(parts[1].trim()).append(":\n");
                changed.forEach(f -> sb.append("  ").append(f).append('\n'));
                return sb.toString();
            }

            List<String> commits = repoManager.listCommits(15);
            if (commits.isEmpty()) {
                return "The repository has no commits yet.";
            }
            StringBuilder sb = new StringBuilder("Recent commits (hash, date, subject):\n");
            commits.forEach(c -> sb.append("  ").append(c).append('\n'));
            return sb.toString();

        } catch (IOException e) {
            return "ERROR reading Git history: " + e.getMessage();
        }
    }
}
