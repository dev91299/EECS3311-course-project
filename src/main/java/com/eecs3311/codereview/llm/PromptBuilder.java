package com.eecs3311.codereview.llm;

import java.util.ArrayList;
import java.util.List;

/**
 * BUILDER PATTERN - assembles a prompt from optional parts.
 *
 * <p>A prompt in this application is built from up to eight independent pieces: a role
 * instruction, project conventions, remembered feedback, retrieved context from other files,
 * the code under review, the required output schema, worked examples and a final instruction.
 * Most call sites need only some of them, and the order matters because models weight the
 * beginning and end of a prompt most heavily.</p>
 *
 * <p>A constructor taking eight nullable strings would be unreadable and easy to misuse.
 * This builder lets each strategy name only the parts it needs, keeps the assembly order in
 * one place, and enforces the context-window guard rail in {@link #build()}.</p>
 */
public class PromptBuilder {

    private String role = "";
    private String conventions = "";
    private String memory = "";
    private String retrievedContext = "";
    private String code = "";
    private String language = "java";
    private String fileName = "";
    private String outputSchema = "";
    private final List<String> instructions = new ArrayList<>();
    private int maxCodeCharacters = 12_000;

    /** Sets the opening role instruction. */
    public PromptBuilder role(String role) {
        this.role = nullSafe(role);
        return this;
    }

    /** Adds project-specific conventions read from the .rules file. */
    public PromptBuilder conventions(String conventions) {
        this.conventions = nullSafe(conventions);
        return this;
    }

    /**
     * Adds remembered developer feedback. This is what makes the agent adapt: findings the
     * developer rejected are summarised here so the model stops reporting them.
     */
    public PromptBuilder memory(String memory) {
        this.memory = nullSafe(memory);
        return this;
    }

    /** Adds snippets retrieved from elsewhere in the repository. */
    public PromptBuilder retrievedContext(String retrievedContext) {
        this.retrievedContext = nullSafe(retrievedContext);
        return this;
    }

    /** Sets the code under review and the file it came from. */
    public PromptBuilder code(String fileName, String code) {
        this.fileName = nullSafe(fileName);
        this.code = nullSafe(code);
        return this;
    }

    /** Sets the language label used for the markdown fence. */
    public PromptBuilder language(String language) {
        this.language = nullSafe(language).isEmpty() ? "java" : language;
        return this;
    }

    /** Describes the exact output format required, usually a JSON schema. */
    public PromptBuilder outputSchema(String outputSchema) {
        this.outputSchema = nullSafe(outputSchema);
        return this;
    }

    /** Appends a closing instruction; several may be added. */
    public PromptBuilder instruction(String instruction) {
        if (instruction != null && !instruction.isBlank()) {
            instructions.add(instruction.trim());
        }
        return this;
    }

    /** Overrides how much code may be included before truncation. */
    public PromptBuilder maxCodeCharacters(int max) {
        this.maxCodeCharacters = Math.max(500, max);
        return this;
    }

    /**
     * Assembles the final user prompt.
     *
     * <p>Code longer than the configured limit is truncated with an explicit marker rather
     * than silently cut, which is the error case use case UC02 describes for an oversized
     * selection: the user is told to select a smaller snippet instead of receiving an
     * analysis of a fragment they cannot see.</p>
     */
    public String build() {
        StringBuilder sb = new StringBuilder();

        if (!conventions.isEmpty()) {
            sb.append("PROJECT CONVENTIONS\n").append(conventions).append("\n\n");
        }
        if (!memory.isEmpty()) {
            sb.append("REMEMBERED DEVELOPER FEEDBACK (do not repeat findings the team rejected)\n")
              .append(memory).append("\n\n");
        }
        if (!retrievedContext.isEmpty()) {
            sb.append("RELATED CODE RETRIEVED FROM THIS REPOSITORY\n")
              .append(retrievedContext).append("\n\n");
        }
        if (!code.isEmpty()) {
            sb.append("CODE UNDER REVIEW");
            if (!fileName.isEmpty()) {
                sb.append(" (").append(fileName).append(')');
            }
            sb.append("\n```").append(language).append('\n');
            if (code.length() > maxCodeCharacters) {
                sb.append(code, 0, maxCodeCharacters)
                  .append("\n/* TRUNCATED: the selection exceeded the context budget. ")
                  .append(code.length() - maxCodeCharacters)
                  .append(" characters were omitted. Select a smaller snippet for a complete review. */\n");
            } else {
                sb.append(code).append('\n');
            }
            sb.append("```\n\n");
        }
        if (!outputSchema.isEmpty()) {
            sb.append("REQUIRED OUTPUT FORMAT\n").append(outputSchema).append("\n\n");
        }
        for (String instruction : instructions) {
            sb.append(instruction).append('\n');
        }
        return sb.toString().trim();
    }

    /** The system message, built from the role plus a standing scope restriction. */
    public String buildSystemPrompt() {
        StringBuilder sb = new StringBuilder();
        sb.append(role.isEmpty() ? "You are an expert software code reviewer." : role);
        sb.append(" Restrict yourself to software engineering topics; if a request is unrelated "
                + "to the code or to programming, say so briefly instead of answering it.");
        return sb.toString();
    }

    /** True when the code had to be truncated, so callers can warn the user. */
    public boolean wasTruncated() {
        return code.length() > maxCodeCharacters;
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value.trim();
    }
}
