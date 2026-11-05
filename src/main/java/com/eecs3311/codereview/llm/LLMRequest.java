package com.eecs3311.codereview.llm;

import com.eecs3311.codereview.model.ChatMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * An immutable request to a language model.
 *
 * <p>Carries the full message list rather than a single prompt string so that multi-turn
 * conversations (feature F10) and the agent's reasoning loop (feature F11) can send their
 * accumulated context in one call.</p>
 */
public class LLMRequest {

    private final List<ChatMessage> messages;
    private final double temperature;
    private final int maxTokens;
    private final String purpose;

    /**
     * @param messages    conversation turns, in order, usually starting with a system message
     * @param temperature sampling temperature; analysis uses a low value for repeatability
     * @param maxTokens   upper bound on the reply length
     * @param purpose     short label such as "security-analysis", used by the logging
     *                    decorator to attribute token spend per feature
     */
    public LLMRequest(List<ChatMessage> messages, double temperature, int maxTokens, String purpose) {
        this.messages = messages == null ? new ArrayList<>() : new ArrayList<>(messages);
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.purpose = purpose == null ? "general" : purpose;
    }

    public List<ChatMessage> getMessages() {
        return new ArrayList<>(messages);
    }

    public double getTemperature() {
        return temperature;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public String getPurpose() {
        return purpose;
    }

    /**
     * A stable key identifying this request's content, used by the caching decorator.
     * Temperature and token limit are included because they change the reply.
     */
    public String cacheKey() {
        StringBuilder sb = new StringBuilder();
        for (ChatMessage m : messages) {
            sb.append(m.getRole().getWireName()).append('\u0001').append(m.getContent()).append('\u0002');
        }
        sb.append(temperature).append('|').append(maxTokens);
        return Integer.toHexString(sb.toString().hashCode()) + "-" + sb.length();
    }

    /** Rough character count of the prompt, used for context-window guard rails. */
    public int approximateCharacters() {
        int total = 0;
        for (ChatMessage m : messages) {
            total += m.getContent().length();
        }
        return total;
    }
}
