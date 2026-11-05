package com.eecs3311.codereview.llm;

import com.eecs3311.codereview.json.Json;
import com.eecs3311.codereview.model.ChatMessage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Offline fallback used only when no API key is configured.
 *
 * <p><strong>What this is and is not.</strong> It is not a store of canned answers. It is a
 * small deterministic static analyser that reads the code in the prompt and derives findings
 * from it with regular expressions and structural heuristics, then emits them in exactly the
 * JSON schema the real model is asked to produce. Its purpose is to keep the application
 * demonstrable, and the test suite runnable, on a machine with no key and no network.</p>
 *
 * <p>It is strictly weaker than the real integration: it cannot reason about intent, explain
 * a defect in context, plan a multi-step audit, or answer an open question about a codebase.
 * Both interfaces display a prominent OFFLINE MODE banner whenever this provider is active so
 * the limitation is never mistaken for the real behaviour. Every feature that is marked
 * AI-based in the report is demonstrated against a real model, not against this class.</p>
 */
public class MockProvider implements LLMProvider {

    private static final Pattern SQL_CONCAT = Pattern.compile(
            "(?i)(execute(Query|Update)?|prepareStatement|createStatement)\\s*\\([^)]*\\+"
            + "|\"[^\"]*\\b(select|insert\\s+into|update|delete\\s+from)\\b[^\"]*\"\\s*\\+"
            + "|\\+\\s*\"[^\"]*\\b(where|values|set)\\b");
    private static final Pattern HARDCODED_SECRET = Pattern.compile(
            "(?i)(password|passwd|secret|api_?key|token)\\s*=\\s*\"[^\"]{4,}\"");
    private static final Pattern EMPTY_CATCH = Pattern.compile(
            "catch\\s*\\([^)]*\\)\\s*\\{\\s*\\}");
    private static final Pattern PRINT_STACK = Pattern.compile("printStackTrace\\s*\\(");
    private static final Pattern SYSTEM_EXIT = Pattern.compile("System\\s*\\.\\s*exit\\s*\\(");
    private static final Pattern DIVISION = Pattern.compile("/\\s*[a-zA-Z_][a-zA-Z0-9_]*\\s*[;)]");
    private static final Pattern METHOD_DECL = Pattern.compile(
            "(public|private|protected|static|\\s)+[\\w<>\\[\\]]+\\s+(\\w+)\\s*\\([^)]*\\)\\s*\\{");
    private static final Pattern NESTED_LOOP = Pattern.compile(
            "for\\s*\\([^)]*\\)\\s*\\{[^{}]*for\\s*\\(", Pattern.DOTALL);
    private static final Pattern STRING_CONCAT_LOOP = Pattern.compile(
            "(for|while)\\s*\\([^)]*\\)\\s*\\{[^{}]*\\+=\\s*\"", Pattern.DOTALL);

    @Override
    public LLMResponse complete(LLMRequest request) {
        String code = extractCode(request);
        String purpose = request.getPurpose();
        String content;

        if (purpose.contains("security") || purpose.contains("issue") || purpose.contains("detect")) {
            content = Json.stringify(Map.of("findings", analyseSecurity(code)));
        } else if (purpose.contains("style") || purpose.contains("standard")) {
            content = Json.stringify(Map.of("findings", analyseStyle(code)));
        } else if (purpose.contains("performance")) {
            content = Json.stringify(Map.of("findings", analysePerformance(code)));
        } else if (purpose.contains("delta") || purpose.contains("compare")) {
            content = "Offline assessment: the deterministic comparison above is authoritative. "
                    + "No language model is configured, so no interpretation of the quality "
                    + "shift can be offered. Configure an API key to enable it.";
        } else if (purpose.contains("summary") || purpose.contains("summar")) {
            content = summarise(code);
        } else if (purpose.contains("test")) {
            content = generateTests(code);
        } else if (purpose.contains("refactor")) {
            content = suggestRefactor(code);
        } else if (purpose.contains("plan")) {
            content = producePlan();
        } else if (purpose.contains("decision")) {
            content = produceDecision(request);
        } else if (purpose.contains("synthesis")) {
            content = produceSynthesis(request);
        } else {
            content = answerChat(request, code);
        }

        int promptTokens = Math.max(1, request.approximateCharacters() / 4);
        int completionTokens = Math.max(1, content.length() / 4);
        return new LLMResponse(content, "offline-static-analyser", promptTokens,
                completionTokens, 5L, false, true);
    }

