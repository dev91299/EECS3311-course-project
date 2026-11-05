package com.eecs3311.codereview.agent.strategy;

import com.eecs3311.codereview.llm.PromptBuilder;
import com.eecs3311.codereview.model.Severity;

/**
 * STRATEGY PATTERN - concrete strategy for efficiency analysis.
 *
 * <p>Added during implementation. Reviewing the Stage 1 design showed that the Security and
 * Style strategies between them covered correctness and formatting but left algorithmic cost
 * unexamined, even though "suggest refactoring" (F04) frequently needs that input to propose
 * anything worthwhile. Adding it as a third strategy rather than extending
 * {@link SecurityStrategy}'s prompt is exactly the extension the Strategy pattern is meant
 * to make cheap: one new class, one registry entry, no edit to the agent.</p>
 */
public class PerformanceStrategy extends AbstractReviewStrategy {

    @Override
    public String getName() {
        return "performance";
    }

    @Override
    public String getDescription() {
        return "Finds algorithmic inefficiency, redundant work and avoidable allocation";
    }

    @Override
    protected String getRoleInstruction() {
        return "You are a performance engineer. You judge cost by how work grows with input "
             + "size, and you only raise an issue when the fix is worth the change.";
    }

    @Override
    protected String getPurpose() {
        return "performance-analysis";
    }

    @Override
    protected void decoratePrompt(PromptBuilder builder, AnalysisContext context) {
        builder.instruction(
                "Identify efficiency problems in the code above:\n"
                + "- Nested iteration over the same data where a map lookup would do.\n"
                + "- Work repeated inside a loop that could be hoisted out of it.\n"
                + "- String building by concatenation in a loop.\n"
                + "- Queries, file reads or network calls issued inside a loop.\n"
                + "- Collections that are copied, sorted or re-scanned more often than needed.\n"
                + "- A data structure whose operations do not match how it is used.\n\n"
                + "For each finding, state the current growth rate and the growth rate after the "
                + "fix, in the description. Do not report micro-optimisations with no measurable "
                + "effect, and do not report style or correctness issues here.");
    }

    /**
     * An inefficiency is not a vulnerability. Capping at HIGH keeps the findings table's
     * CRITICAL band meaningful: it should mean "do not merge this".
     */
    @Override
    protected Severity getSeverityCeiling() {
        return Severity.HIGH;
    }
}
