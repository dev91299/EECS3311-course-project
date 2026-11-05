package com.eecs3311.codereview.llm;

/**
 * The single abstraction over "something that can answer a prompt".
 *
 * <p>Two kinds of class implement it:</p>
 * <ul>
 *   <li><strong>Concrete providers</strong> that actually produce text -
 *       {@link OpenAiCompatibleProvider} (Groq, OpenRouter, Ollama, OpenAI) and
 *       {@link MockProvider} (the offline fallback analyser).</li>
 *   <li><strong>Decorators</strong> in {@code com.eecs3311.codereview.llm.decorator} that add
 *       retrying, caching, rate limiting and usage logging around another provider.</li>
 * </ul>
 *
 * <p>Because decorators both implement and wrap this interface they can be stacked in any
 * order, and the rest of the application never knows how many layers are present.</p>
 */
public interface LLMProvider {

    /**
     * Sends the request and returns the model's reply.
     *
     * @throws LLMException when the request cannot be completed
     */
    LLMResponse complete(LLMRequest request) throws LLMException;

    /** A short name describing this layer, for example {@code "groq(llama-3.3-70b-versatile)"}. */
    String name();

    /** True when this provider does not reach a real model. */
    default boolean isOffline() {
        return false;
    }
}
