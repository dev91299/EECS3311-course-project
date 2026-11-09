import com.eecs3311.codereview.agent.strategy.AnalysisContext;
import com.eecs3311.codereview.agent.strategy.StrategyRegistry;
import com.eecs3311.codereview.app.AgentController;
import com.eecs3311.codereview.command.AcceptCommand;
import com.eecs3311.codereview.command.BulkTriageCommand;
import com.eecs3311.codereview.command.CommandHistory;
import com.eecs3311.codereview.command.RejectCommand;
import com.eecs3311.codereview.command.ReviewCommand;
import com.eecs3311.codereview.export.ExporterFactory;
import com.eecs3311.codereview.json.Json;
import com.eecs3311.codereview.llm.LLMException;
import com.eecs3311.codereview.llm.LLMProvider;
import com.eecs3311.codereview.llm.LLMRequest;
import com.eecs3311.codereview.llm.LLMResponse;
import com.eecs3311.codereview.llm.PromptBuilder;
import com.eecs3311.codereview.llm.decorator.CachingProvider;
import com.eecs3311.codereview.llm.decorator.LoggingProvider;
import com.eecs3311.codereview.llm.decorator.RateLimitingProvider;
import com.eecs3311.codereview.llm.decorator.RetryingProvider;
import com.eecs3311.codereview.llm.decorator.UsageStats;
import com.eecs3311.codereview.model.ChatMessage;
import com.eecs3311.codereview.model.DeltaReport;
import com.eecs3311.codereview.model.FileNode;
import com.eecs3311.codereview.model.ReviewItem;
import com.eecs3311.codereview.model.ReviewReport;
import com.eecs3311.codereview.model.ReviewStatus;
import com.eecs3311.codereview.model.Severity;
import com.eecs3311.codereview.observer.Observer;
import com.eecs3311.codereview.repo.StandardsChecker;
import com.eecs3311.codereview.util.Diff;
import com.eecs3311.codereview.util.SourceText;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A verification harness that needs no build tool and no downloads.
 *
 * <p>The project's main test suite uses JUnit 5 and runs with {@code mvn test}. That requires
 * Maven to download JUnit the first time, which is not always possible on a locked-down or
 * offline machine. This class checks the same core invariants using nothing but the JDK, so
 * the implementation can always be verified:</p>
 *
 * <pre>
 *   javac -d target/classes $(find src/main/java -name '*.java')
 *   javac -cp target/classes -d target/tools tools/SelfCheck.java
 *   java -cp target/classes:target/tools SelfCheck samples/demo-project
 * </pre>
 *
 * <p>It exits 0 when every check passes and 1 otherwise, so it can also be used as a build
 * gate.</p>
 */
public final class SelfCheck {

    private static int passed;
    private static int failed;
    private static final List<String> FAILURES = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        String repository = args.length > 0 ? args[0] : "samples/demo-project";

        System.out.println();
        System.out.println("AI Code Review Agent - self check (no external dependencies)");
        System.out.println("============================================================");
        System.out.println("Runs against the built-in offline analyser, so the result is the");
        System.out.println("same every time and uses none of your API quota.");

        section("Composite pattern - FileNode");
        checkComposite();

        section("Command and Memento patterns - undo");
        checkCommands();

        section("Observer pattern - view notification");
        checkObserver();

        section("Decorator pattern - provider chain");
        checkDecorators();

        section("Rate limiting - requests and tokens per minute");
        checkRateLimiting();

        section("Builder pattern - prompt assembly");
        checkPromptBuilder();

        section("Strategy and Template Method patterns");
        checkStrategies();

        section("Deterministic standards checker (F06)");
        checkStandards();

        section("JSON tolerance to model output");
        checkJson();

        section("Diff engine (F04)");
        checkDiff();

        section("Version comparison (F08)");
        checkDelta();

        section("Factory Method and exporters (F09)");
        checkExporters();

        section("End-to-end: import, analyse, triage, export");
        checkEndToEnd(repository);

