package com.eecs3311.codereview;

import com.eecs3311.codereview.json.Json;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the JSON layer, with particular attention to the malformed output a language
 * model actually produces: fenced blocks, prose wrapped around the JSON, and the array
 * buried inside a wrapper object.
 */
class JsonTest {

    @Test
    void parsesAFlatObject() {
        Object parsed = Json.parse("{\"name\":\"test\",\"count\":3,\"ok\":true,\"none\":null}");
        assertTrue(parsed instanceof Map);
        assertEquals("test", Json.getString(parsed, "", "name"));
        assertEquals(3, Json.getInt(parsed, 0, "count"));
    }

    @Test
    void parsesNestedPaths() {
        String text = "{\"choices\":[{\"message\":{\"content\":\"hello world\"}}],"
                + "\"usage\":{\"prompt_tokens\":120,\"completion_tokens\":45}}";
        Object parsed = Json.parse(text);
        assertEquals("hello world", Json.getString(parsed, "", "choices", 0, "message", "content"));
        assertEquals(120, Json.getInt(parsed, 0, "usage", "prompt_tokens"));
        assertEquals(45, Json.getInt(parsed, 0, "usage", "completion_tokens"));
    }

    @Test
    void stripsMarkdownFencesBeforeParsing() {
        String reply = "Here is the result:\n```json\n{\"findings\":[{\"title\":\"Leak\"}]}\n```\nHope that helps.";
        List<Object> findings = Json.parseArray(reply);
        assertEquals(1, findings.size());
        assertEquals("Leak", Json.getString(findings.get(0), "", "title"));
    }

    @Test
    void unwrapsAnArrayHeldInsideAWrapperObject() {
        String reply = "{\"findings\":[{\"title\":\"A\"},{\"title\":\"B\"}]}";
        assertEquals(2, Json.parseArray(reply).size());
    }

    @Test
    void acceptsABareArray() {
        assertEquals(2, Json.parseArray("[{\"title\":\"A\"},{\"title\":\"B\"}]").size());
    }

    @Test
    void returnsEmptyRatherThanThrowingOnGarbage() {
        assertTrue(Json.parseArray("the model refused to answer").isEmpty());
        assertTrue(Json.parseObject("not json at all").isEmpty());
        assertTrue(Json.parseArray(null).isEmpty());
    }

    @Test
    void escapesControlAndQuoteCharacters() {
        String escaped = Json.escape("line1\nline2 \"quoted\" back\\slash\ttab");
        assertTrue(escaped.contains("\\n"));
        assertTrue(escaped.contains("\\\""));
        assertTrue(escaped.contains("\\\\"));
        assertTrue(escaped.contains("\\t"));
    }

    @Test
    void roundTripsThroughStringifyAndParse() {
        Map<String, Object> original = Map.of("model", "llama-3.3-70b-versatile",
                "temperature", 0.2, "stream", false);
        Object parsed = Json.parse(Json.stringify(original));
        assertEquals("llama-3.3-70b-versatile", Json.getString(parsed, "", "model"));
    }

    @Test
    void handlesUnicodeEscapesInStrings() {
        assertEquals("A", Json.getString(Json.parse("{\"v\":\"\\u0041\"}"), "", "v"));
    }

    @Test
    void parsesDeeplyNestedStructuresWithoutLoss() {
        String text = "{\"a\":{\"b\":{\"c\":[1,2,{\"d\":\"deep\"}]}}}";
        Object parsed = Json.parse(text);
        assertEquals("deep", Json.getString(parsed, "", "a", "b", "c", 2, "d"));
    }

    @Test
    void missingPathsReturnTheFallback() {
        Object parsed = Json.parse("{\"a\":1}");
        assertEquals("fallback", Json.getString(parsed, "fallback", "missing", "deeper"));
        assertEquals(99, Json.getInt(parsed, 99, "missing"));
    }
}
