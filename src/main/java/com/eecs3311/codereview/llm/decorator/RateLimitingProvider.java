package com.eecs3311.codereview.llm.decorator;

import com.eecs3311.codereview.llm.LLMException;
import com.eecs3311.codereview.llm.LLMProvider;
import com.eecs3311.codereview.llm.LLMRequest;
import com.eecs3311.codereview.llm.LLMResponse;

/**
 * DECORATOR PATTERN - concrete decorator enforcing a minimum gap between requests.
 *
 * <p>Free API tiers cap requests per minute. The autonomous audit (feature F11) can fire
 * many calls in quick succession as it works through a repository, which would otherwise
 * trip the provider's limiter and turn a successful audit into a wall of 429 errors.
 * Spacing the calls locally keeps the run inside the quota.</p>
 *
 * <p>This is the concern the Stage 1 report attributed to the Singleton pattern
 * ("enforcing rate limiting"). Moving it into its own decorator leaves the Singleton
 * responsible only for being the single point of access.</p>
 */
public class RateLimitingProvider extends LLMProviderDecorator {

    private final long minimumIntervalMillis;
    private final Object lock = new Object();
    private long lastRequestAt;
    private long totalWaitedMillis;

    /**
     * @param inner              provider to wrap
     * @param requestsPerMinute  permitted request rate; 30 matches the Groq free tier
     */
    public RateLimitingProvider(LLMProvider inner, int requestsPerMinute) {
        super(inner);
        int safeRate = Math.max(1, requestsPerMinute);
        this.minimumIntervalMillis = 60_000L / safeRate;
    }

    @Override
    public LLMResponse complete(LLMRequest request) throws LLMException {
        synchronized (lock) {
            long now = System.currentTimeMillis();
            long elapsed = now - lastRequestAt;
            if (lastRequestAt > 0 && elapsed < minimumIntervalMillis) {
                long wait = minimumIntervalMillis - elapsed;
                totalWaitedMillis += wait;
                try {
                    Thread.sleep(wait);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new LLMException("Rate-limit wait was interrupted.", e, false);
                }
            }
            lastRequestAt = System.currentTimeMillis();
        }
        return inner.complete(request);
    }

    /** Total time this layer has spent throttling, shown by the CLI {@code usage} command. */
    public long getTotalWaitedMillis() {
        return totalWaitedMillis;
    }

    @Override
    public String name() {
        return inner.name() + "+ratelimit";
    }
}
