package com.eecs3311.codereview.agent.strategy;

import com.eecs3311.codereview.llm.PromptBuilder;
import com.eecs3311.codereview.model.Severity;

/**
 * STRATEGY PATTERN - concrete strategy for design and testability review.
 *
 * <p>Looks at structure rather than at lines: coupling, cohesion, duplication, and whether
 * the code can be tested without rewriting it. These findings are what feed the refactoring
 * suggestions (F04) and the test generation (F05), so running this strategy first usually
 * produces better output from both.</p>
 */
public class MaintainabilityStrategy extends AbstractReviewStrategy {

    @Override
    public String getName() {
        return "maintainability";
    }

    @Override
    public String getDescription() {
        return "Reviews structure, coupling, duplication and testability";
    }

    @Override
    protected String getRoleInstruction() {
        return "You are a software architect reviewing a class for long-term maintainability. "
             + "You care about responsibilities and seams, not about formatting.";
    }

    @Override
    protected String getPurpose() {
        return "maintainability-analysis";
    }

    @Override
    protected void decoratePrompt(PromptBuilder builder, AnalysisContext context) {
        builder.instruction(
                "Assess the structure of the code above:\n"
                + "- Responsibilities: does one class or method have more than one reason to change?\n"
                + "- Coupling: does this code reach through objects, depend on concrete types where "
                + "an interface would do, or know about layers it should not?\n"
                + "- Duplication: is the same logic expressed more than once?\n"
                + "- Testability: are there hard-coded dependencies, static state, or side effects "
                + "that make this impossible to test without changing it?\n"
                + "- Abstraction level: does a method mix high-level steps with low-level detail?\n\n"
                + "Name the specific design principle each finding violates, and say what the "
                + "seam would look like after the change. Keep severity at HIGH or below.");
    }

    @Override
    protected Severity getSeverityCeiling() {
        return Severity.HIGH;
    }
}
