package com.eecs3311.codereview.agent.tools;

import com.eecs3311.codereview.agent.CodeRetriever;

import java.util.List;

/**
 * Agent tool: search the repository for relevant code.
 *
 * <p>This is the retrieval capability. It lets the agent locate the places that matter -
 * "where are credentials handled", "where is user input parsed" - instead of reading every
 * file, which is what makes an audit of a real repository affordable.</p>
 */
public class SearchCodeTool implements AgentTool {

    private final CodeRetriever retriever;

    public SearchCodeTool(CodeRetriever retriever) {
        this.retriever = retriever;
    }

    @Override
    public String getName() {
        return "search_code";
    }

    @Override
    public String getDescription() {
        return "Searches the whole repository for code related to a topic and returns the "
             + "best-matching regions with file paths and line numbers.";
    }

    @Override
    public String getInputDescription() {
        return "Keywords or a short phrase, for example 'password validation' or 'database query'.";
    }

    @Override
    public String execute(String input) {
        if (retriever == null || !retriever.isIndexed()) {
            return "ERROR: the repository has not been indexed. Import a repository first.";
        }
        if (input == null || input.isBlank()) {
            return "ERROR: no search terms were given.";
        }

        List<CodeRetriever.Snippet> snippets = retriever.retrieve(input.trim(), 4, 12);
        if (snippets.isEmpty()) {
            return "No code in this repository matched '" + input.trim()
                    + "'. Try different terms, or call list_files to see what is there.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Found ").append(snippets.size()).append(" relevant region(s) for '")
          .append(input.trim()).append("':\n\n");
        sb.append(retriever.renderForPrompt(snippets, 6_000));
        return sb.toString();
    }
}
