package com.eecs3311.codereview.agent.strategy;

/**
 * Everything a review strategy needs in order to analyse one file.
 *
 * <p>Bundling these into one object keeps {@code executeStrategy} to a single parameter, so
 * adding a new input later (an additional retrieved snippet, say) does not change the
 * signature every strategy implements.</p>
 */
public class AnalysisContext {

    private final String filePath;
    private final String code;
    private final String language;
    private final String conventions;
    private final String memoryNotes;
    private final String retrievedContext;

    /**
     * @param filePath         file the code came from, recorded on each finding
     * @param code             the source text to review
     * @param language         language label, used for the markdown fence and the role prompt
     * @param conventions      project rules, from the .rules file
     * @param memoryNotes      remembered developer feedback, so rejected findings are not repeated
     * @param retrievedContext related snippets pulled from elsewhere in the repository
     */
    public AnalysisContext(String filePath, String code, String language, String conventions,
                           String memoryNotes, String retrievedContext) {
        this.filePath = filePath == null ? "(unsaved selection)" : filePath;
        this.code = code == null ? "" : code;
        this.language = language == null || language.isBlank() ? detectLanguage(this.filePath) : language;
        this.conventions = conventions == null ? "" : conventions;
        this.memoryNotes = memoryNotes == null ? "" : memoryNotes;
        this.retrievedContext = retrievedContext == null ? "" : retrievedContext;
    }

    /** Convenience constructor for a bare file with no extra context. */
    public static AnalysisContext of(String filePath, String code) {
        return new AnalysisContext(filePath, code, null, "", "", "");
    }

    public String getFilePath() {
        return filePath;
    }

    public String getCode() {
        return code;
    }

    public String getLanguage() {
        return language;
    }

    public String getConventions() {
        return conventions;
    }

    public String getMemoryNotes() {
        return memoryNotes;
    }

    public String getRetrievedContext() {
        return retrievedContext;
    }

    /** Number of lines in the code, used to validate line numbers the model returns. */
    public int getLineCount() {
        return code.isEmpty() ? 0 : code.split("\n", -1).length;
    }

    /** True when there is nothing worth sending to the model. */
    public boolean isEmpty() {
        return code.isBlank();
    }

    /** Infers a language label from the file extension. */
    private static String detectLanguage(String path) {
        String lower = path.toLowerCase();
        if (lower.endsWith(".java")) {
            return "java";
        }
        if (lower.endsWith(".py")) {
            return "python";
        }
        if (lower.endsWith(".js") || lower.endsWith(".jsx")) {
            return "javascript";
        }
        if (lower.endsWith(".ts") || lower.endsWith(".tsx")) {
            return "typescript";
        }
        if (lower.endsWith(".c") || lower.endsWith(".h")) {
            return "c";
        }
        if (lower.endsWith(".cpp") || lower.endsWith(".hpp") || lower.endsWith(".cc")) {
            return "cpp";
        }
        if (lower.endsWith(".cs")) {
            return "csharp";
        }
        if (lower.endsWith(".go")) {
            return "go";
        }
        if (lower.endsWith(".rb")) {
            return "ruby";
        }
        if (lower.endsWith(".rs")) {
            return "rust";
        }
        if (lower.endsWith(".kt")) {
            return "kotlin";
        }
        if (lower.endsWith(".sql")) {
            return "sql";
        }
        if (lower.endsWith(".sh")) {
            return "bash";
        }
        return "text";
    }
}
