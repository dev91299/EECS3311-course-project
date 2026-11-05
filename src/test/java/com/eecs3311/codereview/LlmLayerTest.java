package com.eecs3311.codereview;

import com.eecs3311.codereview.agent.strategy.AnalysisContext;
import com.eecs3311.codereview.agent.strategy.ReviewStrategy;
import com.eecs3311.codereview.agent.strategy.StrategyRegistry;
import com.eecs3311.codereview.llm.LLMException;
import com.eecs3311.codereview.llm.LLMProvider;
import com.eecs3311.codereview.llm.LLMRequest;
import com.eecs3311.codereview.llm.LLMResponse;
import com.eecs3311.codereview.llm.MockProvider;
import com.eecs3311.codereview.llm.PromptBuilder;
import com.eecs3311.codereview.llm.decorator.CachingProvider;
import com.eecs3311.codereview.llm.decorator.LoggingProvider;
import com.eecs3311.codereview.llm.decorator.RetryingProvider;
import com.eecs3311.codereview.llm.decorator.UsageStats;
import com.eecs3311.codereview.model.ChatMessage;
import com.eecs3311.codereview.repo.StandardsChecker;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the Decorator chain around the model client, the Builder that assembles prompts,
 * and the Strategy registry.
 *
 * <p>Each decorator is tested against a stub provider rather than a real endpoint, so the
 * suite runs offline and deterministically.</p>
 */
class LlmLayerTest {

    /** A stub that counts calls and can be told to fail a given number of times first. */
    private static class StubProvider implements LLMProvider {
        private final AtomicInteger calls = new AtomicInteger();
        private int failuresRemaining;
        private boolean retryableFailures = true;

        StubProvider() {
        }

        StubProvider failing(int times, boolean retryable) {
            this.failuresRemaining = times;
            this.retryableFailures = retryable;
            return this;
        }

        @Override
        public LLMResponse complete(LLMRequest request) throws LLMException {
            calls.incrementAndGet();
            if (failuresRemaining > 0) {
                failuresRemaining--;
                throw new LLMException("simulated failure", retryableFailures, 500);
            }
            return new LLMResponse("stub reply", "stub-model", 100, 50, 12L, false, false);
        }

        @Override
        public String name() {
            return "stub";
        }

        int getCalls() {
            return calls.get();
        }
    }

    private LLMRequest request(String text) {
        return new LLMRequest(List.of(ChatMessage.user(text)), 0.2, 1024, "unit-test");
    }

    // ------------------------------------------------------------------ caching decorator

    @Test
    void cacheServesTheSecondIdenticalRequestWithoutCallingUpstream() throws LLMException {
        StubProvider stub = new StubProvider();
        CachingProvider cache = new CachingProvider(stub, 16, true);

        cache.complete(request("analyse this"));
        LLMResponse second = cache.complete(request("analyse this"));

        assertEquals(1, stub.getCalls(), "the second call must not reach the network");
        assertTrue(second.isFromCache());
        assertEquals(1, cache.getHits());
        assertEquals(1, cache.getMisses());
    }

    @Test
    void differentRequestsAreNotConfused() throws LLMException {
        StubProvider stub = new StubProvider();
        CachingProvider cache = new CachingProvider(stub, 16, true);

        cache.complete(request("file A"));
        cache.complete(request("file B"));

        assertEquals(2, stub.getCalls());
        assertEquals(0, cache.getHits());
    }

    @Test
    void disablingTheCacheAlwaysDelegates() throws LLMException {
        StubProvider stub = new StubProvider();
        CachingProvider cache = new CachingProvider(stub, 16, false);

        cache.complete(request("same"));
        cache.complete(request("same"));

        assertEquals(2, stub.getCalls());
    }

    // ------------------------------------------------------------------ retrying decorator

    @Test
    void retryableFailuresAreRetriedUntilOneSucceeds() throws LLMException {
        StubProvider stub = new StubProvider().failing(2, true);
        RetryingProvider retrying = new RetryingProvider(stub, 4, 1L, null);

        LLMResponse response = retrying.complete(request("x"));

        assertEquals("stub reply", response.getContent());
        assertEquals(3, stub.getCalls(), "two failures then one success");
        assertEquals(2, retrying.getTotalRetries());
    }

    @Test
    void nonRetryableFailuresFailImmediately() {
        StubProvider stub = new StubProvider().failing(5, false);
        RetryingProvider retrying = new RetryingProvider(stub, 4, 1L, null);

        assertThrows(LLMException.class, () -> retrying.complete(request("x")));
        assertEquals(1, stub.getCalls(), "an invalid key must not be retried");
    }

