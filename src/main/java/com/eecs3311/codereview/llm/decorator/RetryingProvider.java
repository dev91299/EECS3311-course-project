package com.eecs3311.codereview.llm.decorator;

import com.eecs3311.codereview.llm.LLMException;
import com.eecs3311.codereview.llm.LLMProvider;
import com.eecs3311.codereview.llm.LLMRequest;
import com.eecs3311.codereview.llm.LLMResponse;

import java.util.function.Consumer;

/**
 * DECORATOR PATTERN - concrete decorator adding automatic retry with exponential backoff.
 *
 * <p>This implements the alternative flow drawn in sequence diagram SD01 and described in
 * use case UC02: on an API timeout the system retries automatically before surfacing a
 * connection warning to the user. Only failures flagged
 * {@link LLMException#isRetryable() retryable} are retried - an invalid key fails fast.</p>
 */
public class RetryingProvider extends LLMProviderDecorator {

    private final int maxAttempts;
    private final long initialBackoffMillis;
    private final Consumer<String> statusListener;
    private int totalRetries;

    /**
     * @param inner                provider to wrap
     * @param maxAttempts          total attempts including the first (minimum 1)
     * @param initialBackoffMillis delay before the second attempt; doubles each time
     * @param statusListener       receives human-readable progress such as
     *                             "Timed out, retrying (2 of 3)"; may be null
     */
    public RetryingProvider(LLMProvider inner, int maxAttempts, long initialBackoffMillis,
                            Consumer<String> statusListener) {
        super(inner);
        this.maxAttempts = Math.max(1, maxAttempts);
        this.initialBackoffMillis = Math.max(100L, initialBackoffMillis);
        this.statusListener = statusListener;
    }

    @Override
    public LLMResponse complete(LLMRequest request) throws LLMException {
        LLMException lastFailure = null;
        long backoff = initialBackoffMillis;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return inner.complete(request);
            } catch (LLMException e) {
                lastFailure = e;
                if (!e.isRetryable() || attempt == maxAttempts) {
                    break;
                }
                totalRetries++;
                report("Request failed (" + e.getUserMessage() + "). Retrying attempt "
                        + (attempt + 1) + " of " + maxAttempts + " in " + backoff + " ms.");
                sleep(backoff);
                backoff *= 2;
            }
        }
        report("All " + maxAttempts + " attempts failed.");
        throw lastFailure == null
                ? new LLMException("The request failed for an unknown reason.", false, 0)
                : lastFailure;
    }

    private void sleep(long millis) throws LLMException {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LLMException("Retry wait was interrupted.", e, false);
        }
    }

    private void report(String message) {
        if (statusListener != null) {
            statusListener.accept(message);
        }
    }

    /** How many retries this layer has performed, reported by the CLI {@code usage} command. */
    public int getTotalRetries() {
        return totalRetries;
    }

    @Override
    public String name() {
        return inner.name() + "+retry(" + maxAttempts + ")";
    }
}
