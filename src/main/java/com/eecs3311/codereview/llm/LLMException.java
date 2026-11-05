package com.eecs3311.codereview.llm;

/**
 * Raised when a request to the language model cannot be completed.
 *
 * <p>The {@link #retryable} flag is what the retrying decorator consults: timeouts, rate
 * limits and 5xx responses are worth another attempt, while an invalid API key or a
 * malformed request is not and must be surfaced to the user immediately.</p>
 */
public class LLMException extends Exception {

    private static final long serialVersionUID = 1L;

    private final boolean retryable;
    private final int statusCode;

    public LLMException(String message, boolean retryable, int statusCode) {
        super(message);
        this.retryable = retryable;
        this.statusCode = statusCode;
    }

    public LLMException(String message, Throwable cause, boolean retryable) {
        super(message, cause);
        this.retryable = retryable;
        this.statusCode = 0;
    }

    /** True when retrying the identical request might succeed. */
    public boolean isRetryable() {
        return retryable;
    }

    /** HTTP status code when the failure came from the API, otherwise 0. */
    public int getStatusCode() {
        return statusCode;
    }

    /**
     * A message safe to show a non-technical user, with the remedy spelled out.
     * This is what the GUI error dialog and the CLI error line display.
     */
    public String getUserMessage() {
        switch (statusCode) {
            case 401:
            case 403:
                return "The API key was rejected. Check LLM_API_KEY in your .env file.";
            case 404:
                return "The model name was not found. Check LLM_MODEL in your .env file.";
            case 429:
                return "Rate limit reached. Wait a moment and try again.";
            case 500:
            case 502:
            case 503:
                return "The model provider is temporarily unavailable. Try again shortly.";
            default:
                return getMessage();
        }
    }
}
