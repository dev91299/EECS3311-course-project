package com.eecs3311.codereview;

import com.eecs3311.codereview.model.ReviewItem;
import com.eecs3311.codereview.model.Severity;
import com.eecs3311.codereview.repo.StandardsChecker;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the deterministic half of the hybrid coding-standard feature (F06).
 *
 * <p>These checks must be exactly reproducible, because the whole reason for splitting them
 * out of the model prompt is that they are decidable without judgement.</p>
 */
class StandardsCheckerTest {

    private boolean hasFinding(List<ReviewItem> findings, String fragment) {
        return findings.stream().anyMatch(f -> f.getTitle().toLowerCase()
                .contains(fragment.toLowerCase()));
    }

    @Test
    void flagsOverlongLines() {
        StandardsChecker checker = new StandardsChecker();
        checker.loadRules("max_line_length = 40");
        String code = "class A {\n    String s = \"" + "x".repeat(60) + "\";\n}\n";
        assertTrue(hasFinding(checker.check("A.java", code), "characters"));
    }

    @Test
    void flagsTabIndentation() {
        StandardsChecker checker = new StandardsChecker();
        assertTrue(hasFinding(checker.check("A.java", "class A {\n\tint x;\n}\n"), "tab"));
    }

    @Test
    void flagsWildcardImports() {
        StandardsChecker checker = new StandardsChecker();
        assertTrue(hasFinding(checker.check("A.java", "import java.util.*;\nclass A {}\n"),
                "wildcard"));
    }

    @Test
    void flagsMethodNamesStartingWithACapital() {
        StandardsChecker checker = new StandardsChecker();
        String code = "class A {\n    public String GetName(String id) {\n        return id;\n    }\n}\n";
        assertTrue(hasFinding(checker.check("A.java", code), "lower-case"));
    }

    @Test
    void flagsConstantsNotInUpperSnakeCase() {
        StandardsChecker checker = new StandardsChecker();
        String code = "class A {\n    private static final String dbPassword = \"x\";\n}\n";
        List<ReviewItem> findings = checker.check("A.java", code);
        assertTrue(hasFinding(findings, "UPPER_SNAKE_CASE"));
        assertTrue(findings.stream().anyMatch(f -> f.getSuggestion().contains("DB_PASSWORD")));
    }

    @Test
    void flagsTooManyParameters() {
        StandardsChecker checker = new StandardsChecker();
        checker.loadRules("max_parameters = 3");
        String code = "class A {\n    public void go(int a, int b, int c, int d, int e) {\n    }\n}\n";
        assertTrue(hasFinding(checker.check("A.java", code), "parameters"));
    }

    @Test
    void genericParametersDoNotInflateTheParameterCount() {
        StandardsChecker checker = new StandardsChecker();
        checker.loadRules("max_parameters = 2");
        String code = "class A {\n    public void go(Map<String, List<Integer>> a, int b) {\n    }\n}\n";
        assertFalse(hasFinding(checker.check("A.java", code), "parameters"),
                "commas inside generics must not be counted as separators");
    }

    @Test
    void flagsUnbracedConditionals() {
        StandardsChecker checker = new StandardsChecker();
        String code = "class A {\n    void go(int x) {\n        if (x > 0) return;\n    }\n}\n";
        assertTrue(hasFinding(checker.check("A.java", code), "braces"));
    }

    @Test
    void flagsAMissingFinalNewline() {
        StandardsChecker checker = new StandardsChecker();
        assertTrue(hasFinding(checker.check("A.java", "class A {}"), "newline"));
    }

    @Test
    void cleanCodeProducesNoFindings() {
        StandardsChecker checker = new StandardsChecker();
        String code = "package demo;\n\n"
                + "import java.util.List;\n\n"
                + "/** A well-formed class. */\n"
                + "public class Clean {\n\n"
                + "    private static final int MAX_SIZE = 10;\n\n"
                + "    public int size(List<String> values) {\n"
                + "        if (values == null) {\n"
                + "            return 0;\n"
                + "        }\n"
                + "        return Math.min(values.size(), MAX_SIZE);\n"
                + "    }\n"
                + "}\n";
        assertTrue(checker.check("Clean.java", code).isEmpty(),
                "conforming code must produce no deterministic findings");
    }

    @Test
    void rulesFileOverridesTheDefaults() {
        StandardsChecker checker = new StandardsChecker();
        assertTrue(checker.getRulesSource().contains("built-in"));
        checker.loadRules("max_line_length = 80\nforbid_tabs = false\n");
        assertEquals("project .rules file", checker.getRulesSource());
        assertFalse(hasFinding(checker.check("A.java", "class A {\n\tint x;\n}\n"), "tab"),
                "a rule turned off in the .rules file must not fire");
    }

    @Test
    void malformedRuleValuesFallBackToTheDefault() {
        StandardsChecker checker = new StandardsChecker();
        checker.loadRules("max_line_length = not-a-number\n");
        String code = "class A {\n    String s = \"" + "x".repeat(200) + "\";\n}\n";
        assertTrue(hasFinding(checker.check("A.java", code), "characters"),
                "the 120-character default still applies");
    }

    @Test
    void allFindingsAreAttributedToTheDeterministicChecker() {
        StandardsChecker checker = new StandardsChecker();
        List<ReviewItem> findings = checker.check("A.java", "import java.util.*;\nclass A {}");
        assertFalse(findings.isEmpty());
        findings.forEach(f -> assertTrue(f.getProducedBy().contains("deterministic")));
    }

    @Test
    void styleFindingsAreNeverCritical() {
        StandardsChecker checker = new StandardsChecker();
        List<ReviewItem> findings = checker.check("A.java", "import java.util.*;\nclass a {\n\tint x;\n}");
        findings.forEach(f -> assertTrue(f.getSeverity().getRank() >= Severity.MEDIUM.getRank(),
                "a formatting rule must never outrank a real defect"));
    }

    @Test
    void describeRulesTellsTheModelNotToRepeatMechanicalChecks() {
        StandardsChecker checker = new StandardsChecker();
        String description = checker.describeRules();
        assertTrue(description.contains("max_line_length"));
        assertTrue(description.toLowerCase().contains("do not report"));
    }
}
