package com.eecs3311.codereview.llm;

import com.eecs3311.codereview.json.Json;
import com.eecs3311.codereview.model.ChatMessage;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Talks to any service that implements the OpenAI {@code /chat/completions} contract.
 *
 * <p>One class therefore covers every provider the project supports - Groq, OpenRouter,
 * a locally hosted Ollama server, and OpenAI itself - because they differ only in base URL,
 * model name and key. Switching provider is a configuration change, not a code change.</p>
 *
 * <p>Built on {@link java.net.http.HttpClient} from the JDK, so no HTTP library is needed.
 * The API key is read from configuration at construction time and is sent only in the
 * {@code Authorization} header; it is never logged, printed or written to a report.</p>
 */
public class OpenAiCompatibleProvider implements LLMProvider {

    private final HttpClient httpClient;
    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final String providerLabel;
    private final Duration timeout;

    /**
     * @param baseUrl       root of the API, for example {@code https://api.groq.com/openai/v1}
     * @param apiKey        secret token; may be empty for a local Ollama server
     * @param model         model identifier, for example {@code openai/gpt-oss-120b}
     * @param providerLabel short name used in logs and in the status bar
     * @param timeoutSeconds per-request timeout
     */
    public OpenAiCompatibleProvider(String baseUrl, String apiKey, String model,
                                    String providerLabel, int timeoutSeconds) {
        this.baseUrl = stripTrailingSlash(baseUrl);
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
        this.providerLabel = providerLabel;
        this.timeout = Duration.ofSeconds(Math.max(5, timeoutSeconds));
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    private static String stripTrailingSlash(String url) {
        if (url == null) {
            return "";
        }
        String trimmed = url.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    @Override
    public LLMResponse complete(LLMRequest request) throws LLMException {
        String body = buildRequestBody(request);
        long started = System.currentTimeMillis();

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chat/completions"))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));

        if (!apiKey.isEmpty()) {
            builder.header("Authorization", "Bearer " + apiKey);
        }
        // Required by OpenRouter; harmless for the other providers.
        builder.header("HTTP-Referer", "https://github.com/eecs3311/ai-code-review-agent");
        builder.header("X-Title", "AI Code Review Agent");

        try {
            HttpResponse<String> response =
                    httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            long latency = System.currentTimeMillis() - started;
            int status = response.statusCode();

            if (status >= 200 && status < 300) {
                return parseSuccess(response.body(), latency);
            }
            throw new LLMException(describeFailure(status, response.body()),
                    isRetryableStatus(status), status);

        } catch (HttpTimeoutException e) {
            // This is the alternative flow drawn in sequence diagram SD01: the retrying
            // decorator catches it, re-attempts, and only then is the user shown "Retry".
            throw new LLMException("The model did not respond within " + timeout.getSeconds()
                    + " seconds.", e, true);
        } catch (IOException e) {
            throw new LLMException("Network error contacting " + providerLabel + ": "
                    + e.getMessage(), e, true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LLMException("The request was interrupted.", e, false);
        }
    }

    /** Builds the JSON body for the chat completions call. */
    private String buildRequestBody(LLMRequest request) {
        List<Object> messages = new ArrayList<>();
        for (ChatMessage message : request.getMessages()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("role", message.getRole().getWireName());
            entry.put("content", message.getContent());
            messages.add(entry);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("messages", messages);
        payload.put("temperature", request.getTemperature());
        payload.put("max_tokens", request.getMaxTokens());
        payload.put("stream", Boolean.FALSE);
        return Json.stringify(payload);
    }

    /** Extracts the reply text and token usage from a successful response. */
    private LLMResponse parseSuccess(String body, long latency) throws LLMException {
        Object root = Json.parse(body);
        String content = Json.getString(root, "", "choices", 0, "message", "content");
        if (content.isEmpty()) {
            // Some providers return the text under a different key when a filter triggers.
            String finishReason = Json.getString(root, "", "choices", 0, "finish_reason");
            throw new LLMException("The model returned an empty reply"
                    + (finishReason.isEmpty() ? "." : " (finish_reason=" + finishReason + ")."),
                    true, 0);
        }
        int promptTokens = Json.getInt(root, 0, "usage", "prompt_tokens");
        int completionTokens = Json.getInt(root, 0, "usage", "completion_tokens");
        String usedModel = Json.getString(root, model, "model");
        return new LLMResponse(content, usedModel, promptTokens, completionTokens, latency, false, false);
    }

    /** Turns an error response into a message that names the cause rather than dumping JSON. */
    private String describeFailure(int status, String body) {
        String apiMessage = Json.getString(Json.parse(safeBody(body)), "", "error", "message");
        if (apiMessage.isEmpty()) {
            apiMessage = Json.getString(Json.parse(safeBody(body)), "", "message");
        }
        String detail = apiMessage.isEmpty()
                ? truncate(body, 300)
                : apiMessage;
        return providerLabel + " returned HTTP " + status + ": " + detail;
    }

    private String safeBody(String body) {
        return body == null ? "{}" : body;
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }

    /** Timeouts, rate limits and server errors are worth another attempt. */
    private static boolean isRetryableStatus(int status) {
        return status == 408 || status == 409 || status == 429 || status >= 500;
    }

    @Override
    public String name() {
        return providerLabel + "(" + model + ")";
    }

    /** The model identifier in use; shown in the status bar and recorded in the report. */
    public String getModel() {
        return model;
    }

    /** The endpoint in use; shown by the CLI {@code config} command. */
    public String getBaseUrl() {
        return baseUrl;
    }
}
