package com.eecs3311.codereview.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A minimal, dependency-free JSON parser and writer.
 *
 * <p>The project deliberately avoids third-party libraries (Jackson/Gson) so that the
 * application builds and runs on any machine with only a JDK installed. This class is
 * used in three places:</p>
 * <ul>
 *   <li>Building request bodies for the LLM HTTP API.</li>
 *   <li>Parsing LLM responses (including structured findings returned by the model).</li>
 *   <li>Persisting agent memory and session snapshots to disk.</li>
 * </ul>
 *
 * <p>Parsed values map to Java types as follows:
 * object -&gt; {@link Map}, array -&gt; {@link List}, string -&gt; {@link String},
 * number -&gt; {@link Double}, true/false -&gt; {@link Boolean}, null -&gt; {@code null}.</p>
 */
public final class Json {

    private final String src;
    private int pos;

    private Json(String src) {
        this.src = src;
        this.pos = 0;
    }

    // ------------------------------------------------------------------ parsing

    /** Parses a JSON document and returns a Map, List, String, Double, Boolean or null. */
    public static Object parse(String text) {
        if (text == null) {
            return null;
        }
        Json p = new Json(text);
        p.skipWhitespace();
        Object value = p.readValue();
        return value;
    }

    /**
     * Parses text that is expected to be a JSON object, returning an empty map on failure.
     * LLMs frequently wrap JSON in markdown fences or add prose around it, so this method
     * first attempts to isolate the JSON region before parsing.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String text) {
        try {
            Object value = parse(extractJsonRegion(text, '{', '}'));
            if (value instanceof Map) {
                return (Map<String, Object>) value;
            }
        } catch (RuntimeException ignored) {
            // fall through to empty map - callers treat this as "model returned no structure"
        }
        return new LinkedHashMap<>();
    }

    /** Parses text that is expected to be a JSON array, returning an empty list on failure. */
    @SuppressWarnings("unchecked")
    public static List<Object> parseArray(String text) {
        try {
            Object value = parse(extractJsonRegion(text, '[', ']'));
            if (value instanceof List) {
                return (List<Object>) value;
            }
            if (value instanceof Map) {
                // Some models wrap the array in {"findings": [...]} - unwrap the first array found.
                for (Object v : ((Map<String, Object>) value).values()) {
                    if (v instanceof List) {
                        return (List<Object>) v;
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // fall through
        }
        return new ArrayList<>();
    }

    /**
     * Strips markdown code fences and surrounding prose, returning the substring between the
     * first {@code open} character and the last matching {@code close} character.
     */
    public static String extractJsonRegion(String text, char open, char close) {
        if (text == null) {
            return "";
        }
        String cleaned = text.replace("```json", "```").trim();
        int fence = cleaned.indexOf("```");
        if (fence >= 0) {
            int end = cleaned.indexOf("```", fence + 3);
            if (end > fence) {
                cleaned = cleaned.substring(fence + 3, end).trim();
            }
        }
        int start = cleaned.indexOf(open);
        int last = cleaned.lastIndexOf(close);
        if (start >= 0 && last > start) {
            return cleaned.substring(start, last + 1);
        }
        return cleaned;
    }

    private Object readValue() {
        skipWhitespace();
        if (pos >= src.length()) {
            throw new IllegalArgumentException("Unexpected end of JSON input");
        }
        char c = src.charAt(pos);
        switch (c) {
            case '{': return readObject();
            case '[': return readArray();
            case '"': return readString();
            case 't': expect("true"); return Boolean.TRUE;
            case 'f': expect("false"); return Boolean.FALSE;
            case 'n': expect("null"); return null;
            default:  return readNumber();
        }
    }

    private Map<String, Object> readObject() {
        Map<String, Object> map = new LinkedHashMap<>();
        pos++; // consume '{'
        skipWhitespace();
        if (peek() == '}') {
            pos++;
            return map;
        }
        while (pos < src.length()) {
            skipWhitespace();
            String key = readString();
            skipWhitespace();
            if (peek() != ':') {
                throw new IllegalArgumentException("Expected ':' at position " + pos);
            }
            pos++;
            Object value = readValue();
            map.put(key, value);
            skipWhitespace();
            char c = peek();
            if (c == ',') {
                pos++;
            } else if (c == '}') {
                pos++;
                return map;
            } else {
                throw new IllegalArgumentException("Expected ',' or '}' at position " + pos);
            }
        }
        throw new IllegalArgumentException("Unterminated JSON object");
    }

    private List<Object> readArray() {
        List<Object> list = new ArrayList<>();
        pos++; // consume '['
        skipWhitespace();
        if (peek() == ']') {
            pos++;
            return list;
        }
        while (pos < src.length()) {
            list.add(readValue());
            skipWhitespace();
            char c = peek();
            if (c == ',') {
                pos++;
            } else if (c == ']') {
                pos++;
                return list;
            } else {
                throw new IllegalArgumentException("Expected ',' or ']' at position " + pos);
            }
        }
        throw new IllegalArgumentException("Unterminated JSON array");
    }

    private String readString() {
        if (peek() != '"') {
            throw new IllegalArgumentException("Expected string at position " + pos);
        }
        pos++;
        StringBuilder sb = new StringBuilder();
        while (pos < src.length()) {
            char c = src.charAt(pos++);
            if (c == '"') {
                return sb.toString();
            }
            if (c == '\\') {
                if (pos >= src.length()) {
                    break;
                }
                char esc = src.charAt(pos++);
                switch (esc) {
                    case 'n': sb.append('\n'); break;
                    case 't': sb.append('\t'); break;
                    case 'r': sb.append('\r'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case '/': sb.append('/'); break;
                    case '\\': sb.append('\\'); break;
                    case '"': sb.append('"'); break;
                    case 'u':
                        if (pos + 4 <= src.length()) {
                            sb.append((char) Integer.parseInt(src.substring(pos, pos + 4), 16));
                            pos += 4;
                        }
                        break;
                    default: sb.append(esc);
                }
            } else {
                sb.append(c);
            }
        }
        throw new IllegalArgumentException("Unterminated JSON string");
    }

    private Double readNumber() {
        int start = pos;
        while (pos < src.length() && "-+.eE0123456789".indexOf(src.charAt(pos)) >= 0) {
            pos++;
        }
        if (start == pos) {
            throw new IllegalArgumentException("Invalid JSON token at position " + pos);
        }
        return Double.valueOf(src.substring(start, pos));
    }

    private void expect(String literal) {
        if (!src.startsWith(literal, pos)) {
            throw new IllegalArgumentException("Expected '" + literal + "' at position " + pos);
        }
        pos += literal.length();
    }

    private char peek() {
        return pos < src.length() ? src.charAt(pos) : '\0';
    }

    private void skipWhitespace() {
        while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
            pos++;
        }
    }

    // ------------------------------------------------------------------ writing

    /** Serialises a Java value (Map, List, String, Number, Boolean, null) to JSON text. */
    public static String stringify(Object value) {
        StringBuilder sb = new StringBuilder();
        write(value, sb);
        return sb.toString();
    }

    private static void write(Object value, StringBuilder sb) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String) {
            sb.append('"').append(escape((String) value)).append('"');
        } else if (value instanceof Number || value instanceof Boolean) {
            sb.append(value);
        } else if (value instanceof Map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : ((Map<?, ?>) value).entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                sb.append('"').append(escape(String.valueOf(e.getKey()))).append("\":");
                write(e.getValue(), sb);
            }
            sb.append('}');
        } else if (value instanceof Iterable) {
            sb.append('[');
            boolean first = true;
            for (Object o : (Iterable<?>) value) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                write(o, sb);
            }
            sb.append(']');
        } else {
            sb.append('"').append(escape(String.valueOf(value))).append('"');
        }
    }

    /** Escapes a string so it is safe to embed inside a JSON string literal. */
    public static String escape(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                case '\b': sb.append("\\b");  break;
                case '\f': sb.append("\\f");  break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ safe getters

    /** Reads a nested string from a parsed JSON structure, returning {@code fallback} if absent. */
    @SuppressWarnings("unchecked")
    public static String getString(Object root, String fallback, Object... path) {
        Object current = root;
        for (Object key : path) {
            if (current instanceof Map && key instanceof String) {
                current = ((Map<String, Object>) current).get(key);
            } else if (current instanceof List && key instanceof Integer) {
                List<Object> list = (List<Object>) current;
                int index = (Integer) key;
                current = (index >= 0 && index < list.size()) ? list.get(index) : null;
            } else {
                return fallback;
            }
        }
        return current == null ? fallback : String.valueOf(current);
    }

    /** Reads a nested number from a parsed JSON structure, returning {@code fallback} if absent. */
    @SuppressWarnings("unchecked")
    public static int getInt(Object root, int fallback, Object... path) {
        Object current = root;
        for (Object key : path) {
            if (current instanceof Map && key instanceof String) {
                current = ((Map<String, Object>) current).get(key);
            } else if (current instanceof List && key instanceof Integer) {
                List<Object> list = (List<Object>) current;
                int index = (Integer) key;
                current = (index >= 0 && index < list.size()) ? list.get(index) : null;
            } else {
                return fallback;
            }
        }
        if (current instanceof Number) {
            return ((Number) current).intValue();
        }
        try {
            return (int) Double.parseDouble(String.valueOf(current));
        } catch (RuntimeException e) {
            return fallback;
        }
    }
}
