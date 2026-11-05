package com.eecs3311.codereview.agent;

import com.eecs3311.codereview.json.Json;
import com.eecs3311.codereview.model.ReviewItem;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The agent's memory, in two layers.
 *
 * <p><strong>Short-term memory</strong> is the scratchpad for one audit run: the observations
 * the agent has collected so far, which it re-reads before deciding each next step.</p>
 *
 * <p><strong>Long-term memory</strong> survives across sessions. It records what the
 * development team decided about previous findings - which were accepted and which were
 * rejected, and why - and persists to {@code .agent-memory/memory.json} beside the imported
 * repository. Before every analysis the relevant entries are retrieved by keyword and
 * injected into the prompt, so a finding the team has already dismissed as not applicable to
 * this project is not raised again.</p>
 *
 * <p>This is what makes the system adaptive rather than stateless, and it is the mechanism
 * behind the Stage 1 claim that the agent "captures human feedback to adapt to
 * project-specific conventions".</p>
 */
public class AgentMemory {

    private static final String MEMORY_DIRECTORY = ".agent-memory";
    private static final String MEMORY_FILE = "memory.json";
    private static final int MAX_LONG_TERM_ENTRIES = 300;

    /** One remembered decision. */
    public static class MemoryEntry {
        private final String fingerprint;
        private final String title;
        private final String category;
        private final String decision;
        private final String reason;
        private final String filePath;
        private int occurrences;

        MemoryEntry(String fingerprint, String title, String category, String decision,
                    String reason, String filePath, int occurrences) {
            this.fingerprint = fingerprint;
            this.title = title;
            this.category = category;
            this.decision = decision;
            this.reason = reason;
            this.filePath = filePath;
            this.occurrences = occurrences;
        }

        public String getTitle() {
            return title;
        }

        public String getCategory() {
            return category;
        }

        public String getDecision() {
            return decision;
        }

        public String getReason() {
            return reason;
        }

        public String getFilePath() {
            return filePath;
        }

        public int getOccurrences() {
            return occurrences;
        }

        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("fingerprint", fingerprint);
            map.put("title", title);
            map.put("category", category);
            map.put("decision", decision);
            map.put("reason", reason);
            map.put("filePath", filePath);
            map.put("occurrences", occurrences);
            return map;
        }

