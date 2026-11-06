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
    private final double retryAfterSeconds;

    public LLMException(String message, boolean retryable, int statusCode) {
        this(message, retryable, statusCode, 0.0);
    }

    /**
     * @param retryAfterSeconds how long the provider asked the caller to wait, from the
     *                          {@code retry-after} header or the error text; 0 when it
     *                          said nothing
     */
    public LLMException(String message, boolean retryable, int statusCode,
                        double retryAfterSeconds) {
        super(message);
        this.retryable = retryable;
        this.statusCode = statusCode;
        this.retryAfterSeconds = Math.max(0.0, retryAfterSeconds);
    }

    public LLMException(String message, Throwable cause, boolean retryable) {
        super(message, cause);
        this.retryable = retryable;
        this.statusCode = 0;
        this.retryAfterSeconds = 0.0;
    }

    /**
     * How long the provider asked the caller to wait before retrying, in seconds.
     *
     * <p>A rate limit is the one failure where the provider knows the right wait and the
     * client does not. Guessing with exponential backoff fails every time when the real
     * answer is a minute, so this value is preferred whenever it is present.</p>
     *
     * @return the requested wait, or 0 when the provider did not specify one
     */
    public double getRetryAfterSeconds() {
        return retryAfterSeconds;
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
                if (retryAfterSeconds > 0) {
                    return "Rate limit reached. The provider asked to wait "
                            + Math.round(retryAfterSeconds) + " seconds; that wait has been "
                            + "made and it is still limiting. Free tiers cap how many tokens "
                            + "you may send per minute, so try again shortly, or reduce "
                            + "AGENT_MAX_STEPS in your .env file.";
                }
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
