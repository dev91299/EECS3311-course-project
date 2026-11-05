package com.eecs3311.codereview.agent.tools;

import com.eecs3311.codereview.llm.LLMException;
import com.eecs3311.codereview.model.ReviewItem;

import java.util.List;

/**
 * Agent tool: run a full review strategy over one file and record the findings.
 *
 * <p>This is the tool with a side effect. The others only read; this one adds findings to
 * the session report, which is why the audit produces something the developer can triage
 * afterwards rather than only a transcript of the agent's reasoning.</p>
 */
public class AnalyzeFileTool implements AgentTool {

    private final AnalysisService analysisService;
    private final List<String> availableStrategies;

    /**
     * @param analysisService     the agent, seen through its narrow analysis interface
     * @param availableStrategies strategy names, listed in the tool description so the
     *                            planner cannot invent one that does not exist
     */
    public AnalyzeFileTool(AnalysisService analysisService, List<String> availableStrategies) {
        this.analysisService = analysisService;
        this.availableStrategies = availableStrategies;
    }

    @Override
    public String getName() {
        return "analyze_file";
    }

    @Override
    public String getDescription() {
        return "Runs a full review of one file and records any findings in the report. "
             + "This is the only tool that produces findings; reading a file does not.";
    }

    @Override
    public String getInputDescription() {
        return "'<file path>|<strategy>' where strategy is one of "
             + String.join(", ", availableStrategies)
             + ". The strategy may be omitted, in which case security is used.";
    }

    @Override
    public String execute(String input) {
        if (input == null || input.isBlank()) {
            return "ERROR: no file was given. Use '<file path>|<strategy>'.";
        }
        String[] parts = input.split("\\|");
        String filePath = parts[0].trim();
        String strategy = parts.length > 1 ? parts[1].trim().toLowerCase() : "security";

        if (filePath.isEmpty()) {
            return "ERROR: the file path part was empty.";
        }
        if (!availableStrategies.contains(strategy)) {
            return "ERROR: '" + strategy + "' is not a strategy. Available: "
                    + String.join(", ", availableStrategies) + ".";
        }

        try {
            List<ReviewItem> findings = analysisService.analyzeFile(filePath, strategy);
            if (findings.isEmpty()) {
                return "Analysed " + filePath + " with the " + strategy
                        + " strategy. No findings: this file is clean in that dimension.";
            }
            StringBuilder sb = new StringBuilder();
            sb.append("Analysed ").append(filePath).append(" with the ").append(strategy)
              .append(" strategy and recorded ").append(findings.size()).append(" finding(s):\n");
            for (ReviewItem finding : findings) {
                sb.append("  [").append(finding.getSeverity().getLabel()).append("] line ")
                  .append(finding.getLineNumber()).append(": ").append(finding.getTitle())
                  .append('\n');
            }
            return sb.toString();
        } catch (LLMException e) {
            return "ERROR: the analysis of " + filePath + " failed: " + e.getUserMessage()
                    + " Consider analysing a different file or finishing the audit.";
        }
    }
}