        @Override
        public String toString() {
            return "[" + decision + "] " + title
                    + (reason.isEmpty() ? "" : " - team said: " + reason);
        }
    }

    private final List<String> shortTerm = new ArrayList<>();
    private final Map<String, MemoryEntry> longTerm = new LinkedHashMap<>();
    private Path memoryPath;

    // ------------------------------------------------------------------ short term

    /** Appends an observation to the current run's scratchpad. */
    public void remember(String observation) {
        if (observation != null && !observation.isBlank()) {
            shortTerm.add(observation.trim());
        }
    }

    /** Clears the scratchpad at the start of a new audit. */
    public void clearShortTerm() {
        shortTerm.clear();
    }

    /** The scratchpad contents, newest last. */
    public List<String> getShortTerm() {
        return new ArrayList<>(shortTerm);
    }

    /**
     * The scratchpad rendered for a prompt, capped so a long audit cannot overflow the
     * context window. The most recent observations are kept, because they are what the next
     * decision depends on.
     */
    public String renderShortTerm(int maxEntries) {
        if (shortTerm.isEmpty()) {
            return "(nothing observed yet)";
        }
        int from = Math.max(0, shortTerm.size() - maxEntries);
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < shortTerm.size(); i++) {
            sb.append(i + 1).append(". ").append(shortTerm.get(i)).append('\n');
        }
        if (from > 0) {
            sb.insert(0, "(" + from + " earlier observations omitted)\n");
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ long term

    /** Points long-term memory at a repository and loads any memory already stored there. */
    public void attachToRepository(Path repositoryRoot) {
        if (repositoryRoot == null) {
            return;
        }
        this.memoryPath = repositoryRoot.resolve(MEMORY_DIRECTORY).resolve(MEMORY_FILE);
        load();
    }

    /**
     * Records the team's decision about a finding. Rejections are the valuable half: they
     * tell the agent what this project does not consider a problem.
     */
    public void recordDecision(ReviewItem item) {
        if (item == null || !item.isResolved()) {
            return;
        }
        String fingerprint = item.getFingerprint();
        MemoryEntry existing = longTerm.get(fingerprint);
        if (existing != null) {
            existing.occurrences++;
            return;
        }
        longTerm.put(fingerprint, new MemoryEntry(
                fingerprint,
                item.getTitle(),
                item.getCategory(),
                item.getStatus().getLabel(),
                item.getFeedback(),
                item.getFilePath(),
                1));
        trim();
        save();
    }

    private void trim() {
        if (longTerm.size() <= MAX_LONG_TERM_ENTRIES) {
            return;
        }
        List<String> oldest = new ArrayList<>(longTerm.keySet())
                .subList(0, longTerm.size() - MAX_LONG_TERM_ENTRIES);
        for (String key : new ArrayList<>(oldest)) {
            longTerm.remove(key);
        }
    }

    /**
     * Retrieves the remembered decisions most relevant to a piece of code, scored by how
     * many of the entry's significant words appear in the code or its path.
     *
     * <p>Relevance matters here: dumping all 300 remembered decisions into every prompt would
     * both exceed the context budget and bury the ones that apply.</p>
     *
     * @param code     the code about to be analysed
     * @param filePath the file it came from
     * @param limit    how many entries to return
     */
    public List<MemoryEntry> recall(String code, String filePath, int limit) {
        if (longTerm.isEmpty()) {
            return new ArrayList<>();
        }
        String haystack = ((code == null ? "" : code) + " " + (filePath == null ? "" : filePath))
                .toLowerCase(Locale.ROOT);

        return longTerm.values().stream()
                .map(entry -> Map.entry(entry, score(entry, haystack, filePath)))
                .filter(pair -> pair.getValue() > 0)
                .sorted(Map.Entry.<MemoryEntry, Integer>comparingByValue().reversed())
                .limit(Math.max(1, limit))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    private int score(MemoryEntry entry, String haystack, String filePath) {
        int score = 0;
        if (filePath != null && entry.filePath.equals(filePath)) {
            score += 5; // a decision about this very file is always relevant
        }
        for (String word : significantWords(entry.title + " " + entry.category)) {
            if (haystack.contains(word)) {
                score += 2;
            }
        }
        for (String word : significantWords(entry.reason)) {
            if (haystack.contains(word)) {
                score += 1;
            }
        }
        if (entry.decision.equalsIgnoreCase("Rejected")) {
            score += 1; // rejections carry more instruction than acceptances
        }
        return score;
    }

    private static final Set<String> STOP_WORDS = Set.of(
            "the", "and", "for", "that", "with", "this", "from", "not", "but", "are", "was",
            "has", "have", "been", "will", "would", "should", "could", "when", "where", "which",
            "there", "their", "them", "then", "than", "into", "over", "more", "most", "some",
            "such", "only", "very", "also", "here", "does", "doing", "done", "make", "made");

    private List<String> significantWords(String text) {
        if (text == null || text.isBlank()) {
            return new ArrayList<>();
        }
        return java.util.Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+"))
                .filter(w -> w.length() > 3 && !STOP_WORDS.contains(w))
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * The recalled entries rendered for injection into a prompt, phrased as instructions so
     * the model acts on them rather than merely reading them.
     */
    public String renderRecall(String code, String filePath, int limit) {
        List<MemoryEntry> relevant = recall(code, filePath, limit);
        if (relevant.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (MemoryEntry entry : relevant) {
            if ("Rejected".equalsIgnoreCase(entry.decision)) {
                sb.append("- The team REJECTED the finding \"").append(entry.title).append("\"");
                if (!entry.reason.isBlank()) {
                    sb.append(", saying: ").append(entry.reason);
                }
                sb.append(". Do not report this again unless the circumstances clearly differ.\n");
            } else {
                sb.append("- The team ACCEPTED the finding \"").append(entry.title)
                  .append("\", so concerns of this kind are worth reporting here.\n");
            }
        }
        return sb.toString();
    }

    /** Every remembered decision, for the CLI {@code memory} command and the GUI panel. */
    public List<MemoryEntry> getAll() {
        return longTerm.values().stream()
                .sorted(Comparator.comparingInt(MemoryEntry::getOccurrences).reversed())
                .collect(Collectors.toList());
    }

    /** Number of remembered decisions. */
    public int size() {
        return longTerm.size();
    }

    /** Erases long-term memory, both in process and on disk. */
    public void forgetAll() {
        longTerm.clear();
        save();
    }

    /** Where memory is stored, shown by the CLI so the user can inspect or delete the file. */
    public String getStorageLocation() {
        return memoryPath == null ? "(not attached to a repository)" : memoryPath.toString();
    }

    // ------------------------------------------------------------------ persistence

    private void load() {
        longTerm.clear();
        if (memoryPath == null || !Files.isReadable(memoryPath)) {
            return;
        }
        try {
            String text = Files.readString(memoryPath, StandardCharsets.UTF_8);
            for (Object raw : Json.parseArray(text)) {
                if (!(raw instanceof Map)) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) raw;
                String fingerprint = Json.getString(map, "", "fingerprint");
                if (fingerprint.isEmpty()) {
                    continue;
                }
                longTerm.put(fingerprint, new MemoryEntry(
                        fingerprint,
                        Json.getString(map, "", "title"),
                        Json.getString(map, "General", "category"),
                        Json.getString(map, "Rejected", "decision"),
                        Json.getString(map, "", "reason"),
                        Json.getString(map, "", "filePath"),
                        Json.getInt(map, 1, "occurrences")));
            }
        } catch (IOException | RuntimeException e) {
            System.err.println("[memory] Could not read " + memoryPath + ": " + e.getMessage());
        }
    }

    private void save() {
        if (memoryPath == null) {
            return;
        }
        try {
            Files.createDirectories(memoryPath.getParent());
            List<Object> entries = longTerm.values().stream()
                    .map(MemoryEntry::toMap)
                    .collect(Collectors.toList());
            Files.writeString(memoryPath, Json.stringify(entries), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("[memory] Could not write " + memoryPath + ": " + e.getMessage());
        }
    }

    /** Used by tests to point memory at a temporary directory. */
    void attachToPathForTesting(String path) {
        this.memoryPath = Paths.get(path);
    }
}
