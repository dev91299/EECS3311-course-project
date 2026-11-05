package com.eecs3311.codereview.agent.strategy;

import com.eecs3311.codereview.llm.PromptBuilder;
import com.eecs3311.codereview.model.ReviewItem;
import com.eecs3311.codereview.model.Severity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * STRATEGY PATTERN - concrete strategy for defect and vulnerability detection (feature F03).
 *
 * <p>Looks for problems that change what the program does or expose it to attack: injection,
 * unvalidated input, credential handling, resource leaks, concurrency hazards, null
 * dereferences, and incorrect error handling. Deliberately silent about formatting, which is
 * {@link StyleStrategy}'s job - overlapping strategies would report the same line twice.</p>
 */
public class SecurityStrategy extends AbstractReviewStrategy {

    @Override
    public String getName() {
        return "security";
    }

    @Override
    public String getDescription() {
        return "Detects bugs, vulnerabilities and unsafe error handling, with severity grading";
    }

    @Override
    protected String getRoleInstruction() {
        return "You are a senior application security engineer reviewing code before it is "
             + "merged. You report only defects that have a concrete consequence, and you are "
             + "precise about which line causes the problem.";
    }

    @Override
    protected String getPurpose() {
        return "security-analysis";
    }

    @Override
    protected void decoratePrompt(PromptBuilder builder, AnalysisContext context) {
        builder.instruction(
                "Review the code above for defects in these categories, in priority order:\n"
                + "1. Injection and unvalidated input reaching a sink (SQL, shell, path, HTML).\n"
                + "2. Hard-coded or logged credentials and other secret handling mistakes.\n"
                + "3. Incorrect error handling: swallowed exceptions, failures left unreported, "
                + "operations that continue after an error.\n"
                + "4. Null dereferences, off-by-one errors, unchecked division, and other "
                + "correctness faults that produce wrong results or crashes.\n"
                + "5. Resource leaks: streams, connections and locks that are not released on "
                + "every path.\n"
                + "6. Concurrency hazards: shared mutable state without synchronisation, "
                + "check-then-act races.\n\n"
                + "Rules you must follow:\n"
                + "- Report formatting, naming and comment style NOWHERE; a separate pass covers them.\n"
                + "- Do not report a problem you cannot point at a specific line for.\n"
                + "- Reserve CRITICAL for issues that allow data loss, data exposure or remote "
                + "compromise. Reserve HIGH for defects that produce wrong behaviour in normal use.\n"
                + "- If the code is sound, return an empty findings array rather than inventing "
                + "something to say.");
    }

    /**
     * Security findings are ordered by severity so that the most serious appear at the top of
     * the findings table without the view needing to know anything about strategies.
     */
    @Override
    protected List<ReviewItem> postProcess(List<ReviewItem> findings, AnalysisContext context) {
        List<ReviewItem> sorted = new ArrayList<>(findings);
        sorted.sort(Comparator.comparingInt(item -> item.getSeverity().getRank()));
        return sorted;
    }

    @Override
    protected Severity getSeverityCeiling() {
        return Severity.CRITICAL;
    }
}
