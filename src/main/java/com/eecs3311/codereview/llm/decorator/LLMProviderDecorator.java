package com.eecs3311.codereview.llm.decorator;

import com.eecs3311.codereview.llm.LLMProvider;

/**
 * DECORATOR PATTERN - abstract Decorator role.
 *
 * <p>Holds a reference to the wrapped {@link LLMProvider} and implements the same interface,
 * so a decorated provider is indistinguishable from a bare one at every call site. Concrete
 * decorators override {@code complete} to add behaviour before or after delegating.</p>
 *
 * <p>The design problem this solves: retrying, caching, rate limiting and usage accounting
 * are four independent concerns that all apply to every model call. Putting them inside
 * {@link com.eecs3311.codereview.llm.OpenAiCompatibleProvider} would have produced one class
 * with four reasons to change, and would have duplicated all four in the offline provider.
 * As decorators each concern is written once, tested in isolation, and composed at run time
 * in {@link com.eecs3311.codereview.llm.LLMProviderFactory}.</p>
 */
public abstract class LLMProviderDecorator implements LLMProvider {

    /** The next layer in the chain; never null. */
    protected final LLMProvider inner;

    protected LLMProviderDecorator(LLMProvider inner) {
        if (inner == null) {
            throw new IllegalArgumentException("A decorator must wrap a provider");
        }
        this.inner = inner;
    }

    /** The wrapped provider, exposed so the factory can describe the whole chain. */
    public LLMProvider getInner() {
        return inner;
    }

    @Override
    public String name() {
        return inner.name();
    }

    @Override
    public boolean isOffline() {
        return inner.isOffline();
    }
}