    @Test
    void retriesStopAtTheConfiguredLimit() {
        StubProvider stub = new StubProvider().failing(99, true);
        RetryingProvider retrying = new RetryingProvider(stub, 3, 1L, null);

        assertThrows(LLMException.class, () -> retrying.complete(request("x")));
        assertEquals(3, stub.getCalls());
    }

    @Test
    void theStatusListenerReceivesRetryProgress() {
        StringBuilder messages = new StringBuilder();
        StubProvider stub = new StubProvider().failing(1, true);
        RetryingProvider retrying = new RetryingProvider(stub, 3, 1L, messages::append);

        assertDoesNotThrowChecked(() -> retrying.complete(request("x")));
        assertTrue(messages.toString().contains("Retrying"));
    }

    // ------------------------------------------------------------------ logging decorator

    @Test
    void usageIsAccumulatedPerFeature() throws LLMException {
        UsageStats stats = new UsageStats();
        LoggingProvider logging = new LoggingProvider(new StubProvider(), stats, false);

        logging.complete(new LLMRequest(List.of(ChatMessage.user("a")), 0.2, 100, "security-analysis"));
        logging.complete(new LLMRequest(List.of(ChatMessage.user("b")), 0.2, 100, "security-analysis"));
        logging.complete(new LLMRequest(List.of(ChatMessage.user("c")), 0.2, 100, "test-generation"));

        assertEquals(3, stats.getRequestCount());
        assertEquals(450, stats.getTotalTokens(), "3 calls x 150 tokens");
        assertEquals(2, stats.getCallsByPurpose().get("security-analysis"));
        assertTrue(stats.render().contains("test-generation"));
    }

    @Test
    void failuresAreCountedSeparately() {
        UsageStats stats = new UsageStats();
        LoggingProvider logging = new LoggingProvider(
                new StubProvider().failing(1, false), stats, false);

        assertThrows(LLMException.class, () -> logging.complete(request("x")));
        assertEquals(1, stats.getFailureCount());
        assertEquals(0, stats.getRequestCount());
    }

    @Test
    void costEstimationUsesTheConfiguredRates() throws LLMException {
        UsageStats stats = new UsageStats();
        new LoggingProvider(new StubProvider(), stats, false).complete(request("x"));
        assertEquals(0.0, stats.estimateCostUsd(0.0, 0.0), 1e-9, "a free tier costs nothing");
        assertEquals(0.0002, stats.estimateCostUsd(1.0, 2.0), 1e-9);
    }

    @Test
    void decoratorsComposeInAnyOrderAndPreserveTheInterface() throws LLMException {
        StubProvider stub = new StubProvider();
        LLMProvider chain = new LoggingProvider(
                new CachingProvider(
                        new RetryingProvider(stub, 2, 1L, null), 8, true),
                new UsageStats(), false);

        chain.complete(request("x"));
        chain.complete(request("x"));

        assertEquals(1, stub.getCalls());
        assertTrue(chain.name().contains("stub"));
        assertFalse(chain.isOffline());
    }

    // ------------------------------------------------------------------ offline provider

    @Test
    void offlineProviderDerivesFindingsFromTheActualCode() throws LLMException {
        MockProvider provider = new MockProvider();
        String code = "String q = \"SELECT * FROM users WHERE id = '\" + id + \"'\";\n"
                + "ResultSet r = statement.executeQuery(q + extra);\n";
        LLMRequest request = new LLMRequest(
                List.of(ChatMessage.user("```java\n" + code + "```")), 0.2, 1024, "security-analysis");

        String reply = provider.complete(request).getContent();
        assertTrue(reply.contains("findings"));
        assertTrue(reply.toLowerCase().contains("sql"),
                "the offline analyser must react to the code it was given");
        assertTrue(provider.isOffline());
    }

    @Test
    void offlineProviderReturnsNoFindingsForCleanCode() throws LLMException {
        MockProvider provider = new MockProvider();
        LLMRequest request = new LLMRequest(
                List.of(ChatMessage.user("```java\nint total = a + b;\n```")),
                0.2, 1024, "security-analysis");
        String reply = provider.complete(request).getContent();
        assertTrue(reply.contains("\"findings\":[]"));
    }

    // ------------------------------------------------------------------ prompt builder