        System.out.println();
        System.out.println("============================================================");
        System.out.printf("  %d passed, %d failed%n", passed, failed);
        if (failed > 0) {
            System.out.println();
            FAILURES.forEach(failure -> System.out.println("  FAILED: " + failure));
        }
        System.out.println();
        System.exit(failed == 0 ? 0 : 1);
    }

    // ------------------------------------------------------------------ checks

    private static void checkComposite() {
        FileNode root = FileNode.directory("project", "/project");
        FileNode src = FileNode.directory("src", "/project/src");
        src.add(FileNode.file("App.java", "/project/src/App.java", 1000));
        src.add(FileNode.file("Util.java", "/project/src/Util.java", 500));
        root.add(src);
        root.add(FileNode.file("pom.xml", "/project/pom.xml", 300));

        check("leaf counts as one file",
                FileNode.file("A.java", "/A.java", 10).getTotalFiles() == 1);
        check("directory sums its whole subtree", root.getTotalFiles() == 3);
        check("sizes sum recursively", root.getTotalSizeBytes() == 1800);
        check("flatten returns files only", root.flatten().size() == 3
                && root.flatten().stream().noneMatch(FileNode::isDirectory));
        check("find locates a nested file by name",
                root.find("Util.java").map(FileNode::getPath)
                        .orElse("").equals("/project/src/Util.java"));
        check("find returns empty for a missing file", root.find("Missing.java").isEmpty());
        check("a leaf rejects children",
                FileNode.file("A.java", "/A.java", 1)
                        .add(FileNode.file("B.java", "/B.java", 1)).getChildren().isEmpty());
    }

    private static void checkCommands() {
        ReviewReport report = new ReviewReport();
        ReviewItem finding = new ReviewItem("/A.java", 42, Severity.HIGH, "Null dereference",
                "desc", "fix", "Correctness", "security");
        report.addItem(finding);
        CommandHistory history = new CommandHistory();

        history.execute(new AcceptCommand(finding, report, "fixed in a1b2c3"));
        check("accept sets the status", finding.getStatus() == ReviewStatus.ACCEPTED);
        check("accept stores the note", finding.getFeedback().equals("fixed in a1b2c3"));

        history.execute(new RejectCommand(finding, report, "unreachable path"));
        check("reject overwrites the status", finding.getStatus() == ReviewStatus.REJECTED);

        history.undo();
        check("undo restores the previous command's state, not a default",
                finding.getStatus() == ReviewStatus.ACCEPTED
                        && finding.getFeedback().equals("fixed in a1b2c3"));

        history.redo();
        check("redo reapplies the undone command", finding.getStatus() == ReviewStatus.REJECTED);

        history.undo();
        history.execute(new AcceptCommand(finding, report, "new branch"));
        check("a new action clears the redo branch", !history.canRedo());

        // Bulk action undoes as one step.
        ReviewReport bulkReport = new ReviewReport();
        List<ReviewCommand> commands = new ArrayList<>();
        List<ReviewItem> bulkItems = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            ReviewItem item = new ReviewItem("/B" + i + ".java", i + 1, Severity.INFO,
                    "Info finding " + i, "", "", "Style", "style");
            bulkReport.addItem(item);
            bulkItems.add(item);
            commands.add(new RejectCommand(item, bulkReport, "bulk"));
        }
        CommandHistory bulkHistory = new CommandHistory();
        bulkHistory.execute(new BulkTriageCommand("Reject all Info", commands));
        check("bulk action applies to every finding",
                bulkItems.stream().allMatch(i -> i.getStatus() == ReviewStatus.REJECTED));
        bulkHistory.undo();
        check("bulk action undoes as a single step",
                bulkItems.stream().allMatch(i -> i.getStatus() == ReviewStatus.PENDING)
                        && !bulkHistory.canUndo());

        check("undo on an empty history is safe", new CommandHistory().undo().isEmpty());
        check("the log records both DO and UNDO",
                history.renderLog().contains("DO") && history.renderLog().contains("UNDO"));
    }

    private static void checkObserver() {
        ReviewReport report = new ReviewReport();
        AtomicInteger calls = new AtomicInteger();
        Observer counting = (event, payload) -> calls.incrementAndGet();
        Observer faulty = (event, payload) -> {
            throw new IllegalStateException("broken view");
        };

        report.addObserver(faulty);
        report.addObserver(counting);
        report.addObserver(counting);
        check("duplicate registration is ignored", report.getObserverCount() == 2);

        report.addItems(List.of(
                new ReviewItem("/A.java", 1, Severity.HIGH, "One", "", "", "Security", "security"),
                new ReviewItem("/A.java", 2, Severity.LOW, "Two", "", "", "Style", "style")));
        check("a batch notifies exactly once", calls.get() == 1);
        check("a failing view does not block the others", calls.get() == 1);

        report.removeObserver(counting);
        report.addItem(new ReviewItem("/A.java", 3, Severity.LOW, "Three", "", "",
                "Style", "style"));
        check("removed observers stop receiving events", calls.get() == 1);

        check("duplicate findings are discarded", report.getTotalIssues() == 3);
        report.addItem(new ReviewItem("/A.java", 1, Severity.HIGH, "One", "", "",
                "Security", "security"));
        check("re-adding an identical finding is a no-op", report.getTotalIssues() == 3);
    }

    private static void checkDecorators() throws Exception {
        CountingProvider stub = new CountingProvider();
        CachingProvider cache = new CachingProvider(stub, 8, true);
        cache.complete(request("same", "test"));
        LLMResponse second = cache.complete(request("same", "test"));
        check("cache avoids the second network call", stub.calls.get() == 1);
        check("cached replies are flagged", second.isFromCache());

        cache.complete(request("different", "test"));
        check("different prompts are not confused", stub.calls.get() == 2);

        CountingProvider flaky = new CountingProvider();
        flaky.failuresRemaining = 2;
        flaky.retryable = true;
        RetryingProvider retrying = new RetryingProvider(flaky, 4, 1L, null);
        retrying.complete(request("x", "test"));
        check("retryable failures are retried until success", flaky.calls.get() == 3);

        CountingProvider fatal = new CountingProvider();
        fatal.failuresRemaining = 5;
        fatal.retryable = false;
        RetryingProvider noRetry = new RetryingProvider(fatal, 4, 1L, null);
        boolean threw = false;
        try {
            noRetry.complete(request("x", "test"));
        } catch (LLMException e) {
            threw = true;
        }
        check("non-retryable failures fail immediately", threw && fatal.calls.get() == 1);

        UsageStats stats = new UsageStats();
        LoggingProvider logging = new LoggingProvider(new CountingProvider(), stats, false);
        logging.complete(request("a", "security-analysis"));
        logging.complete(request("b", "security-analysis"));
        logging.complete(request("c", "test-generation"));
        check("usage is accumulated", stats.getRequestCount() == 3 && stats.getTotalTokens() == 450);
        check("usage is attributed per feature",
                stats.getCallsByPurpose().get("security-analysis") == 2);
        check("a free tier costs nothing", stats.estimateCostUsd(0, 0) == 0.0);

        CountingProvider base = new CountingProvider();
        LLMProvider chain = new LoggingProvider(
                new CachingProvider(new RetryingProvider(base, 2, 1L, null), 8, true),
                new UsageStats(), false);
        chain.complete(request("y", "test"));
        chain.complete(request("y", "test"));
        check("decorators compose in any order", base.calls.get() == 1);
    }

    private static void checkRateLimiting() throws Exception {
        // A provider that always reports a 429 and asks for a specific wait.
        class Limited implements LLMProvider {
            private final AtomicInteger calls = new AtomicInteger();
            private int failuresRemaining;

            @Override
            public LLMResponse complete(LLMRequest request) throws LLMException {
                calls.incrementAndGet();
                if (failuresRemaining > 0) {
                    failuresRemaining--;
                    throw new LLMException("Rate limit reached.", true, 429, 0.4);
                }
                return new LLMResponse("ok", "stub", 100, 50, 5L, false, false);
            }

            @Override
            public String name() {
                return "limited";
            }
        }

        Limited limited = new Limited();
        limited.failuresRemaining = 1;
        RetryingProvider retrying = new RetryingProvider(limited, 3, 50L, null);

        long started = System.currentTimeMillis();
        retrying.complete(request("x", "test"));
        long elapsed = System.currentTimeMillis() - started;

        check("a 429 is retried after the wait the provider asked for, not the default backoff",
                elapsed >= 400 && elapsed < 3000);
        check("the retry-after value is carried on the exception",
                new LLMException("x", true, 429, 7.5).getRetryAfterSeconds() == 7.5);
        check("an exception without a retry-after reports zero",
                new LLMException("x", true, 500).getRetryAfterSeconds() == 0.0);

        // Token accounting: the window must reflect what was actually charged, not the
        // pre-call estimate, so the next decision is made on real numbers.
        CountingProvider counting = new CountingProvider();
        RateLimitingProvider limiter = new RateLimitingProvider(counting, 600, 100_000, null);
        limiter.complete(request("hello", "test"));
        check("token spend is recorded in the rolling window",
                limiter.getTokensInWindow() == 150);
        limiter.complete(request("hello again", "test"));
        check("spend accumulates across calls", limiter.getTokensInWindow() == 300);

        // With the ceiling disabled the limiter must not block.
        RateLimitingProvider unlimited = new RateLimitingProvider(counting, 600, 0, null);
        long t0 = System.currentTimeMillis();
        unlimited.complete(request("x", "test"));
        check("a zero token ceiling disables token throttling",
                System.currentTimeMillis() - t0 < 1000);

        // A tiny ceiling must force a wait rather than letting the request fail.
        StringBuilder notices = new StringBuilder();
        RateLimitingProvider tight = new RateLimitingProvider(counting, 600, 200, notices::append);
        tight.complete(request("first", "test"));
        long before = System.currentTimeMillis();
        Thread waiter = new Thread(() -> {
            try {
                tight.complete(request("second", "test"));
            } catch (LLMException ignored) {
                // interrupted below
            }
        });
        waiter.start();
        Thread.sleep(600);
        boolean blocked = waiter.isAlive();
        waiter.interrupt();
        waiter.join(2000);
        check("exceeding the token ceiling makes the caller wait rather than fail", blocked);
        check("the user is told why it is waiting",
                notices.toString().toLowerCase().contains("token limit"));
    }

    private static void checkPromptBuilder() {
        String minimal = new PromptBuilder().role("r").code("A.java", "class A {}").build();
        check("unsupplied sections are omitted",
                minimal.contains("CODE UNDER REVIEW") && !minimal.contains("PROJECT CONVENTIONS"));

        String full = new PromptBuilder()
                .conventions("rules").memory("memory").retrievedContext("context")
                .code("A.java", "class A {}").outputSchema("schema").instruction("do it").build();
        check("sections are assembled in a fixed order",
                full.indexOf("PROJECT CONVENTIONS") < full.indexOf("REMEMBERED")
                        && full.indexOf("REMEMBERED") < full.indexOf("RELATED CODE")
                        && full.indexOf("RELATED CODE") < full.indexOf("CODE UNDER REVIEW"));

        PromptBuilder big = new PromptBuilder().maxCodeCharacters(200).code("B.java", "x".repeat(900));
        check("oversized code is truncated with a marker",
                big.wasTruncated() && big.build().contains("TRUNCATED"));
        check("the system prompt carries the scope restriction",
                new PromptBuilder().buildSystemPrompt().toLowerCase().contains("software engineering"));
    }

    private static void checkStrategies() throws Exception {
        StrategyRegistry registry = new StrategyRegistry(new StandardsChecker());
        check("four strategies are registered", registry.all().size() == 4);
        check("lookup is case insensitive", registry.find("SECURITY").isPresent());
        check("unknown strategies are not invented", registry.find("nope").isEmpty());
        check("there is a default strategy", registry.getDefault().getName().equals("security"));
        check("empty input short-circuits before any model call",
                registry.find("security").orElseThrow()
                        .executeStrategy(AnalysisContext.of("A.java", "   ")).isEmpty());
        check("language is inferred from the extension",
                AnalysisContext.of("a.py", "x").getLanguage().equals("python")
                        && AnalysisContext.of("a.java", "x").getLanguage().equals("java"));
    }

    private static void checkStandards() {
        StandardsChecker checker = new StandardsChecker();

        check("wildcard imports are flagged",
                hasFinding(checker.check("A.java", "import java.util.*;\nclass A {}\n"), "wildcard"));
        check("tabs are flagged",
                hasFinding(checker.check("A.java", "class A {\n\tint x;\n}\n"), "tab"));
        check("constants must be UPPER_SNAKE_CASE",
                hasFinding(checker.check("A.java",
                        "class A {\n    private static final String dbPass = \"x\";\n}\n"),
                        "UPPER_SNAKE_CASE"));
        check("unbraced conditionals are flagged",
                hasFinding(checker.check("A.java",
                        "class A {\n    void go(int x) {\n        if (x > 0) return;\n    }\n}\n"),
                        "braces"));

        // Regression: the word "class" inside a comment must not be read as a declaration.
        String withComment = "/**\n * This class is intentionally small.\n */\n"
                + "public class Clean {\n\n    public int size() {\n        return 0;\n    }\n}\n";
        check("prose containing 'class' is not read as a declaration",
                !hasFinding(checker.check("Clean.java", withComment), "capital"));

        // Regression: a constructor is required to start with a capital.
        String withConstructor = "public class Widget {\n\n    public Widget() {\n"
                + "        int x = 0;\n    }\n}\n";
        check("constructors are not flagged as badly named methods",
                !hasFinding(checker.check("Widget.java", withConstructor), "lower-case"));

        // Regression: commas inside generics are not parameter separators.
        StandardsChecker strict = new StandardsChecker();
        strict.loadRules("max_parameters = 2");
        check("generic type arguments do not inflate the parameter count",
                !hasFinding(strict.check("A.java",
                        "class A {\n    void go(Map<String, List<Integer>> a, int b) {\n    }\n}\n"),
                        "parameters"));

        String clean = "package demo;\n\nimport java.util.List;\n\n"
                + "public class Clean {\n\n    private static final int MAX_SIZE = 10;\n\n"
                + "    public int size(List<String> values) {\n"
                + "        if (values == null) {\n            return 0;\n        }\n"
                + "        return Math.min(values.size(), MAX_SIZE);\n    }\n}\n";
        check("conforming code produces no findings", checker.check("Clean.java", clean).isEmpty());

        StandardsChecker configured = new StandardsChecker();
        configured.loadRules("forbid_tabs = false\n");
        check("a rule turned off in .rules does not fire",
                !hasFinding(configured.check("A.java", "class A {\n\tint x;\n}\n"), "tab"));
        check("the rules source is reported",
                configured.getRulesSource().contains(".rules"));

        check("comment stripping preserves line positions",
                SourceText.stripCommentsAndStrings("a // b\nc").split("\n").length == 2);
    }

    private static void checkJson() {
        Object parsed = Json.parse(
                "{\"choices\":[{\"message\":{\"content\":\"hi\"}}],\"usage\":{\"prompt_tokens\":7}}");
        check("nested paths resolve",
                Json.getString(parsed, "", "choices", 0, "message", "content").equals("hi")
                        && Json.getInt(parsed, 0, "usage", "prompt_tokens") == 7);
        check("markdown fences are stripped",
                Json.parseArray("Sure:\n```json\n{\"findings\":[{\"title\":\"X\"}]}\n```").size() == 1);
        check("a wrapped array is unwrapped",
                Json.parseArray("{\"findings\":[{\"a\":1},{\"a\":2}]}").size() == 2);
        check("a bare array parses", Json.parseArray("[{\"a\":1}]").size() == 1);
        check("garbage returns empty instead of throwing",
                Json.parseArray("the model refused").isEmpty()
                        && Json.parseObject("nonsense").isEmpty());
        check("escaping covers quotes, newlines and backslashes",
                Json.escape("a\"b\nc\\d").equals("a\\\"b\\nc\\\\d"));
        check("missing paths return the fallback",
                Json.getString(Json.parse("{}"), "fallback", "nope").equals("fallback"));
    }

    private static void checkDiff() {
        check("identical text has no changes", Diff.countChanges("a\nb", "a\nb")[0] == 0);
        int[] inserted = Diff.countChanges("a\nb\nc", "a\nNEW\nb\nc");
        check("an insertion is one addition and no removals",
                inserted[0] == 1 && inserted[1] == 0);
        int[] changed = Diff.countChanges("a\nb\nc", "a\nCHANGED\nc");
        check("a modification is one addition and one removal",
                changed[0] == 1 && changed[1] == 1);
        check("the unified view uses standard markers",
                Diff.renderUnified("a\nb", "a\nc").contains("- b")
                        && Diff.renderUnified("a\nb", "a\nc").contains("+ c"));
        check("the side-by-side view has both headers",
                Diff.renderSideBySide("a", "b", 40).contains("ORIGINAL")
                        && Diff.renderSideBySide("a", "b", 40).contains("SUGGESTED"));
    }

    private static void checkDelta() {
        ReviewReport before = new ReviewReport();
        before.addItems(List.of(
                new ReviewItem("/A.java", 10, Severity.CRITICAL, "SQL injection", "", "",
                        "Security", "security"),
                new ReviewItem("/A.java", 20, Severity.LOW, "Naming", "", "", "Style", "style")));

        ReviewReport after = new ReviewReport();
        after.addItems(List.of(
                new ReviewItem("/A.java", 57, Severity.LOW, "Naming", "", "", "Style", "style"),
                new ReviewItem("/B.java", 5, Severity.HIGH, "Resource leak", "", "",
                        "Security", "security")));

        DeltaReport delta = new DeltaReport(before.snapshot("v1", "aaa"), after.snapshot("v2", "bbb"));
        check("resolved findings are identified", delta.getResolved().size() == 1
                && delta.getResolved().get(0).getTitle().equals("SQL injection"));
        check("new findings are identified", delta.getIntroduced().size() == 1
                && delta.getIntroduced().get(0).getTitle().equals("Resource leak"));
        check("a finding that moved lines still counts as persisting",
                delta.getPersisting().size() == 1);
        check("net change is computed", delta.getNetChange() == 0);
        check("the delta renders all three sections",
                delta.render().contains("RESOLVED")
                        && delta.render().contains("NEWLY INTRODUCED")
                        && delta.render().contains("STILL PRESENT"));
    }

    private static void checkExporters() throws Exception {
        ReviewReport report = new ReviewReport();
        report.setRepositoryPath("/project");
        ReviewItem finding = new ReviewItem("/project/src/A.java", 12, Severity.CRITICAL,
                "SQL injection via <concatenation>", "Unsafe & direct.", "Use parameters.",
                "Security", "security");
        report.addItem(finding);
        finding.updateStatus(ReviewStatus.REJECTED, "handled by the ORM layer");

        for (String format : ExporterFactory.supportedFormats()) {
            check("factory resolves " + format, ExporterFactory.create(format).isPresent());
        }
        check("an unknown format is rejected", ExporterFactory.create("docx").isEmpty());
        check("format is inferred from a file name",
                ExporterFactory.createForFileName("r.md").orElseThrow().getExtension().equals("md"));

        Path directory = Files.createTempDirectory("selfcheck-export-");

        Path markdown = directory.resolve("r.md");
        ExporterFactory.create("md").orElseThrow().export(report, markdown);
        String markdownText = Files.readString(markdown);
        check("markdown contains the finding", markdownText.contains("SQL injection"));
        check("markdown contains the reviewer's reason",
                markdownText.contains("handled by the ORM layer"));
        check("exported paths are relative, not absolute",
                markdownText.contains("src/A.java") && !markdownText.contains("/project/src/A.java"));

        Path html = directory.resolve("r.html");
        ExporterFactory.create("html").orElseThrow().export(report, html);
        String htmlText = Files.readString(html);
        check("html is well formed",
                htmlText.startsWith("<!DOCTYPE html>") && htmlText.trim().endsWith("</html>"));
        check("html escapes markup in findings",
                htmlText.contains("&lt;concatenation&gt;") && !htmlText.contains("<concatenation>"));

        Path json = directory.resolve("r.json");
        ExporterFactory.create("json").orElseThrow().export(report, json);
        Object parsed = Json.parse(Files.readString(json));
        check("json carries the schema version",
                Json.getString(parsed, "", "schemaVersion").equals("2.0"));
        check("json carries the totals", Json.getInt(parsed, 0, "summary", "total") == 1);

        Path pdf = directory.resolve("r.pdf");
        ExporterFactory.create("pdf").orElseThrow().export(report, pdf);
        byte[] bytes = Files.readAllBytes(pdf);
        String pdfText = new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1);
        check("pdf has the correct header", pdfText.startsWith("%PDF-1.4"));
        check("pdf has a catalogue and page tree",
                pdfText.contains("/Type /Catalog") && pdfText.contains("/Type /Pages"));
        check("pdf has a cross-reference table and trailer",
                pdfText.contains("xref") && pdfText.trim().endsWith("%%EOF"));
        check("pdf is not a stub", bytes.length > 800);

        ReviewReport empty = new ReviewReport();
        Path emptyReport = directory.resolve("empty.md");
        ExporterFactory.create("md").orElseThrow().export(empty, emptyReport);
        check("an empty report still exports",
                Files.readString(emptyReport).contains("No findings were recorded"));
    }

    private static void checkEndToEnd(String repository) throws Exception {
        Path memory = Path.of(repository, ".agent-memory", "memory.json");
        Files.deleteIfExists(memory);

        // Deliberately run against the built-in offline analyser rather than whatever
        // provider happens to be configured. A verification harness has to be
        // reproducible: if it called a live API it would fail whenever that service was
        // rate limiting, offline or simply slow, which says nothing about this code. It
        // would also spend the user's quota every time they checked their build.
        System.setProperty("LLM_PROVIDER", "mock");
        com.eecs3311.codereview.llm.LLMClient.getInstance().reloadConfiguration();

        AgentController controller = new AgentController();
        FileNode root = controller.handleImport(repository, false);
        check("the repository imports", root.getTotalFiles() > 0);
        check("the retrieval index is built",
                controller.getAgent().getRetriever().getIndexedFileCount() > 0);
        check("the project .rules file is loaded",
                controller.getAgent().getStandardsChecker().getRulesSource().contains(".rules"));
        check("all five agent tools are registered",
                controller.getAgent().getTools().names().size() == 5);

        List<ReviewItem> findings = controller.requestReview(
                root.find("LoginService.java").orElseThrow().getPath(), null, "security");
        check("analysis produces findings", !findings.isEmpty());
        check("findings reach the report", controller.getReport().getTotalIssues() > 0);
        check("at least one critical finding is detected",
                findings.stream().anyMatch(f -> f.getSeverity() == Severity.CRITICAL));

        if (findings.isEmpty()) {
            // Report the remaining checks as failed rather than throwing: a stack trace
            // tells the reader nothing about which behaviours are broken.
            check("triage, memory, undo, snapshot, export and audit could be checked",
                    false);
            System.out.println();
            System.out.println("   The analysis returned nothing, so the checks that depend");
            System.out.println("   on a finding were skipped. Run this to see the reason:");
            System.out.println("     ./run.sh --cli --repo " + repository
                    + " --command \"analyze LoginService.java security\"");
            return;
        }

        ReviewItem first = findings.get(0);
        controller.rejectFinding(first.getIssueId(), "not applicable on this project");
        check("rejection records the status", first.getStatus() == ReviewStatus.REJECTED);
        check("rejection is written to agent memory",
                controller.getAgent().getMemory().size() == 1);
        check("agent memory persists to disk", Files.exists(memory));
        check("recall renders an instruction for the model",
                controller.getAgent().getMemory()
                        .renderRecall(first.getTitle(), first.getFilePath(), 5)
                        .contains("REJECTED"));

        controller.undo();
        check("undo reverses the triage", first.getStatus() == ReviewStatus.PENDING);

        int dismissed = controller.bulkRejectAtOrBelow(Severity.MEDIUM, "not tracked");
        check("bulk triage affects pending findings at or below the threshold", dismissed > 0);
        controller.undo();

        controller.takeSnapshot("before", "aaa111");
        check("a snapshot is stored", controller.getSnapshotLabels().contains("before"));

        Map<String, Integer> summary = controller.getReport().aggregateItems();
        check("aggregation reports a total", summary.get("total") > 0);

        Path output = Files.createTempDirectory("selfcheck-e2e-").resolve("review.pdf");
        Path written = controller.exportReport(output.toString(), null);
        check("the report exports to PDF", Files.size(written) > 800);

        String audit = controller.runAudit("Find the security problems here", 8, null);
        check("the autonomous audit returns a conclusion", audit != null && !audit.isBlank());
        check("the audit produced a multi-step reasoning trace",
                controller.getAgent().getAuditTrace().size() >= 4);
        check("the trace contains tool invocations",
                controller.getAgent().getAuditTrace().stream()
                        .anyMatch(step -> step.getType().name().equals("ACTION")));
        check("the audit recorded findings in the report",
                controller.getReport().getTotalIssues() > 0);

        check("retrieval finds relevant code",
                !controller.getAgent().getRetriever()
                        .retrieve("password authentication", 3, 8).isEmpty());

        check("token usage was measured",
                com.eecs3311.codereview.llm.LLMClient.getInstance()
                        .getUsageStats().getRequestCount() > 0);

        Files.deleteIfExists(memory);
    }

    // ------------------------------------------------------------------ helpers

    private static boolean hasFinding(List<ReviewItem> findings, String fragment) {
        return findings.stream().anyMatch(f -> f.getTitle().toLowerCase()
                .contains(fragment.toLowerCase()));
    }

    private static LLMRequest request(String text, String purpose) {
        return new LLMRequest(List.of(ChatMessage.user(text)), 0.2, 1024, purpose);
    }

    /** A provider stub that counts calls and can be told to fail first. */
    private static class CountingProvider implements LLMProvider {
        private final AtomicInteger calls = new AtomicInteger();
        private int failuresRemaining;
        private boolean retryable = true;

        @Override
        public LLMResponse complete(LLMRequest request) throws LLMException {
            calls.incrementAndGet();
            if (failuresRemaining > 0) {
                failuresRemaining--;
                throw new LLMException("simulated failure", retryable, 500);
            }
            return new LLMResponse("stub", "stub-model", 100, 50, 10L, false, false);
        }

        @Override
        public String name() {
            return "stub";
        }
    }

    private static void section(String title) {
        System.out.println();
        System.out.println("-- " + title);
    }

    private static void check(String description, boolean condition) {
        if (condition) {
            passed++;
            System.out.println("   PASS  " + description);
        } else {
            failed++;
            FAILURES.add(description);
            System.out.println("   FAIL  " + description);
        }
    }

    private SelfCheck() {
    }
}
