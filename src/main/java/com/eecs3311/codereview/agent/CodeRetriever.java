package com.eecs3311.codereview.agent;

import com.eecs3311.codereview.model.FileNode;
import com.eecs3311.codereview.repo.RepoManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Retrieval over the imported repository (feature F13, and the context source for F10).
 *
 * <p>A language model cannot be shown a whole codebase, so a question such as "where is the
 * password validated?" has to be answered by finding the few relevant places first and
 * sending only those. This class does that selection: it scores every file against the query
 * and returns the best-matching regions with their line numbers.</p>
 *
 * <p>Scoring is TF-IDF-like. A term that appears in almost every file (such as {@code public})
 * carries almost no weight, while a term that appears in two files out of two hundred is
 * strongly discriminating. Matches in a file's name or path are weighted more heavily than
 * matches in its body, because a developer asking about "authentication" is usually looking
 * for {@code AuthenticationService}. The index is built once per import and reused.</p>
 */
public class CodeRetriever {

    /** One retrieved region of one file. */
    public static class Snippet {
        private final String filePath;
        private final int startLine;
        private final String text;
        private final double score;

        Snippet(String filePath, int startLine, String text, double score) {
            this.filePath = filePath;
            this.startLine = startLine;
            this.text = text;
            this.score = score;
        }

        public String getFilePath() {
            return filePath;
        }

        public int getStartLine() {
            return startLine;
        }

        public String getText() {
            return text;
        }

        public double getScore() {
            return score;
        }

        @Override
        public String toString() {
            return filePath + ":" + startLine + " (score " + String.format("%.2f", score) + ")";
        }
    }

    private static final Set<String> STOP_WORDS = Set.of(
            "the", "and", "for", "that", "with", "this", "from", "not", "but", "are", "was",
            "public", "private", "protected", "static", "void", "final", "class", "import",
            "return", "new", "int", "string", "boolean", "null", "true", "false", "what",
            "where", "how", "why", "does", "which", "when", "who", "can", "you", "show", "find");

    private final RepoManager repoManager;
    private final Map<String, String> fileContents = new HashMap<>();
    private final Map<String, Integer> documentFrequency = new HashMap<>();
    private int indexedFileCount;

    public CodeRetriever(RepoManager repoManager) {
        this.repoManager = repoManager;
    }

    /**
     * Builds the index over the imported repository. Called once after an import; a repeat
     * call rebuilds it, which is how the index stays correct after a different folder is
     * opened.
     *
     * @param maxFiles upper bound so importing a very large repository stays responsive
     * @return number of files indexed
     */
    public int buildIndex(int maxFiles) {
        fileContents.clear();
        documentFrequency.clear();
        indexedFileCount = 0;

        if (repoManager == null || !repoManager.isLoaded()) {
            return 0;
        }
        List<FileNode> files = repoManager.allFiles();
        for (FileNode file : files) {
            if (indexedFileCount >= maxFiles) {
                break;
            }
            String content = repoManager.readFileQuietly(file.getPath());
            if (content.isEmpty()) {
                continue;
            }
            fileContents.put(file.getPath(), content);
            indexedFileCount++;
            for (String term : new HashSet<>(tokenise(content))) {
                documentFrequency.merge(term, 1, Integer::sum);
            }
        }
        return indexedFileCount;
    }

    /**
     * Returns the regions of the repository most relevant to a query.
     *
     * @param query       natural-language question or keywords
     * @param maxSnippets how many regions to return
     * @param linesOfContext how many lines to include around each match
     */
    public List<Snippet> retrieve(String query, int maxSnippets, int linesOfContext) {
        List<Snippet> results = new ArrayList<>();
        if (query == null || query.isBlank() || fileContents.isEmpty()) {
            return results;
        }
        List<String> terms = tokenise(query);
        if (terms.isEmpty()) {
            return results;
        }

        for (Map.Entry<String, String> entry : fileContents.entrySet()) {
            String path = entry.getKey();
            String content = entry.getValue();
            double fileScore = scoreFile(path, content, terms);
            if (fileScore <= 0) {
                continue;
            }
            Snippet best = bestRegion(path, content, terms, linesOfContext, fileScore);
            if (best != null) {
                results.add(best);
            }
        }
        results.sort(Comparator.comparingDouble(Snippet::getScore).reversed());
        return results.stream().limit(Math.max(1, maxSnippets)).collect(Collectors.toList());
    }

