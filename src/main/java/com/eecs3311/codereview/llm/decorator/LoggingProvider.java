package com.eecs3311.codereview.llm.decorator;

import com.eecs3311.codereview.llm.LLMException;
import com.eecs3311.codereview.llm.LLMProvider;
import com.eecs3311.codereview.llm.LLMRequest;
import com.eecs3311.codereview.llm.LLMResponse;

/**
 * DECORATOR PATTERN - concrete decorator that measures every call.
 *
 * <p>Accumulates token counts, latency and failure counts into a shared {@link UsageStats}
 * so the AI collaboration log in the Stage 2 report can quote measured figures. It logs
 * only metadata: prompts, replies and the API key never reach the log.</p>
 */
public class LoggingProvider extends LLMProviderDecorator {

    private final UsageStats stats;
    private final boolean verbose;

    /**
     * @param inner   provider to wrap
     * @param stats   shared accumulator
     * @param verbose when true each call prints a one-line trace to standard error
     */
    public LoggingProvider(LLMProvider inner, UsageStats stats, boolean verbose) {
        super(inner);
        this.stats = stats == null ? new UsageStats() : stats;
        this.verbose = verbose;
    }

    @Override
    public LLMResponse complete(LLMRequest request) throws LLMException {
        long started = System.currentTimeMillis();
        try {
            LLMResponse response = inner.complete(request);
            stats.record(request.getPurpose(), response.getPromptTokens(),
                    response.getCompletionTokens(), response.getLatencyMillis(),
                    response.isFromCache());
            if (verbose) {
                System.err.printf("[llm] %-22s %5d tokens  %5d ms  %s%n",
                        request.getPurpose(), response.getTotalTokens(),
                        response.getLatencyMillis(),
                        response.isFromCache() ? "(cache)" : response.getModel());
            }
            return response;
        } catch (LLMException e) {
            stats.recordFailure(request.getPurpose());
            if (verbose) {
                System.err.printf("[llm] %-22s FAILED after %d ms: %s%n",
                        request.getPurpose(), System.currentTimeMillis() - started, e.getMessage());
            }
            throw e;
        }
    }

    /** The accumulator this layer writes to. */
    public UsageStats getStats() {
        return stats;
    }

    @Override
    public String name() {
        return inner.name() + "+logging";
    }
}
