package com.eecs3311.codereview.llm;

/**
 * The model's reply, together with the metadata the application needs for cost accounting
 * and for telling the user where an answer came from.
 */
public class LLMResponse {

    private final String content;
    private final String model;
    private final int promptTokens;
    private final int completionTokens;
    private final long latencyMillis;
    private final boolean fromCache;
    private final boolean offline;

    public LLMResponse(String content, String model, int promptTokens, int completionTokens,
                       long latencyMillis, boolean fromCache, boolean offline) {
        this.content = content == null ? "" : content;
        this.model = model == null ? "unknown" : model;
        this.promptTokens = Math.max(0, promptTokens);
        this.completionTokens = Math.max(0, completionTokens);
        this.latencyMillis = Math.max(0, latencyMillis);
        this.fromCache = fromCache;
        this.offline = offline;
    }

    /** The generated text. */
    public String getContent() {
        return content;
    }

    /** Identifier of the model that produced the reply. */
    public String getModel() {
        return model;
    }

    public int getPromptTokens() {
        return promptTokens;
    }

    public int getCompletionTokens() {
        return completionTokens;
    }

    public int getTotalTokens() {
        return promptTokens + completionTokens;
    }

    public long getLatencyMillis() {
        return latencyMillis;
    }

    /** True when the caching decorator served this reply without a network call. */
    public boolean isFromCache() {
        return fromCache;
    }

    /** True when the reply came from the offline fallback analyser rather than a real model. */
    public boolean isOffline() {
        return offline;
    }

    /** Returns a copy flagged as having come from the cache. */
    public LLMResponse asCached() {
        return new LLMResponse(content, model, promptTokens, completionTokens, 0L, true, offline);
    }

    @Override
    public String toString() {
        return "LLMResponse{model=" + model + ", tokens=" + getTotalTokens()
                + ", latency=" + latencyMillis + "ms, cached=" + fromCache + ", offline=" + offline + '}';
    }
}
