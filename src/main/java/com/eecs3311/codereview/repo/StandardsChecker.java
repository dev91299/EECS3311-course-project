package com.eecs3311.codereview.repo;

import com.eecs3311.codereview.model.ReviewItem;
import com.eecs3311.codereview.model.Severity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The deterministic half of the coding-standard check (feature F06).
 *
 * <p>Feature F06 is classified as hybrid: rules that can be stated exactly - maximum line
 * length, indentation character, identifier casing, file header requirements - are decided
 * here by parsing, with no model call and no ambiguity. Rules that cannot be stated exactly
 * - whether a name actually describes what it holds, whether a comment adds information -
 * are passed to the model by {@code StyleStrategy}.</p>
 *
 * <p>Splitting the work this way matters for correctness and for cost: a line-length rule
 * decided by a model would be both slower and occasionally wrong, while a naming-quality
 * rule decided by a regular expression would be useless.</p>
 *
 * <p>Rules are read from an optional {@code .rules} file in the repository root. When that
 * file is absent the defaults below are used, which is the fallback behaviour use case
 * UC02 and feature F06 both specify.</p>
 */
public class StandardsChecker {

    /** Default rule values, applied when no .rules file is present. */
    private int maxLineLength = 120;
    private boolean forbidTabs = true;
    private boolean requireFinalNewline = true;
    private int maxMethodLines = 60;
    private int maxParameters = 6;
    private boolean forbidWildcardImports = true;
    private boolean requireBracesOnConditionals = true;
    private String rulesSource = "built-in defaults (PEP8/Google-style equivalents for Java)";

    private static final Pattern METHOD_SIGNATURE = Pattern.compile(
            "^\\s*(public|protected|private)?\\s*(static\\s+)?(final\\s+)?"
            + "[\\w<>\\[\\],\\s\\.]+\\s+(\\w+)\\s*\\(([^)]*)\\)\\s*\\{\\s*$");
    private static final Pattern CONSTANT_DECL = Pattern.compile(
            "static\\s+final\\s+[\\w<>\\[\\]]+\\s+(\\w+)\\s*=");
    private static final Pattern CLASS_DECL = Pattern.compile(
            "\\b(class|interface|enum|record)\\s+(\\w+)");
    private static final Pattern WILDCARD_IMPORT = Pattern.compile("^import\\s+[\\w\\.]+\\.\\*\\s*;");
    private static final Pattern UNBRACED_IF = Pattern.compile(
            "^\\s*(if|for|while)\\s*\\([^)]*\\)\\s*[^\\s{/].*;\\s*$");

    /**
     * Parses a {@code .rules} file. Unrecognised lines are ignored so that a human-readable
     * conventions document can double as the rules file.
     *
     * <p>Recognised syntax is {@code key = value}, one per line, with {@code #} comments.
     * Keys: max_line_length, forbid_tabs, require_final_newline, max_method_lines,
     * max_parameters, forbid_wildcard_imports, require_braces.</p>
     */
    public void loadRules(String rulesText) {
        if (rulesText == null || rulesText.isBlank()) {
            return;
        }
        boolean anyRecognised = false;
        for (String raw : rulesText.split("\n")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) {
                continue;
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = line.substring(0, eq).trim().toLowerCase().replace('-', '_');
            String value = line.substring(eq + 1).trim();
            try {
                switch (key) {
                    case "max_line_length":
                        maxLineLength = Integer.parseInt(value);
                        anyRecognised = true;
                        break;
                    case "forbid_tabs":
                        forbidTabs = parseBoolean(value);
                        anyRecognised = true;
                        break;
                    case "require_final_newline":
                        requireFinalNewline = parseBoolean(value);
                        anyRecognised = true;
                        break;
                    case "max_method_lines":
                        maxMethodLines = Integer.parseInt(value);
                        anyRecognised = true;
                        break;
                    case "max_parameters":
                        maxParameters = Integer.parseInt(value);
                        anyRecognised = true;
                        break;
                    case "forbid_wildcard_imports":
                        forbidWildcardImports = parseBoolean(value);
                        anyRecognised = true;
                        break;
                    case "require_braces":
                        requireBracesOnConditionals = parseBoolean(value);
                        anyRecognised = true;
                        break;
                    default:
                        break;
                }
            } catch (NumberFormatException ignored) {
                // A malformed value keeps the default rather than failing the whole check.
            }
        }
        if (anyRecognised) {
            rulesSource = "project .rules file";
        }
    }

