package com.eecs3311.codereview.llm.decorator;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Running totals of model usage for the session.
 *
 * <p>Section 5 of the Stage 2 report must state the token and money cost of the AI tools
 * used. Rather than estimating that afterwards, the application measures it: every reply
 * carries its token counts, the logging decorator accumulates them here, and both
 * interfaces can print the figure at any moment.</p>
 */
public class UsageStats {

    private int requestCount;
    private int cacheHitCount;
    private int failureCount;
    private long promptTokens;
    private long completionTokens;
    private long totalLatencyMillis;
    private final Map<String, Integer> callsByPurpose = new LinkedHashMap<>();
    private final Map<String, Long> tokensByPurpose = new LinkedHashMap<>();

    /** Records one completed call. */
    public synchronized void record(String purpose, int prompt, int completion,
                                    long latencyMillis, boolean fromCache) {
        requestCount++;
        if (fromCache) {
            cacheHitCount++;
        }
        promptTokens += prompt;
        completionTokens += completion;
        totalLatencyMillis += latencyMillis;
        callsByPurpose.merge(purpose, 1, Integer::sum);
        tokensByPurpose.merge(purpose, (long) (prompt + completion), Long::sum);
    }

    /** Records one failed call. */
    public synchronized void recordFailure(String purpose) {
        failureCount++;
        callsByPurpose.merge(purpose + " (failed)", 1, Integer::sum);
    }

    public synchronized int getRequestCount() {
        return requestCount;
    }

    public synchronized int getCacheHitCount() {
        return cacheHitCount;
    }

    public synchronized int getFailureCount() {
        return failureCount;
    }

    public synchronized long getPromptTokens() {
        return promptTokens;
    }

    public synchronized long getCompletionTokens() {
        return completionTokens;
    }

    public synchronized long getTotalTokens() {
        return promptTokens + completionTokens;
    }

    public synchronized long getAverageLatencyMillis() {
        return requestCount == 0 ? 0 : totalLatencyMillis / requestCount;
    }

    public synchronized Map<String, Integer> getCallsByPurpose() {
        return new LinkedHashMap<>(callsByPurpose);
    }

    public synchronized Map<String, Long> getTokensByPurpose() {
        return new LinkedHashMap<>(tokensByPurpose);
    }

    /**
     * Estimated cost in US dollars at the configured per-million-token rates.
     * On the Groq free tier both rates are zero, so this reports 0.00 and the report can
     * state the real figure rather than a guess.
     */
    public synchronized double estimateCostUsd(double promptRatePerMillion,
                                               double completionRatePerMillion) {
        return (promptTokens / 1_000_000.0) * promptRatePerMillion
             + (completionTokens / 1_000_000.0) * completionRatePerMillion;
    }

    /** A formatted block suitable for printing in the CLI or pasting into the report. */
    public synchronized String render() {
        StringBuilder sb = new StringBuilder();
        sb.append("MODEL USAGE THIS SESSION\n");
        sb.append("  Requests sent      : ").append(requestCount).append('\n');
        sb.append("  Served from cache  : ").append(cacheHitCount).append('\n');
        sb.append("  Failed requests    : ").append(failureCount).append('\n');
        sb.append("  Prompt tokens      : ").append(promptTokens).append('\n');
        sb.append("  Completion tokens  : ").append(completionTokens).append('\n');
        sb.append("  Total tokens       : ").append(getTotalTokens()).append('\n');
        sb.append("  Average latency    : ").append(getAverageLatencyMillis()).append(" ms\n");
        if (!callsByPurpose.isEmpty()) {
            sb.append("  Breakdown by feature:\n");
            for (Map.Entry<String, Integer> e : callsByPurpose.entrySet()) {
                sb.append("    - ").append(e.getKey()).append(": ").append(e.getValue())
                  .append(" call(s), ").append(tokensByPurpose.getOrDefault(e.getKey(), 0L))
                  .append(" tokens\n");
            }
        }
        return sb.toString();
    }

    /** Resets all counters. */
    public synchronized void reset() {
        requestCount = 0;
        cacheHitCount = 0;
        failureCount = 0;
        promptTokens = 0;
        completionTokens = 0;
        totalLatencyMillis = 0;
        callsByPurpose.clear();
        tokensByPurpose.clear();
    }
}
