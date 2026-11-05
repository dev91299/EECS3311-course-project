package com.eecs3311.codereview.model;

import java.time.Instant;

/**
 * One turn in the natural-language conversation with the agent (feature F10).
 *
 * <p>The role names match the OpenAI-compatible chat API that the LLM layer speaks, so a
 * list of these objects can be serialised straight into a request body. Keeping the full
 * list is what gives the assistant conversational memory across turns.</p>
 */
public class ChatMessage {

    /** Who produced the message. */
    public enum Role {
        /** Instructions that define the assistant's behaviour and constraints. */
        SYSTEM("system"),
        /** A message typed by the developer. */
        USER("user"),
        /** A message produced by the model. */
        ASSISTANT("assistant");

        private final String wireName;

        Role(String wireName) {
            this.wireName = wireName;
        }

        /** The role string the chat API expects. */
        public String getWireName() {
            return wireName;
        }
    }

    private final Role role;
    private final String content;
    private final Instant timestamp;

    public ChatMessage(Role role, String content) {
        this.role = role == null ? Role.USER : role;
        this.content = content == null ? "" : content;
        this.timestamp = Instant.now();
    }

    /** Convenience factory for a system instruction. */
    public static ChatMessage system(String content) {
        return new ChatMessage(Role.SYSTEM, content);
    }

    /** Convenience factory for a developer message. */
    public static ChatMessage user(String content) {
        return new ChatMessage(Role.USER, content);
    }

    /** Convenience factory for a model reply. */
    public static ChatMessage assistant(String content) {
        return new ChatMessage(Role.ASSISTANT, content);
    }

    public Role getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return role.getWireName() + ": " + content;
    }
}
