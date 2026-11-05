package com.eecs3311.codereview.agent.tools;

import com.eecs3311.codereview.llm.LLMException;
import com.eecs3311.codereview.model.ReviewItem;

import java.util.List;

/**
 * The narrow view of the agent that its analysis tool is allowed to use.
 *
 * <p>Defining this interface in the tools package, and having {@code ReviewAgent} implement
 * it, keeps the dependency pointing one way: tools depend on an interface, the agent depends
 * on tools. Without it, {@link AnalyzeFileTool} would need a reference to the whole agent and
 * the two packages would depend on each other.</p>
 */
public interface AnalysisService {

    /**
     * Analyses one file with one named strategy and records the findings in the session
     * report.
     *
     * @param filePath     file to analyse
     * @param strategyName strategy name, for example {@code security}
     * @return the findings produced
     * @throws LLMException when the model could not be reached
     */
    List<ReviewItem> analyzeFile(String filePath, String strategyName) throws LLMException;
}