    /** Scores a whole file: inverse-document-frequency weighted term hits, plus a path bonus. */
    private double scoreFile(String path, String content, List<String> terms) {
        String lowerContent = content.toLowerCase(Locale.ROOT);
        String lowerPath = path.toLowerCase(Locale.ROOT);
        double score = 0;
        for (String term : terms) {
            int occurrences = countOccurrences(lowerContent, term);
            if (occurrences == 0 && !lowerPath.contains(term)) {
                continue;
            }
            int df = documentFrequency.getOrDefault(term, 1);
            double idf = Math.log((indexedFileCount + 1.0) / df);
            score += Math.log(1 + occurrences) * idf;
            if (lowerPath.contains(term)) {
                score += 2.5 * idf; // the file is named after the thing being asked about
            }
        }
        return score;
    }

    /** Finds the densest window of matching lines inside one file. */
    private Snippet bestRegion(String path, String content, List<String> terms,
                               int linesOfContext, double fileScore) {
        String[] lines = content.split("\n", -1);
        int window = Math.max(4, linesOfContext);
        int bestStart = 0;
        int bestHits = 0;

        for (int start = 0; start < lines.length; start++) {
            int hits = 0;
            for (int i = start; i < Math.min(lines.length, start + window); i++) {
                String lower = lines[i].toLowerCase(Locale.ROOT);
                for (String term : terms) {
                    if (lower.contains(term)) {
                        hits++;
                    }
                }
            }
            if (hits > bestHits) {
                bestHits = hits;
                bestStart = start;
            }
        }
        if (bestHits == 0) {
            // The match was in the path only; return the head of the file as context.
            bestStart = 0;
        }
        int end = Math.min(lines.length, bestStart + window);
        StringBuilder sb = new StringBuilder();
        for (int i = bestStart; i < end; i++) {
            sb.append(i + 1).append(": ").append(lines[i]).append('\n');
        }
        return new Snippet(path, bestStart + 1, sb.toString(), fileScore + bestHits * 0.5);
    }

    /**
     * Renders retrieved snippets for inclusion in a prompt, with a character budget so a
     * broad query cannot blow the context window.
     */
    public String renderForPrompt(List<Snippet> snippets, int characterBudget) {
        if (snippets == null || snippets.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Snippet snippet : snippets) {
            String block = "--- " + relativise(snippet.getFilePath())
                    + " (from line " + snippet.getStartLine() + ")\n"
                    + snippet.getText() + "\n";
            if (sb.length() + block.length() > characterBudget) {
                break;
            }
            sb.append(block);
        }
        return sb.toString();
    }

    private String relativise(String path) {
        return repoManager == null ? path : repoManager.relativise(path);
    }

    /** Plain keyword search used by the CLI {@code search} command and the agent's tool. */
    public List<String> grep(String literal, int maxResults) {
        List<String> hits = new ArrayList<>();
        if (literal == null || literal.isBlank()) {
            return hits;
        }
        String needle = literal.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> entry : fileContents.entrySet()) {
            String[] lines = entry.getValue().split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                if (lines[i].toLowerCase(Locale.ROOT).contains(needle)) {
                    hits.add(relativise(entry.getKey()) + ":" + (i + 1) + ": " + lines[i].trim());
                    if (hits.size() >= maxResults) {
                        return hits;
                    }
                }
            }
        }
        return hits;
    }

    private int countOccurrences(String haystack, String needle) {
        int count = 0;
        int index = haystack.indexOf(needle);
        while (index >= 0) {
            count++;
            index = haystack.indexOf(needle, index + needle.length());
        }
        return count;
    }

    private List<String> tokenise(String text) {
        List<String> terms = new ArrayList<>();
        for (String token : text.toLowerCase(Locale.ROOT).split("[^a-z0-9_]+")) {
            if (token.length() > 2 && !STOP_WORDS.contains(token)) {
                terms.add(token);
            }
            // Split camelCase identifiers so "getUserPassword" also matches "password".
            if (token.length() > 6) {
                for (String part : token.split("(?<=[a-z])(?=[A-Z])")) {
                    if (part.length() > 3 && !STOP_WORDS.contains(part) && !terms.contains(part)) {
                        terms.add(part);
                    }
                }
            }
        }
        return terms;
    }

    /** Number of files currently indexed; shown in the status bar after an import. */
    public int getIndexedFileCount() {
        return indexedFileCount;
    }

    /** True when an index has been built. */
    public boolean isIndexed() {
        return indexedFileCount > 0;
    }
}