    private boolean parseBoolean(String value) {
        return "true".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value) || "1".equals(value);
    }

    /**
     * Applies every deterministic rule to one file.
     *
     * @param filePath path recorded on each finding
     * @param code     the file's text
     * @return findings, one per violation
     */
    public List<ReviewItem> check(String filePath, String code) {
        List<ReviewItem> findings = new ArrayList<>();
        if (code == null || code.isEmpty()) {
            return findings;
        }
        String[] lines = code.split("\n", -1);

        // Pattern matching must see code only. Comments and string literals are blanked out,
        // with positions preserved, so prose such as "This class is small" cannot be read as
        // a declaration and a URL in a string cannot be read as a comment.
        String codeOnly = com.eecs3311.codereview.util.SourceText.stripCommentsAndStrings(code);
        String[] codeLines = codeOnly.split("\n", -1);

        // Collect the type names declared in this file first. A constructor has the same name
        // as its type and is required to start with a capital, so the method-naming rule must
        // not fire on it.
        java.util.Set<String> declaredTypes = new java.util.HashSet<>();
        Matcher typeMatcher = CLASS_DECL.matcher(codeOnly);
        while (typeMatcher.find()) {
            declaredTypes.add(typeMatcher.group(2));
        }

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            // Structural rules are applied to the code-only view; formatting rules are
            // applied to the raw line, because a tab inside a comment is still a tab.
            String codeLine = i < codeLines.length ? codeLines[i] : "";
            int lineNumber = i + 1;

            if (line.length() > maxLineLength) {
                findings.add(item(filePath, lineNumber, Severity.LOW, "Formatting",
                        "Line is " + line.length() + " characters (limit " + maxLineLength + ")",
                        "The project rule set caps line length at " + maxLineLength
                                + " characters so that side-by-side diffs stay readable.",
                        "Wrap the statement across multiple lines."));
            }
            if (forbidTabs && line.contains("\t")) {
                findings.add(item(filePath, lineNumber, Severity.INFO, "Formatting",
                        "Tab character used for indentation",
                        "The rule set requires spaces; mixed indentation renders differently "
                                + "in different editors and produces noisy diffs.",
                        "Replace the tab with spaces."));
            }
            if (forbidWildcardImports && WILDCARD_IMPORT.matcher(codeLine).find()) {
                findings.add(item(filePath, lineNumber, Severity.LOW, "Imports",
                        "Wildcard import",
                        "Importing a whole package hides which types the file actually uses "
                                + "and can cause ambiguous references when a dependency adds a class.",
                        "Import the specific types this file needs."));
            }
            if (requireBracesOnConditionals && UNBRACED_IF.matcher(codeLine).find()) {
                findings.add(item(filePath, lineNumber, Severity.MEDIUM, "Control flow",
                        "Conditional body without braces",
                        "A single-statement body without braces is a known source of defects "
                                + "when a second statement is added later.",
                        "Wrap the body in braces."));
            }

            Matcher constant = CONSTANT_DECL.matcher(codeLine);
            if (constant.find()) {
                String name = constant.group(1);
                if (!name.equals(name.toUpperCase())) {
                    findings.add(item(filePath, lineNumber, Severity.LOW, "Naming",
                            "Constant not in UPPER_SNAKE_CASE: " + name,
                            "Java convention names static final fields in upper snake case so "
                                    + "constants are recognisable at the point of use.",
                            "Rename to " + toUpperSnake(name) + "."));
                }
            }

            Matcher classDecl = CLASS_DECL.matcher(codeLine);
            if (classDecl.find()) {
                String name = classDecl.group(2);
                if (!name.isEmpty() && !Character.isUpperCase(name.charAt(0))) {
                    findings.add(item(filePath, lineNumber, Severity.LOW, "Naming",
                            "Type name does not start with a capital: " + name,
                            "Java convention is UpperCamelCase for type names.",
                            "Rename to " + Character.toUpperCase(name.charAt(0))
                                    + name.substring(1) + "."));
                }
            }

            Matcher method = METHOD_SIGNATURE.matcher(codeLine);
            if (method.find()) {
                String name = method.group(4);
                String parameters = method.group(5);
                if (!name.isEmpty() && Character.isUpperCase(name.charAt(0))
                        && !declaredTypes.contains(name)) {
                    findings.add(item(filePath, lineNumber, Severity.LOW, "Naming",
                            "Method name does not start with a lower-case letter: " + name,
                            "Java convention is lowerCamelCase for methods, which distinguishes "
                                    + "a call from a constructor at a glance.",
                            "Rename to " + Character.toLowerCase(name.charAt(0))
                                    + name.substring(1) + "."));
                }
                int parameterCount = countParameters(parameters);
                if (parameterCount > maxParameters) {
                    findings.add(item(filePath, lineNumber, Severity.MEDIUM, "Design",
                            "Method takes " + parameterCount + " parameters (limit "
                                    + maxParameters + ")",
                            "A long parameter list is easy to call incorrectly, especially when "
                                    + "several parameters share a type.",
                            "Group related parameters into a small value object."));
                }
                int length = measureMethodLength(lines, i);
                if (length > maxMethodLines) {
                    findings.add(item(filePath, lineNumber, Severity.MEDIUM, "Design",
                            "Method '" + name + "' is " + length + " lines (limit "
                                    + maxMethodLines + ")",
                            "A method this long usually has more than one responsibility, which "
                                    + "makes it hard to name, test and change safely.",
                            "Extract the distinct steps into separate methods."));
                }
            }
        }

        if (requireFinalNewline && !code.endsWith("\n")) {
            findings.add(item(filePath, lines.length, Severity.INFO, "Formatting",
                    "File does not end with a newline",
                    "A missing final newline makes the last line show as changed in diffs.",
                    "Add a newline at the end of the file."));
        }
        return findings;
    }

    private int countParameters(String parameterList) {
        if (parameterList == null || parameterList.isBlank()) {
            return 0;
        }
        int count = 1;
        int depth = 0;
        for (char c : parameterList.toCharArray()) {
            if (c == '<' || c == '(' || c == '[') {
                depth++;
            } else if (c == '>' || c == ')' || c == ']') {
                depth--;
            } else if (c == ',' && depth == 0) {
                count++;
            }
        }
        return count;
    }

    /** Counts lines until the method's closing brace, by tracking brace depth. */
    private int measureMethodLength(String[] lines, int startIndex) {
        int depth = 0;
        for (int i = startIndex; i < lines.length; i++) {
            for (char c : lines[i].toCharArray()) {
                if (c == '{') {
                    depth++;
                } else if (c == '}') {
                    depth--;
                }
            }
            if (depth <= 0 && i > startIndex) {
                return i - startIndex + 1;
            }
        }
        return lines.length - startIndex;
    }

    private String toUpperSnake(String name) {
        return name.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toUpperCase();
    }

    private ReviewItem item(String filePath, int line, Severity severity, String category,
                            String title, String description, String suggestion) {
        return new ReviewItem(filePath, line, severity, title, description, suggestion,
                category, "StandardsChecker (deterministic)");
    }

    /**
     * A summary of the active rules, injected into the model prompt so the AI half of the
     * check judges against the same standard as the deterministic half.
     */
    public String describeRules() {
        Map<String, Object> rules = new LinkedHashMap<>();
        rules.put("max_line_length", maxLineLength);
        rules.put("forbid_tabs", forbidTabs);
        rules.put("max_method_lines", maxMethodLines);
        rules.put("max_parameters", maxParameters);
        rules.put("forbid_wildcard_imports", forbidWildcardImports);
        rules.put("require_braces", requireBracesOnConditionals);
        StringBuilder sb = new StringBuilder("Rule source: ").append(rulesSource).append('\n');
        for (Map.Entry<String, Object> e : rules.entrySet()) {
            sb.append("  ").append(e.getKey()).append(" = ").append(e.getValue()).append('\n');
        }
        sb.append("The rules above are already enforced mechanically. Do not report violations "
                + "of them; report only judgement-based concerns.");
        return sb.toString();
    }

    /** Where the active rules came from, shown in the UI. */
    public String getRulesSource() {
        return rulesSource;
    }
}