    // ------------------------------------------------------------------ analysers

    private List<Object> analyseSecurity(String code) {
        List<Object> findings = new ArrayList<>();
        String[] lines = code.split("\n", -1);

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int lineNumber = i + 1;

            if (SQL_CONCAT.matcher(line).find()) {
                findings.add(finding(lineNumber, "CRITICAL", "Security",
                        "SQL query built by string concatenation",
                        "User-controlled text is concatenated directly into a SQL statement, which "
                        + "allows an attacker to change the meaning of the query.",
                        "Use a PreparedStatement with ? placeholders and bind the values."));
            }
            if (HARDCODED_SECRET.matcher(line).find()) {
                findings.add(finding(lineNumber, "CRITICAL", "Security",
                        "Credential hard-coded in source",
                        "A secret is written as a literal in the source file, so it is exposed to "
                        + "anyone who can read the repository and cannot be rotated without a rebuild.",
                        "Read the value from an environment variable or an untracked .env file."));
            }
            if (EMPTY_CATCH.matcher(line).find()) {
                findings.add(finding(lineNumber, "HIGH", "Error handling",
                        "Exception swallowed by an empty catch block",
                        "The failure is discarded, so the program continues in an undefined state "
                        + "and the cause is invisible during debugging.",
                        "Log the exception or rethrow it wrapped in a domain-specific exception."));
            }
            if (PRINT_STACK.matcher(line).find()) {
                findings.add(finding(lineNumber, "MEDIUM", "Error handling",
                        "printStackTrace used instead of logging",
                        "Stack traces written to standard error are lost in production and may leak "
                        + "internal paths to users.",
                        "Route the failure through the application logger."));
            }
            if (SYSTEM_EXIT.matcher(line).find()) {
                findings.add(finding(lineNumber, "MEDIUM", "Robustness",
                        "System.exit called outside the entry point",
                        "Terminating the JVM from library code prevents callers from recovering and "
                        + "makes the class untestable.",
                        "Throw an exception and let the entry point decide whether to exit."));
            }
            if (line.contains("== null") && line.contains(".") && line.contains("&&")
                    && line.indexOf('.') < line.indexOf("== null")) {
                findings.add(finding(lineNumber, "HIGH", "Correctness",
                        "Possible dereference before the null check",
                        "The expression touches the reference before testing it against null.",
                        "Reorder the condition so the null test comes first."));
            }
            if (DIVISION.matcher(line).find() && !line.contains("//") && !line.trim().startsWith("*")) {
                findings.add(finding(lineNumber, "MEDIUM", "Correctness",
                        "Division without a zero guard",
                        "The divisor is a variable that is not checked against zero on this path, "
                        + "so the operation can throw ArithmeticException at run time.",
                        "Validate the divisor before dividing and report the error explicitly."));
            }
        }
        return findings;
    }

    /**
     * The offline stand-in for the judgement half of the hybrid style check.
     *
     * <p>Deliberately does <em>not</em> repeat anything {@code StandardsChecker} already
     * decides mechanically (line length, tabs, method casing, braces, imports). In the real
     * system the model is told the same thing in its prompt; here the exclusion is enforced
     * in code, so the two halves do not both report the same violation.</p>
     */
    private List<Object> analyseStyle(String code) {
        List<Object> findings = new ArrayList<>();
        String[] lines = code.split("\n", -1);

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int lineNumber = i + 1;

            if (line.matches(".*\\b(String|int|double|Object|var)\\s+(data|temp|tmp|val|value|obj|thing|stuff|result)\\b.*")) {
                findings.add(finding(lineNumber, "LOW", "Naming",
                        "Identifier does not describe what it holds",
                        "A generic name such as 'data' or 'temp' forces every reader to trace "
                        + "the assignment to find out what the variable actually contains.",
                        "Rename it after the domain concept it represents."));
            }
            if (line.matches(".*\\bint\\s+[a-z]\\s*=.*") && !line.contains("for")) {
                findings.add(finding(lineNumber, "INFO", "Naming",
                        "Single-letter variable name outside a loop",
                        "A one-character name does not convey intent at the point of use.",
                        "Give the variable a descriptive name."));
            }
            if (line.trim().startsWith("//") && line.toUpperCase().contains("TODO")) {
                findings.add(finding(lineNumber, "INFO", "Maintenance",
                        "Unresolved TODO comment",
                        "The comment marks work that was never completed.",
                        "Complete the work or track it in the issue tracker."));
            }
        }
        return findings;
    }

    private List<Object> analysePerformance(String code) {
        List<Object> findings = new ArrayList<>();
        Matcher nested = NESTED_LOOP.matcher(code);
        if (nested.find()) {
            findings.add(finding(lineOf(code, nested.start()), "MEDIUM", "Performance",
                    "Nested loop over the same data",
                    "The nested iteration gives quadratic behaviour, which degrades sharply as the "
                    + "input grows.",
                    "Index one collection into a HashMap and look values up in a single pass."));
        }
        Matcher concat = STRING_CONCAT_LOOP.matcher(code);
        if (concat.find()) {
            findings.add(finding(lineOf(code, concat.start()), "MEDIUM", "Performance",
                    "String concatenation inside a loop",
                    "Each concatenation allocates a new String, so the loop does quadratic copying.",
                    "Accumulate into a StringBuilder and convert once after the loop."));
        }
        return findings;
    }

    private String summarise(String code) {
        String[] lines = code.split("\n", -1);
        String codeOnly = com.eecs3311.codereview.util.SourceText.stripCommentsAndStrings(code);
        int blank = 0;
        int comments = 0;
        List<String> methods = new ArrayList<>();
        String className = "(not detected)";

        Matcher classMatcher = Pattern.compile("\\b(class|interface|enum|record)\\s+([A-Z]\\w*)")
                .matcher(codeOnly);
        if (classMatcher.find()) {
            className = classMatcher.group(2);
        }
        Matcher methodMatcher = METHOD_DECL.matcher(codeOnly);
        while (methodMatcher.find()) {
            String name = methodMatcher.group(2);
            if (!methods.contains(name) && !name.equals("if") && !name.equals("for")
                    && !name.equals("while") && !name.equals("switch") && !name.equals("catch")) {
                methods.add(name);
            }
        }
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                blank++;
            } else if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) {
                comments++;
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("OFFLINE STRUCTURAL SUMMARY\n\n");
        sb.append("Type: ").append(className).append('\n');
        sb.append("Size: ").append(lines.length).append(" lines (")
          .append(blank).append(" blank, ").append(comments).append(" comment)\n");
        sb.append("Methods detected (").append(methods.size()).append("): ")
          .append(methods.isEmpty() ? "none" : String.join(", ", methods)).append("\n\n");
        sb.append("This is a structural description produced without a language model. ")
          .append("Configure an API key to get a behavioural summary that explains what the code ")
          .append("does, what it depends on, and where its risks are.");
        return sb.toString();
    }

    private String generateTests(String code) {
        String className = "ClassUnderTest";
        String codeOnly = com.eecs3311.codereview.util.SourceText.stripCommentsAndStrings(code);
        Matcher classMatcher = Pattern.compile("\\b(class|record)\\s+([A-Z]\\w*)").matcher(codeOnly);
        if (classMatcher.find()) {
            className = classMatcher.group(2);
        }
        List<String> methods = new ArrayList<>();
        Matcher methodMatcher = METHOD_DECL.matcher(codeOnly);
        while (methodMatcher.find() && methods.size() < 5) {
            String name = methodMatcher.group(2);
            if (!methods.contains(name) && !name.equals("if") && !name.equals("for")) {
                methods.add(name);
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("```java\n");
        sb.append("import org.junit.jupiter.api.Test;\n");
        sb.append("import static org.junit.jupiter.api.Assertions.*;\n\n");
        sb.append("class ").append(className).append("Test {\n\n");
        if (methods.isEmpty()) {
            sb.append("    @Test\n    void placeholder() {\n")
              .append("        fail(\"No methods were detected in the selection.\");\n    }\n");
        }
        for (String method : methods) {
            sb.append("    @Test\n");
            sb.append("    void ").append(method).append("_returnsExpectedResultForTypicalInput() {\n");
            sb.append("        // Arrange\n        ").append(className)
              .append(" subject = new ").append(className).append("();\n");
            sb.append("        // Act / Assert\n");
            sb.append("        fail(\"Offline skeleton: fill in the expected behaviour of ")
              .append(method).append("\");\n");
            sb.append("    }\n\n");
            sb.append("    @Test\n");
            sb.append("    void ").append(method).append("_rejectsInvalidInput() {\n");
            sb.append("        fail(\"Offline skeleton: assert the failure path of ")
              .append(method).append("\");\n");
            sb.append("    }\n\n");
        }
        sb.append("}\n```\n\n");
        sb.append("Offline mode produced test skeletons from method signatures only. ")
          .append("With an API key configured the agent writes executable assertions derived from ")
          .append("the actual behaviour and edge cases of each method.");
        return sb.toString();
    }

    private String suggestRefactor(String code) {
        String[] lines = code.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        sb.append("```java\n");
        for (String line : lines) {
            String improved = line.replaceAll("\t", "    ");
            sb.append(improved).append('\n');
        }
        sb.append("```\n\n");
        sb.append("Offline mode normalised whitespace only. A real refactoring - extracting methods, ")
          .append("removing duplication, replacing conditionals with polymorphism - requires the ")
          .append("language model. Configure an API key to use this feature properly.");
        return sb.toString();
    }

    private String producePlan() {
        return "1. Call list_files to take an inventory of what the repository contains.\n"
             + "2. Call search_code to locate the code that handles credentials and queries.\n"
             + "3. Call analyze_file with the security strategy on each source file found.\n"
             + "4. Summarise the recorded findings in priority order.\n\n"
             + "(Offline heuristic plan: produced by a fixed policy, not by a language model.)";
    }

    private String produceDecision(LLMRequest request) {
        String observed = sectionAfter(allUserText(request), "WHAT YOU HAVE OBSERVED SO FAR");

        if (!observed.contains("Ran list_files")) {
            return decision("Inventory the repository before choosing targets.",
                    "list_files", "");
        }
        if (!observed.contains("Ran search_code")) {
            return decision("Locate the code most likely to handle credentials.",
                    "search_code", "password authentication query");
        }

        List<String> candidates = new ArrayList<>();
        Matcher fileMatcher = Pattern.compile("([\\w./\\\\-]+\\.(java|py|js|ts|cs|go|rb))")
                .matcher(observed);
        while (fileMatcher.find() && candidates.size() < 12) {
            String candidate = fileMatcher.group(1);
            if (!candidates.contains(candidate)) {
                candidates.add(candidate);
            }
        }
        for (String candidate : candidates) {
            if (!observed.contains("Ran analyze_file('" + candidate)) {
                return decision("Run the security strategy over " + candidate + ".",
                        "analyze_file", candidate + "|security");
            }
        }

        Map<String, Object> done = new LinkedHashMap<>();
        done.put("thought", "Every file found has been analysed.");
        done.put("final_answer", produceSynthesisText(observed));
        return Json.stringify(done);
    }

    private String decision(String thought, String tool, String input) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("thought", thought);
        map.put("tool", tool);
        map.put("input", input);
        return Json.stringify(map);
    }

    private String produceSynthesis(LLMRequest request) {
        return produceSynthesisText(sectionAfter(allUserText(request), "EVERYTHING YOU OBSERVED"));
    }

    private String produceSynthesisText(String observed) {
        int analysed = countOccurrences(observed, "Ran analyze_file");
        int findings = countOccurrences(observed, "[Critical]") + countOccurrences(observed, "[High]");

        StringBuilder sb = new StringBuilder();
        sb.append("OFFLINE AUDIT SUMMARY\n\n");
        sb.append("The agent ran its planning loop and invoked its tools in sequence: it took an ")
          .append("inventory of the repository, searched for credential-handling code, and then ")
          .append("analysed ").append(analysed).append(" file(s) with the security strategy.\n\n");
        sb.append("Findings recorded during this run are listed in the findings table; ")
          .append(findings).append(" of them were graded Critical or High.\n\n");
        sb.append("This summary was produced without a language model, so it describes what the ")
          .append("agent did rather than interpreting what it found. Configure an API key to get ")
          .append("a reasoned assessment, which is what this feature is built for.");
        return sb.toString();
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

    /** Returns everything after a named heading in the prompt. */
    private String sectionAfter(String text, String heading) {
        int index = text.indexOf(heading);
        return index < 0 ? text : text.substring(index + heading.length());
    }

    private String allUserText(LLMRequest request) {
        StringBuilder sb = new StringBuilder();
        for (ChatMessage message : request.getMessages()) {
            sb.append(message.getContent()).append('\n');
        }
        return sb.toString();
    }

    private String answerChat(LLMRequest request, String code) {
        String question = "";
        List<ChatMessage> messages = request.getMessages();
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i).getRole() == ChatMessage.Role.USER) {
                question = messages.get(i).getContent();
                break;
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("OFFLINE MODE - no language model is configured, so this is not a real answer.\n\n");
        sb.append("Your question was: ").append(truncate(question, 200)).append("\n\n");
        if (!code.isBlank()) {
            sb.append("Context attached: ").append(code.split("\n", -1).length)
              .append(" lines of code were retrieved and would have been sent to the model.\n\n");
        }
        sb.append("To get a real answer, put an API key in your .env file and restart. ")
          .append("See the README section 'Local configuration'.");
        return sb.toString();
    }

    // ------------------------------------------------------------------ helpers

    private Map<String, Object> finding(int line, String severity, String category,
                                        String title, String description, String suggestion) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("line", line);
        map.put("severity", severity);
        map.put("category", category);
        map.put("title", title);
        map.put("description", description);
        map.put("suggestion", suggestion);
        return map;
    }

    private int lineOf(String text, int characterIndex) {
        int line = 1;
        for (int i = 0; i < characterIndex && i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    /** Pulls the code block out of the prompt so the heuristics analyse code, not instructions. */
    private String extractCode(LLMRequest request) {
        StringBuilder all = new StringBuilder();
        for (ChatMessage message : request.getMessages()) {
            if (message.getRole() == ChatMessage.Role.USER) {
                all.append(message.getContent()).append('\n');
            }
        }
        String text = all.toString();
        int fence = text.indexOf("```");
        if (fence >= 0) {
            int bodyStart = text.indexOf('\n', fence);
            int end = text.indexOf("```", fence + 3);
            if (bodyStart > 0 && end > bodyStart) {
                return text.substring(bodyStart + 1, end);
            }
        }
        return text;
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }

    @Override
    public String name() {
        return "offline-static-analyser";
    }

    @Override
    public boolean isOffline() {
        return true;
    }
}
