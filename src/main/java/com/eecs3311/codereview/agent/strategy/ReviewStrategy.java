package com.eecs3311.codereview.agent.strategy;

import com.eecs3311.codereview.llm.LLMException;
import com.eecs3311.codereview.model.ReviewItem;

import java.util.List;

/**
 * STRATEGY PATTERN - the Strategy interface.
 *
 * <p>One interchangeable way of analysing a file. The context object, {@code ReviewAgent},
 * holds a reference to whichever strategy is currently selected and calls
 * {@link #executeStrategy} without knowing which kind it has. Selecting "Security" or
 * "Style" in either interface swaps the object, not a branch in a conditional.</p>
 *
 * <p>The design problem: issue detection (F03), coding-standard checking (F06) and the
 * performance and maintainability passes all follow the same shape - take code, produce
 * findings - but differ in what they look for, how they are prompted, how severe their
 * findings are, and whether a deterministic pre-pass applies. Encoding those differences as
 * conditionals inside the agent would concentrate every future analysis type in one growing
 * method. As separate strategy classes, adding an analysis type means adding one class and
 * registering it, with no edit to the agent.</p>
 */
public interface ReviewStrategy {

    /**
     * Analyses the code and returns the findings.
     *
     * @throws LLMException when the model could not be reached
     */
    List<ReviewItem> executeStrategy(AnalysisContext context) throws LLMException;

    /** Stable identifier used in the UI, the CLI and in {@link StrategyRegistry}. */
    String getName();

    /** One-line description shown in the strategy picker and in {@code help}. */
    String getDescription();
}
