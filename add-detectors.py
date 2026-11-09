"""Adds two detector families to the offline analyser: unclosed resources and
reference comparison of strings. Run from the project root."""
import pathlib

p = pathlib.Path("src/main/java/com/eecs3311/codereview/llm/MockProvider.java")
s = p.read_text(encoding="utf-8")

# --- new patterns -----------------------------------------------------------
anchor = '    private static final Pattern NESTED_LOOP = Pattern.compile('
additions = '''    private static final Pattern RESOURCE_OPENED = Pattern.compile(
            "(?i)\\\\bnew\\\\s+(BufferedReader|BufferedWriter|FileReader|FileWriter|"
            + "FileInputStream|FileOutputStream|Scanner|PrintWriter|InputStreamReader)\\\\s*\\\\(");
    private static final Pattern STRING_REFERENCE_COMPARISON = Pattern.compile(
            "[!=]=\\\\s*[a-zA-Z_][a-zA-Z0-9_]*\\\\s*[;)&|]|"
            + "\\\\b[a-zA-Z_][a-zA-Z0-9_]*(Name|Path|Text|String|Directory|Id|Key)\\\\s*[!=]=\\\\s*"
            + "[a-zA-Z_][a-zA-Z0-9_]*");

'''
assert anchor in s
s = s.replace(anchor, additions + anchor, 1)

# --- use them in the security pass ------------------------------------------
old_tail = '''            if (DIVISION.matcher(line).find() && !line.contains("//") && !line.trim().startsWith("*")) {'''
new_tail = '''            if (RESOURCE_OPENED.matcher(line).find() && !usesTryWithResources(line)) {
                findings.add(finding(lineNumber, "HIGH", "Resource management",
                        "Resource opened without a guaranteed close",
                        "The stream is created outside a try-with-resources block, so it is "
                        + "not closed when an exception is thrown between here and the close "
                        + "call. Each leaked handle stays open until the process exits.",
                        "Open it in a try-with-resources block so it closes on every path."));
            }
            if (STRING_REFERENCE_COMPARISON.matcher(line).find()
                    && looksLikeStringComparison(line)) {
                findings.add(finding(lineNumber, "HIGH", "Correctness",
                        "Strings compared by reference instead of by value",
                        "== compares whether two references point at the same object, not "
                        + "whether the text matches. It happens to work for literals the "
                        + "compiler has interned and fails for strings built at run time.",
                        "Use equals() to compare the contents."));
            }
            if (DIVISION.matcher(line).find() && !line.contains("//") && !line.trim().startsWith("*")) {'''
assert old_tail in s
s = s.replace(old_tail, new_tail, 1)

# --- helpers ----------------------------------------------------------------
helper_anchor = "    private Map<String, Object> finding(int line, String severity, String category,"
helpers = '''    /** True when the line opens the resource inside a try-with-resources header. */
    private boolean usesTryWithResources(String line) {
        return line.trim().startsWith("try") || line.contains("try (") || line.contains("try(");
    }

    /**
     * Reduces false positives on the reference-comparison check.
     *
     * <p>== is correct for numbers, for null and for enum constants, so a line is only
     * reported when neither side looks numeric and neither side is null or an enum-style
     * constant.</p>
     */
    private boolean looksLikeStringComparison(String line) {
        String stripped = line.trim();
        if (stripped.contains("null") || stripped.contains("length")
                || stripped.contains(".size()") || stripped.contains("==  -")) {
            return false;
        }
        // A comparison against an ALL_CAPS constant is almost always an enum or a number.
        if (stripped.matches(".*[!=]=\\\\s*[A-Z][A-Z_0-9]+\\\\s*[;)&|].*")) {
            return false;
        }
        // Digits on either side mean arithmetic, not text.
        return !stripped.matches(".*[!=]=\\\\s*[0-9].*") && !stripped.matches(".*[0-9]\\\\s*[!=]=.*");
    }

'''
assert helper_anchor in s
s = s.replace(helper_anchor, helpers + helper_anchor, 1)

p.write_text(s, encoding="utf-8")
print("added resource-leak and reference-comparison detectors")
