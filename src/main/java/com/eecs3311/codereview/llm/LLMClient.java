package com.eecs3311.codereview.llm;

import com.eecs3311.codereview.app.AppConfig;
import com.eecs3311.codereview.llm.decorator.UsageStats;
import com.eecs3311.codereview.model.ChatMessage;

import java.util.List;
import java.util.function.Consumer;

/**
 * SINGLETON PATTERN - the single point of access to the language model.
 *
 * <p>Every feature that needs the model - the six review strategies, the chat assistant, the
 * autonomous planner and the cross-version comparison - goes through this one instance.
 * That gives the application one place where the API credential is held, one usage total to
 * report, and one shared response cache, and it guarantees that the per-minute throttle sees
 * every outbound call rather than one throttle per feature.</p>
 *
 * <p><strong>Implementation note.</strong> The instance is created by the initialisation-on-demand
 * holder idiom: the JVM guarantees that the nested {@code Holder} class is loaded lazily and
 * exactly once, which makes {@link #getInstance()} thread-safe without any synchronisation
 * cost on the common path. This matters because the Swing event thread and the agent's
 * background worker both call it.</p>
 *
 * <p><strong>Change from Stage 1.</strong> The Stage 1 design gave this class the fields
 * {@code apiKey} and {@code instance} and the responsibilities of credential storage,
 * transport, rate limiting and request construction. In the implementation the credential
 * lives in {@link AppConfig} (never in a field that could be serialised or printed),
 * transport lives in {@link OpenAiCompatibleProvider}, and rate limiting lives in a decorator.
 * What remains here is what the Singleton pattern is actually for: controlled single access.</p>
 */
public final class LLMClient {

    /** Lazily initialised, thread-safe holder. */
    private static final class Holder {
        private static final LLMClient INSTANCE = new LLMClient();
    }

    private final UsageStats stats = new UsageStats();
    private volatile LLMProvider provider;
    private volatile Consumer<String> statusListener;

    private LLMClient() {
        this.provider = LLMProviderFactory.create(stats, this::publishStatus);
    }

    /** Returns the one instance, creating it on first use. */
    public static LLMClient getInstance() {
        return Holder.INSTANCE;
    }

    /**
     * Registers a listener that receives progress messages such as retry notices, so the
     * GUI status bar and the CLI can show what is happening during a slow call.
     */
    public void setStatusListener(Consumer<String> listener) {
        this.statusListener = listener;
    }

    private void publishStatus(String message) {
        Consumer<String> listener = statusListener;
        if (listener != null) {
            listener.accept(message);
        } else {
            System.err.println("[llm] " + message);
        }
    }

    /**
     * Sends a single-turn prompt and returns the reply text.
     *
     * @param systemPrompt instructions defining the assistant's role
     * @param userPrompt   the request
     * @param purpose      label used for token attribution, for example "security-analysis"
     */
    public String sendPrompt(String systemPrompt, String userPrompt, String purpose)
            throws LLMException {
        LLMRequest request = new LLMRequest(
                List.of(ChatMessage.system(systemPrompt), ChatMessage.user(userPrompt)),
                AppConfig.getDouble(AppConfig.KEY_TEMPERATURE, 0.2),
                AppConfig.getInt(AppConfig.KEY_MAX_TOKENS, 2048),
                purpose);
        return provider.complete(request).getContent();
    }

    /** Sends a multi-turn conversation, used by the chat assistant and the agent loop. */
    public String sendConversation(List<ChatMessage> messages, String purpose, double temperature)
            throws LLMException {
        LLMRequest request = new LLMRequest(messages, temperature,
                AppConfig.getInt(AppConfig.KEY_MAX_TOKENS, 2048), purpose);
        return provider.complete(request).getContent();
    }

    /** Sends a request and returns the full response including token counts. */
    public LLMResponse send(LLMRequest request) throws LLMException {
        return provider.complete(request);
    }

    /** True when no API key is configured and the offline analyser is in use. */
    public boolean isOfflineMode() {
        return provider.isOffline();
    }

    /** Describes the active provider chain, for the status bar and the report. */
    public String describeProvider() {
        return provider.name();
    }

    /** Measured token usage for this session. */
    public UsageStats getUsageStats() {
        return stats;
    }

    /**
     * Rebuilds the provider chain from the current configuration. Used by the CLI
     * {@code reload} command so a newly added API key takes effect without restarting.
     */
    public synchronized void reloadConfiguration() {
        this.provider = LLMProviderFactory.create(stats, this::publishStatus);
    }

    /** Replaces the provider; used by tests to inject a stub. */
    synchronized void setProviderForTesting(LLMProvider replacement) {
        if (replacement != null) {
            this.provider = replacement;
        }
    }
}
