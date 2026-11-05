package com.eecs3311.codereview.app;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads application configuration from local, untracked files.
 *
 * <p><strong>API key protection.</strong> No credential is ever hard-coded in source.
 * Keys are read, in order of precedence, from:</p>
 * <ol>
 *   <li>a JVM system property (e.g. {@code -DLLM_API_KEY=...}),</li>
 *   <li>an operating-system environment variable,</li>
 *   <li>a local {@code .env} file,</li>
 *   <li>a local {@code config.local.json} file.</li>
 * </ol>
 *
 * <p>Both {@code .env} and {@code config.local.json} are listed in {@code .gitignore}.
 * The repository contains only {@code .env.example}, which holds placeholders.</p>
 */
public final class AppConfig {

    /** Recognised configuration keys. */
    public static final String KEY_API_KEY = "LLM_API_KEY";
    public static final String KEY_PROVIDER = "LLM_PROVIDER";
    public static final String KEY_BASE_URL = "LLM_BASE_URL";
    public static final String KEY_MODEL = "LLM_MODEL";
    public static final String KEY_TIMEOUT = "LLM_TIMEOUT_SECONDS";
    public static final String KEY_MAX_RETRIES = "LLM_MAX_RETRIES";
    public static final String KEY_TEMPERATURE = "LLM_TEMPERATURE";
    public static final String KEY_MAX_TOKENS = "LLM_MAX_TOKENS";
    public static final String KEY_AGENT_MAX_STEPS = "AGENT_MAX_STEPS";
    public static final String KEY_CACHE_ENABLED = "LLM_CACHE_ENABLED";

    private static final Map<String, String> VALUES = new LinkedHashMap<>();
    private static boolean loaded = false;

    private AppConfig() {
    }

    /** Loads configuration files once, on first access. */
    private static synchronized void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        loadEnvFile(Paths.get(".env"));
        loadEnvFile(Paths.get(System.getProperty("user.home"), ".ai-code-review-agent.env"));
        loadJsonFile(Paths.get("config.local.json"));
    }

    private static void loadEnvFile(Path path) {
        if (!Files.isReadable(path)) {
            return;
        }
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            for (String raw : lines) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                if (line.startsWith("export ")) {
                    line = line.substring(7).trim();
                }
                int eq = line.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                String key = line.substring(0, eq).trim();
                String value = line.substring(eq + 1).trim();
                value = stripQuotes(value);
                VALUES.putIfAbsent(key, value);
            }
        } catch (IOException e) {
            System.err.println("[config] Could not read " + path + ": " + e.getMessage());
        }
    }

    private static void loadJsonFile(Path path) {
        if (!Files.isReadable(path)) {
            return;
        }
        try {
            String text = Files.readString(path, StandardCharsets.UTF_8);
            Map<String, Object> map = com.eecs3311.codereview.json.Json.parseObject(text);
            for (Map.Entry<String, Object> e : map.entrySet()) {
                if (e.getValue() != null) {
                    VALUES.putIfAbsent(e.getKey(), String.valueOf(e.getValue()));
                }
            }
        } catch (IOException e) {
            System.err.println("[config] Could not read " + path + ": " + e.getMessage());
        }
    }

    private static String stripQuotes(String value) {
        if (value.length() >= 2
                && ((value.startsWith("\"") && value.endsWith("\""))
                 || (value.startsWith("'") && value.endsWith("'")))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    /** Returns a configuration value, or {@code fallback} when it is not set anywhere. */
    public static String get(String key, String fallback) {
        ensureLoaded();
        String fromProperty = System.getProperty(key);
        if (isSet(fromProperty)) {
            return fromProperty.trim();
        }
        String fromEnv = System.getenv(key);
        if (isSet(fromEnv)) {
            return fromEnv.trim();
        }
        String fromFile = VALUES.get(key);
        if (isSet(fromFile)) {
            return fromFile.trim();
        }
        return fallback;
    }

    /** Returns an integer configuration value, or {@code fallback} when unset or malformed. */
    public static int getInt(String key, int fallback) {
        try {
            return Integer.parseInt(get(key, String.valueOf(fallback)).trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** Returns a double configuration value, or {@code fallback} when unset or malformed. */
    public static double getDouble(String key, double fallback) {
        try {
            return Double.parseDouble(get(key, String.valueOf(fallback)).trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** Returns a boolean configuration value. */
    public static boolean getBoolean(String key, boolean fallback) {
        String value = get(key, String.valueOf(fallback));
        return "true".equalsIgnoreCase(value) || "1".equals(value) || "yes".equalsIgnoreCase(value);
    }

    /**
     * Returns true when a usable API key is configured. When false the application runs in
     * offline mode, which is reported clearly in both user interfaces.
     */
    public static boolean hasApiKey() {
        String key = get(KEY_API_KEY, "");
        return isSet(key) && !key.contains("YOUR_") && !key.equalsIgnoreCase("changeme");
    }

    private static boolean isSet(String value) {
        return value != null && !value.trim().isEmpty();
    }

    /**
     * Returns a redacted preview of the API key, safe to print in logs, reports and
     * screen recordings. Only the last four characters are ever shown.
     */
    public static String redactedApiKey() {
        String key = get(KEY_API_KEY, "");
        if (!isSet(key)) {
            return "(not set)";
        }
        if (key.length() <= 4) {
            return "****";
        }
        return "****" + key.substring(key.length() - 4);
    }

    /** Clears cached values; used by tests. */
    static synchronized void resetForTesting() {
        VALUES.clear();
        loaded = false;
    }
}
