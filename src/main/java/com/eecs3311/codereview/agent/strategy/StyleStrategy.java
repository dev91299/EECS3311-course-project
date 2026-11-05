package com.eecs3311.codereview.agent.strategy;

import com.eecs3311.codereview.llm.PromptBuilder;
import com.eecs3311.codereview.model.ReviewItem;
import com.eecs3311.codereview.model.Severity;
import com.eecs3311.codereview.repo.StandardsChecker;

import java.util.ArrayList;
import java.util.List;

/**
 * STRATEGY PATTERN - concrete strategy for coding-standard checking (feature F06).
 *
 * <p>This is the hybrid strategy. It overrides the Template Method's deterministic hook to
 * run {@link StandardsChecker} first, which decides every rule that can be decided exactly,
 * then prompts the model only about the judgement calls the rule engine cannot make:
 * whether a name describes what it holds, whether a comment is worth its line, whether a
 * class has taken on more than one responsibility.</p>
 *
 * <p>The active rules are injected into the prompt together with an instruction not to
 * repeat them, so the two halves do not both report the same violation.</p>
 */
public class StyleStrategy extends AbstractReviewStrategy {

    private final StandardsChecker checker;

    /**
     * @param checker the rule engine, already loaded with the project's .rules file
     */
    public StyleStrategy(StandardsChecker checker) {
        this.checker = checker == null ? new StandardsChecker() : checker;
    }

    @Override
    public String getName() {
        return "style";
    }

    @Override
    public String getDescription() {
        return "Checks the project coding standard: exact rules mechanically, judgement calls by AI";
    }

    /** TEMPLATE METHOD hook: the deterministic half of the hybrid feature. */
    @Override
    protected List<ReviewItem> runDeterministicPass(AnalysisContext context) {
        List<ReviewItem> findings = new ArrayList<>();
        findings.addAll(checker.check(context.getFilePath(), context.getCode()));
        return findings;
    }

    @Override
    protected String getRoleInstruction() {
        return "You are a meticulous code reviewer enforcing a team's coding standard. "
             + "A rule engine has already checked everything that can be checked mechanically, "
             + "so you comment only on what requires judgement.";
    }

    @Override
    protected String getPurpose() {
        return "style-analysis";
    }

    @Override
    protected void decoratePrompt(PromptBuilder builder, AnalysisContext context) {
        builder.conventions(checker.describeRules()
                + (context.getConventions().isEmpty() ? ""
                   : "\n\nAdditional written conventions:\n" + context.getConventions()));
        builder.instruction(
                "Report only standard violations that require judgement:\n"
                + "- Identifiers that are syntactically fine but do not describe what they hold "
                + "or do (for example 'data', 'temp', 'process', 'flag').\n"
                + "- Comments that restate the code, are stale, or are missing where the intent "
                + "is genuinely non-obvious.\n"
                + "- Inconsistency with the rest of this file: a different error-handling style, "
                + "a different ordering convention, a different abstraction level in one method.\n"
                + "- A class or method that has taken on a second responsibility.\n"
                + "- Dead code, unused declarations and unreachable branches.\n\n"
                + "Do NOT report line length, tabs, brace placement, wildcard imports, constant "
                + "casing or method length: all of those are already enforced mechanically and "
                + "repeating them produces duplicate findings.\n"
                + "Keep severity at MEDIUM or below; a style concern is never CRITICAL.");
    }

    /** Style concerns never outrank real defects in the findings table. */
    @Override
    protected Severity getSeverityCeiling() {
        return Severity.MEDIUM;
    }
}
