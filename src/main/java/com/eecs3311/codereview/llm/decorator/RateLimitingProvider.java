package com.eecs3311.codereview.llm.decorator;

import com.eecs3311.codereview.llm.LLMException;
import com.eecs3311.codereview.llm.LLMProvider;
import com.eecs3311.codereview.llm.LLMRequest;
import com.eecs3311.codereview.llm.LLMResponse;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;

/**
 * DECORATOR PATTERN - concrete decorator enforcing the provider's rate limits locally.
 *
 * <p>Free API tiers cap two separate things, and both have to be respected:</p>
 * <ul>
 *   <li><strong>Requests per minute.</strong> Easy to stay under by spacing calls.</li>
 *   <li><strong>Tokens per minute.</strong> The one that actually bites. Groq's free tier
 *       allows 8,000 tokens a minute, and a single file analysis - the code, the project
 *       conventions, the recalled decisions and the retrieved context - can be two to four
 *       thousand. Three analyses in quick succession exhaust the budget even though only
 *       three requests were made.</li>
 * </ul>
 *
 * <p>The autonomous audit is what exposes this: it issues a decision call and an analysis
 * call per step, so it hits the token ceiling several steps in and the run dies partway
 * through. This decorator therefore tracks a rolling sixty-second window of token spend and
 * waits for room before sending, rather than letting the request fail and relying on the
 * retry layer to recover.</p>
 *
 * <p>Spend is estimated from prompt length before the call, then corrected with the real
 * figure once the reply arrives, so the window reflects what was actually charged.</p>
 */
public class RateLimitingProvider extends LLMProviderDecorator {

    /** One entry in the rolling window: when a call was made, and what it cost. */
    private static final class Spend {
        private final long atMillis;
        private int tokens;

        private Spend(long atMillis, int tokens) {
            this.atMillis = atMillis;
            this.tokens = tokens;
        }
    }

    private static final long WINDOW_MILLIS = 60_000L;

    /** Keep a margin below the published ceiling, since estimates are approximate. */
    private static final double SAFETY_FACTOR = 0.85;

    private final long minimumIntervalMillis;
    private final int tokensPerMinute;
    private final Consumer<String> statusListener;
    private final Deque<Spend> window = new ArrayDeque<>();
    private final Object lock = new Object();

    private long lastRequestAt;
    private long totalWaitedMillis;

    /**
     * @param inner             provider to wrap
     * @param requestsPerMinute permitted request rate
     * @param tokensPerMinute   permitted token rate; 0 disables token-aware throttling
     * @param statusListener    receives waiting notices for display; may be null
     */
    public RateLimitingProvider(LLMProvider inner, int requestsPerMinute, int tokensPerMinute,
                                Consumer<String> statusListener) {
        super(inner);
        this.minimumIntervalMillis = 60_000L / Math.max(1, requestsPerMinute);
        this.tokensPerMinute = Math.max(0, tokensPerMinute);
        this.statusListener = statusListener;
    }

    @Override
    public LLMResponse complete(LLMRequest request) throws LLMException {
        // A prompt of n characters is roughly n/4 tokens; the reply is bounded by maxTokens.
        int estimate = (request.approximateCharacters() / 4) + request.getMaxTokens() / 4;

        waitForRequestSlot();
        Spend entry = waitForTokenBudget(estimate);

        LLMResponse response = inner.complete(request);

        // Replace the estimate with what the provider actually charged.
        if (entry != null && response.getTotalTokens() > 0) {
            synchronized (lock) {
                entry.tokens = response.getTotalTokens();
            }
        }
        return response;
    }

    /** Spaces calls so the requests-per-minute ceiling is not crossed. */
    private void waitForRequestSlot() throws LLMException {
        long wait;
        synchronized (lock) {
            long elapsed = System.currentTimeMillis() - lastRequestAt;
            wait = (lastRequestAt > 0 && elapsed < minimumIntervalMillis)
                    ? minimumIntervalMillis - elapsed : 0L;
        }
        if (wait > 0) {
            sleep(wait);
        }
        synchronized (lock) {
            lastRequestAt = System.currentTimeMillis();
        }
    }

    /**
     * Waits until the rolling window has room for a call of the estimated size, then
     * records it.
     *
     * @return the window entry, so the caller can correct it with the real token count
     */
    private Spend waitForTokenBudget(int estimatedTokens) throws LLMException {
        if (tokensPerMinute <= 0) {
            return null;
        }
        int budget = (int) (tokensPerMinute * SAFETY_FACTOR);

        while (true) {
            long waitFor;
            synchronized (lock) {
                long now = System.currentTimeMillis();
                while (!window.isEmpty() && now - window.peekFirst().atMillis >= WINDOW_MILLIS) {
                    window.removeFirst();
                }
                int spent = window.stream().mapToInt(s -> s.tokens).sum();

                if (spent + estimatedTokens <= budget || window.isEmpty()) {
                    Spend entry = new Spend(now, estimatedTokens);
                    window.addLast(entry);
                    return entry;
                }
                // Wait until the oldest entry ages out of the window and frees its share.
                waitFor = WINDOW_MILLIS - (now - window.peekFirst().atMillis) + 200L;
            }

            report("Staying inside the free tier's token limit - waiting "
                    + Math.round(waitFor / 1000.0) + "s before the next request.");
            sleep(waitFor);
        }
    }

    private void sleep(long millis) throws LLMException {
        if (millis <= 0) {
            return;
        }
        synchronized (lock) {
            totalWaitedMillis += millis;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LLMException("The rate-limit wait was interrupted.", e, false);
        }
    }

    private void report(String message) {
        if (statusListener != null) {
            statusListener.accept(message);
        }
    }

    /** Total time this layer has spent throttling, shown by the CLI {@code usage} command. */
    public long getTotalWaitedMillis() {
        synchronized (lock) {
            return totalWaitedMillis;
        }
    }

    /** Tokens charged inside the current rolling window. */
    public int getTokensInWindow() {
        synchronized (lock) {
            long now = System.currentTimeMillis();
            return window.stream()
                    .filter(s -> now - s.atMillis < WINDOW_MILLIS)
                    .mapToInt(s -> s.tokens)
                    .sum();
        }
    }

    @Override
    public String name() {
        return inner.name() + "+ratelimit";
    }
}
