package com.eecs3311.codereview.agent.strategy;

import com.eecs3311.codereview.json.Json;
import com.eecs3311.codereview.llm.LLMClient;
import com.eecs3311.codereview.llm.LLMException;
import com.eecs3311.codereview.llm.PromptBuilder;
import com.eecs3311.codereview.model.ReviewItem;
import com.eecs3311.codereview.model.Severity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * TEMPLATE METHOD PATTERN - defines the skeleton of an analysis run.
 *
 * <p>Every analysis performs the same seven steps in the same order:</p>
 * <ol>
 *   <li>reject empty input,</li>
 *   <li>run any deterministic pre-pass,</li>
 *   <li>assemble the prompt,</li>
 *   <li>call the model through the Singleton client,</li>
 *   <li>parse the structured reply into findings,</li>
 *   <li>validate and clamp the findings against the real file,</li>
 *   <li>apply strategy-specific post-processing.</li>
 * </ol>
 *
 * <p>{@link #executeStrategy} is {@code final} so that order cannot be broken by a subclass.
 * What varies between strategies - the role given to the model, the body of the prompt, the
 * deterministic pre-pass, the severity floor and ceiling - is expressed as abstract methods
 * and hooks. Without this base class, steps 1, 4, 5 and 6 would be copied into every
 * strategy, and a fix to the JSON-repair logic in step 5 would have to be made four times.</p>
 *
 * <p>Relationship to the Strategy pattern: Strategy decides <em>which</em> analysis runs,
 * Template Method decides <em>how every</em> analysis runs. They sit on the same classes but
 * solve different problems, which is why both are counted separately in the report.</p>
 */
public abstract class AbstractReviewStrategy implements ReviewStrategy {

    /** The shared JSON schema every finding-producing strategy asks the model to follow. */
    protected static final String FINDINGS_SCHEMA =
            "Reply with JSON only, no prose and no markdown fence, in exactly this shape:\n"
            + "{\"findings\":[{\"line\":<integer line number>,"
            + "\"severity\":\"CRITICAL|HIGH|MEDIUM|LOW|INFO\","
            + "\"category\":\"<short category>\","
            + "\"title\":\"<one line, under 90 characters>\","
            + "\"description\":\"<why this is a problem, 1-3 sentences>\","
            + "\"suggestion\":\"<the concrete fix, 1-2 sentences>\"}]}\n"
            + "If there is nothing worth reporting, reply with {\"findings\":[]}.";

    /**
     * TEMPLATE METHOD. Final by design: subclasses customise the steps, never their order.
     */
    @Override
    public final List<ReviewItem> executeStrategy(AnalysisContext context) throws LLMException {
        if (context == null || context.isEmpty()) {
            return new ArrayList<>();
        }

        // Step 2 - deterministic pre-pass (hook; most strategies do nothing here).
        List<ReviewItem> findings = new ArrayList<>(runDeterministicPass(context));

        // Step 3 - assemble the prompt from the varying parts.
        PromptBuilder builder = new PromptBuilder()
                .role(getRoleInstruction())
                .conventions(context.getConventions())
                .memory(context.getMemoryNotes())
                .retrievedContext(context.getRetrievedContext())
                .language(context.getLanguage())
                .code(context.getFilePath(), context.getCode())
                .outputSchema(FINDINGS_SCHEMA);
        decoratePrompt(builder, context);

        // Step 4 - one call, through the single shared client.
        String raw = LLMClient.getInstance().sendPrompt(
                builder.buildSystemPrompt(), builder.build(), getPurpose());

        // Step 5 and 6 - parse, then validate against the real file.
        findings.addAll(validate(parseFindings(raw, context), context));

        // Step 7 - strategy-specific post-processing.
        return postProcess(findings, context);
    }

    // ------------------------------------------------------------------ abstract steps

    /** The role instruction placed in the system message. */
    protected abstract String getRoleInstruction();

    /** What this strategy asks the model to look for; appended as closing instructions. */
    protected abstract void decoratePrompt(PromptBuilder builder, AnalysisContext context);

    /** Token-accounting label, for example {@code "security-analysis"}. */
    protected abstract String getPurpose();

    // ------------------------------------------------------------------ hooks

    /**
     * Hook: findings produced without calling the model. Overridden by
     * {@link StyleStrategy}, which runs the deterministic rule engine first.
     */
    protected List<ReviewItem> runDeterministicPass(AnalysisContext context) {
        return new ArrayList<>();
    }

    /** Hook: the most severe level this strategy may assign. */
    protected Severity getSeverityCeiling() {
        return Severity.CRITICAL;
    }

    /** Hook: final filtering or re-ranking. The default returns the list unchanged. */
    protected List<ReviewItem> postProcess(List<ReviewItem> findings, AnalysisContext context) {
        return findings;
    }

    // ------------------------------------------------------------------ shared steps

    /**
     * Parses the model's reply into findings. Shared by every strategy, which is the main
     * reason this base class exists: models return JSON inconsistently - wrapped in prose,
     * fenced, or as a bare array - and all of that tolerance lives here, once.
     */
    protected List<ReviewItem> parseFindings(String raw, AnalysisContext context) {
        List<ReviewItem> findings = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return findings;
        }
        List<Object> entries = Json.parseArray(raw);
        for (Object entry : entries) {
            if (!(entry instanceof Map)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) entry;

            String title = Json.getString(map, "", "title");
            if (title.isBlank()) {
                title = Json.getString(map, "", "issue");
            }
            if (title.isBlank()) {
                continue; // an entry with no headline is not a usable finding
            }
            findings.add(new ReviewItem(
                    context.getFilePath(),
                    Json.getInt(map, 0, "line"),
                    Severity.fromText(Json.getString(map, "MEDIUM", "severity")),
                    title,
                    Json.getString(map, "", "description"),
                    Json.getString(map, "", "suggestion"),
                    Json.getString(map, getName(), "category"),
                    getName()));
        }
        return findings;
    }

    /**
     * Guards against the two ways a model's output can be wrong in a way that matters:
     * a line number outside the file, and a severity above what this strategy is allowed
     * to assign. Both are corrected rather than discarded, because the finding itself may
     * still be valid.
     */
    protected List<ReviewItem> validate(List<ReviewItem> findings, AnalysisContext context) {
        int lineCount = context.getLineCount();
        Severity ceiling = getSeverityCeiling();
        List<ReviewItem> validated = new ArrayList<>();
        for (ReviewItem finding : findings) {
            if (finding.getLineNumber() > lineCount) {
                // Re-create at line 0 (file-wide) rather than pointing at a line that is not there.
                ReviewItem corrected = new ReviewItem(finding.getFilePath(), 0,
                        finding.getSeverity(), finding.getTitle(), finding.getDescription(),
                        finding.getSuggestion(), finding.getCategory(), finding.getProducedBy());
                finding = corrected;
            }
            if (finding.getSeverity().getRank() < ceiling.getRank()) {
                finding.setSeverity(ceiling);
            }
            validated.add(finding);
        }
        return validated;
    }
}
