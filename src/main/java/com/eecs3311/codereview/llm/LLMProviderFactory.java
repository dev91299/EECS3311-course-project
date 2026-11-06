package com.eecs3311.codereview.llm;

import com.eecs3311.codereview.app.AppConfig;
import com.eecs3311.codereview.llm.decorator.CachingProvider;
import com.eecs3311.codereview.llm.decorator.LoggingProvider;
import com.eecs3311.codereview.llm.decorator.RateLimitingProvider;
import com.eecs3311.codereview.llm.decorator.RetryingProvider;
import com.eecs3311.codereview.llm.decorator.UsageStats;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * FACTORY METHOD PATTERN - the creator.
 *
 * <p>Decides which concrete {@link LLMProvider} to build from configuration, and assembles
 * the decorator chain around it. Client code asks for "a provider" and receives a fully
 * configured one; it never names a concrete class, so adding a new backend is a change in
 * this one file.</p>
 *
 * <p>Known providers and their defaults:</p>
 * <table border="1">
 *   <caption>Supported backends</caption>
 *   <tr><th>LLM_PROVIDER</th><th>Default base URL</th><th>Default model</th></tr>
 *   <tr><td>groq</td><td>https://api.groq.com/openai/v1</td><td>openai/gpt-oss-120b</td></tr>
 *   <tr><td>openrouter</td><td>https://openrouter.ai/api/v1</td><td>google/gemma-4-31b-it:free</td></tr>
 *   <tr><td>ollama</td><td>http://localhost:11434/v1</td><td>llama3.1</td></tr>
 *   <tr><td>openai</td><td>https://api.openai.com/v1</td><td>gpt-4o-mini</td></tr>
 *   <tr><td>mock</td><td>n/a</td><td>offline static analyser</td></tr>
 * </table>
 */
public final class LLMProviderFactory {

    private static final Map<String, String[]> PRESETS = new LinkedHashMap<>();

    static {
        // provider key -> { base URL, default model, display label }
        PRESETS.put("groq", new String[]{
                "https://api.groq.com/openai/v1", "openai/gpt-oss-120b", "Groq"});
        PRESETS.put("openrouter", new String[]{
                "https://openrouter.ai/api/v1", "google/gemma-4-31b-it:free", "OpenRouter"});
        PRESETS.put("ollama", new String[]{
                "http://localhost:11434/v1", "llama3.1", "Ollama"});
        PRESETS.put("openai", new String[]{
                "https://api.openai.com/v1", "gpt-4o-mini", "OpenAI"});
    }

    private LLMProviderFactory() {
    }

    /**
     * FACTORY METHOD: builds the provider described by the current configuration, wrapped
     * in the decorator chain.
     *
     * <p>Chain order, outermost first: logging, caching, rate limiting, retrying, transport.
     * Logging is outermost so cache hits are counted as requests served. Caching sits above
     * rate limiting so a cached answer is not delayed by the throttle. Retrying is innermost
     * so a retried call is counted once by the logger rather than once per attempt.</p>
     *
     * @param stats          accumulator the logging layer writes to
     * @param statusListener receives retry progress messages for display; may be null
     */
    public static LLMProvider create(UsageStats stats, Consumer<String> statusListener) {
        LLMProvider transport = createTransport();

        LLMProvider chain = new RetryingProvider(
                transport,
                AppConfig.getInt(AppConfig.KEY_MAX_RETRIES, 3),
                800L,
                statusListener);

        if (!transport.isOffline()) {
            // Groq's free tier allows 30 requests and 8,000 tokens a minute. The token
            // ceiling is the binding one for this application, because a single file
            // analysis carries the code plus its context. Both are configurable, so a
            // paid tier can raise them without a code change.
            chain = new RateLimitingProvider(chain,
                    AppConfig.getInt("LLM_REQUESTS_PER_MINUTE", 28),
                    AppConfig.getInt("LLM_TOKENS_PER_MINUTE", 8000),
                    statusListener);
        }

        chain = new CachingProvider(chain, 128,
                AppConfig.getBoolean(AppConfig.KEY_CACHE_ENABLED, true));

        chain = new LoggingProvider(chain, stats,
                AppConfig.getBoolean("LLM_VERBOSE", false));

        return chain;
    }

    /** Creates the undecorated transport layer. */
    private static LLMProvider createTransport() {
        String providerKey = AppConfig.get(AppConfig.KEY_PROVIDER, "groq").toLowerCase().trim();

        if ("mock".equals(providerKey) || "offline".equals(providerKey)) {
            return new MockProvider();
        }

        boolean needsKey = !"ollama".equals(providerKey);
        if (needsKey && !AppConfig.hasApiKey()) {
            // No key: fall back rather than failing at startup, so the GUI and CLI still open.
            return new MockProvider();
        }

        String[] preset = PRESETS.getOrDefault(providerKey,
                new String[]{"", "", providerKey.isEmpty() ? "custom" : providerKey});

        String baseUrl = AppConfig.get(AppConfig.KEY_BASE_URL, preset[0]);
        String model = AppConfig.get(AppConfig.KEY_MODEL, preset[1]);
        String label = preset[2];

        if (baseUrl == null || baseUrl.isBlank()) {
            System.err.println("[llm] LLM_BASE_URL is not set for provider '" + providerKey
                    + "'. Falling back to offline mode.");
            return new MockProvider();
        }

        return new OpenAiCompatibleProvider(
                baseUrl,
                AppConfig.get(AppConfig.KEY_API_KEY, ""),
                model,
                label,
                AppConfig.getInt(AppConfig.KEY_TIMEOUT, 60));
    }

    /** The provider keys this factory recognises, for the CLI {@code config} command. */
    public static Map<String, String[]> getPresets() {
        return new LinkedHashMap<>(PRESETS);
    }
}