    @Test
    void builderOmitsSectionsThatWereNotSupplied() {
        String prompt = new PromptBuilder()
                .role("reviewer")
                .code("A.java", "class A {}")
                .build();

        assertTrue(prompt.contains("CODE UNDER REVIEW"));
        assertFalse(prompt.contains("PROJECT CONVENTIONS"));
        assertFalse(prompt.contains("REMEMBERED DEVELOPER FEEDBACK"));
    }

    @Test
    void builderIncludesEverySuppliedSectionInOrder() {
        String prompt = new PromptBuilder()
                .conventions("max_line_length = 100")
                .memory("- The team REJECTED \"x\"")
                .retrievedContext("--- Other.java")
                .code("A.java", "class A {}")
                .outputSchema("{\"findings\":[]}")
                .instruction("Report only real defects.")
                .build();

        assertTrue(prompt.indexOf("PROJECT CONVENTIONS") < prompt.indexOf("REMEMBERED"));
        assertTrue(prompt.indexOf("REMEMBERED") < prompt.indexOf("RELATED CODE"));
        assertTrue(prompt.indexOf("RELATED CODE") < prompt.indexOf("CODE UNDER REVIEW"));
        assertTrue(prompt.indexOf("CODE UNDER REVIEW") < prompt.indexOf("REQUIRED OUTPUT FORMAT"));
        assertTrue(prompt.endsWith("Report only real defects."));
    }

    @Test
    void oversizedCodeIsTruncatedWithAnExplicitMarker() {
        PromptBuilder builder = new PromptBuilder()
                .maxCodeCharacters(500)
                .code("Big.java", "x".repeat(2000));

        assertTrue(builder.wasTruncated());
        assertTrue(builder.build().contains("TRUNCATED"));
        assertTrue(builder.build().contains("Select a smaller snippet"));
    }

    @Test
    void systemPromptAlwaysCarriesTheScopeRestriction() {
        String system = new PromptBuilder().role("You review Java.").buildSystemPrompt();
        assertTrue(system.contains("You review Java."));
        assertTrue(system.toLowerCase().contains("software engineering"));
    }

    // ------------------------------------------------------------------ strategy registry

    @Test
    void registryExposesTheFourBuiltInStrategies() {
        StrategyRegistry registry = new StrategyRegistry(new StandardsChecker());
        assertEquals(4, registry.all().size());
        assertTrue(registry.names().containsAll(
                List.of("security", "style", "performance", "maintainability")));
    }

    @Test
    void registryLookupIsCaseInsensitiveAndHasADefault() {
        StrategyRegistry registry = new StrategyRegistry(new StandardsChecker());
        assertTrue(registry.find("SECURITY").isPresent());
        assertTrue(registry.find("nonexistent").isEmpty());
        assertEquals("security", registry.getDefault().getName());
    }

    @Test
    void registeringAStrategyReplacesTheOneWithTheSameName() {
        StrategyRegistry registry = new StrategyRegistry(new StandardsChecker());
        ReviewStrategy replacement = new ReviewStrategy() {
            @Override
            public List<com.eecs3311.codereview.model.ReviewItem> executeStrategy(
                    AnalysisContext context) {
                return List.of();
            }

            @Override
            public String getName() {
                return "security";
            }

            @Override
            public String getDescription() {
                return "replacement";
            }
        };
        registry.register(replacement);
        assertSame(replacement, registry.find("security").orElseThrow());
        assertEquals(4, registry.all().size(), "replacing must not grow the registry");
    }

    @Test
    void emptyContextShortCircuitsWithoutCallingTheModel() throws LLMException {
        StrategyRegistry registry = new StrategyRegistry(new StandardsChecker());
        ReviewStrategy security = registry.find("security").orElseThrow();
        assertTrue(security.executeStrategy(AnalysisContext.of("A.java", "   ")).isEmpty());
    }

    @Test
    void analysisContextInfersTheLanguageFromTheExtension() {
        assertEquals("java", AnalysisContext.of("A.java", "x").getLanguage());
        assertEquals("python", AnalysisContext.of("a.py", "x").getLanguage());
        assertEquals("typescript", AnalysisContext.of("a.ts", "x").getLanguage());
        assertEquals("text", AnalysisContext.of("a.unknown", "x").getLanguage());
    }

    /** Small helper so a checked-exception lambda reads cleanly in an assertion. */
    private void assertDoesNotThrowChecked(ThrowingRunnable runnable) {
        try {
            runnable.run();
        } catch (Exception e) {
            throw new AssertionError("expected no exception but got: " + e, e);
        }
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
