package com.eecs3311.codereview.llm.decorator;

import com.eecs3311.codereview.llm.LLMException;
import com.eecs3311.codereview.llm.LLMProvider;
import com.eecs3311.codereview.llm.LLMRequest;
import com.eecs3311.codereview.llm.LLMResponse;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * DECORATOR PATTERN - concrete decorator adding an in-memory response cache.
 *
 * <p>Re-analysing an unchanged file is common: the developer re-runs the analysis after
 * triaging findings, the agent re-reads a file it has already seen, and the comparison
 * feature analyses the same unchanged files on both sides of a diff. Serving those from the
 * cache removes the latency and, on a metered key, the cost.</p>
 *
 * <p>Eviction is least-recently-used with a bounded capacity, implemented with an
 * access-ordered {@link LinkedHashMap}, so a long session cannot exhaust memory.</p>
 */
public class CachingProvider extends LLMProviderDecorator {

    private final Map<String, LLMResponse> cache;
    private final boolean enabled;
    private int hits;
    private int misses;

    /**
     * @param inner    provider to wrap
     * @param capacity maximum number of cached replies
     * @param enabled  when false the decorator delegates without caching, which is how the
     *                 {@code LLM_CACHE_ENABLED=false} setting is honoured
     */
    public CachingProvider(LLMProvider inner, int capacity, boolean enabled) {
        super(inner);
        this.enabled = enabled;
        final int max = Math.max(1, capacity);
        this.cache = new LinkedHashMap<String, LLMResponse>(16, 0.75f, true) {
            private static final long serialVersionUID = 1L;

            @Override
            protected boolean removeEldestEntry(Map.Entry<String, LLMResponse> eldest) {
                return size() > max;
            }
        };
    }

    @Override
    public LLMResponse complete(LLMRequest request) throws LLMException {
        if (!enabled) {
            return inner.complete(request);
        }
        String key = request.cacheKey();
        synchronized (cache) {
            LLMResponse cached = cache.get(key);
            if (cached != null) {
                hits++;
                return cached.asCached();
            }
        }
        misses++;
        LLMResponse fresh = inner.complete(request);
        synchronized (cache) {
            cache.put(key, fresh);
        }
        return fresh;
    }

    /** Number of calls answered without reaching the network. */
    public int getHits() {
        return hits;
    }

    /** Number of calls that had to be sent upstream. */
    public int getMisses() {
        return misses;
    }

    /** Empties the cache; the CLI exposes this so a demo can show a cold call again. */
    public void clear() {
        synchronized (cache) {
            cache.clear();
        }
        hits = 0;
        misses = 0;
    }

    @Override
    public String name() {
        return inner.name() + "+cache";
    }
}
