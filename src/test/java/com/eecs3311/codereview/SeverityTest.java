package com.eecs3311.codereview;

import com.eecs3311.codereview.model.Severity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests how model output is mapped onto the fixed {@link Severity} scale.
 *
 * <p>This matters because the severity in a finding does not come from our code - it comes
 * back as free text from a language model, which is not bound by our enum. One model writes
 * "CRITICAL", another writes "high", a third writes "Major" or "blocker". If any of those
 * caused an exception, a single oddly-worded finding would abort an entire analysis run.</p>
 *
 * <p>{@code fromText} therefore never throws: anything it cannot place becomes MEDIUM. These
 * tests pin that behaviour down so a future change cannot quietly reintroduce the crash.</p>
 */
class SeverityTest {

    @Test
    void exactNamesAreRecognised() {
        assertEquals(Severity.CRITICAL, Severity.fromText("CRITICAL"));
        assertEquals(Severity.HIGH, Severity.fromText("HIGH"));
        assertEquals(Severity.MEDIUM, Severity.fromText("MEDIUM"));
        assertEquals(Severity.LOW, Severity.fromText("LOW"));
        assertEquals(Severity.INFO, Severity.fromText("INFO"));
    }

    @Test
    void capitalisationDoesNotMatter() {
        assertEquals(Severity.CRITICAL, Severity.fromText("critical"));
        assertEquals(Severity.HIGH, Severity.fromText("High"));
        assertEquals(Severity.LOW, Severity.fromText("lOw"));
    }

    @Test
    void surroundingWhitespaceIsIgnored() {
        assertEquals(Severity.HIGH, Severity.fromText("  HIGH  "));
        assertEquals(Severity.CRITICAL, Severity.fromText("\nCRITICAL\t"));
    }

    @Test
    void commonSynonymsAreMappedRatherThanRejected() {
        // Wording seen from real models that is not one of our five names.
        assertEquals(Severity.CRITICAL, Severity.fromText("blocker"));
        assertEquals(Severity.CRITICAL, Severity.fromText("SEVERE"));
        assertEquals(Severity.HIGH, Severity.fromText("major"));
        assertEquals(Severity.HIGH, Severity.fromText("bug"));
        assertEquals(Severity.MEDIUM, Severity.fromText("warning"));
        assertEquals(Severity.LOW, Severity.fromText("minor"));
        assertEquals(Severity.LOW, Severity.fromText("nit"));
        assertEquals(Severity.INFO, Severity.fromText("suggestion"));
    }

    @Test
    void unrecognisedTextBecomesMediumInsteadOfThrowing() {
        assertEquals(Severity.MEDIUM, Severity.fromText("banana"));
        assertEquals(Severity.MEDIUM, Severity.fromText(""));
        assertEquals(Severity.MEDIUM, Severity.fromText("   "));
    }

    @Test
    void nullBecomesMediumInsteadOfThrowing() {
        // A model that omits the field entirely must not crash the run.
        assertEquals(Severity.MEDIUM, Severity.fromText(null));
    }

    @Test
    void rankOrdersMostSevereFirst() {
        // The findings table sorts on this, so the order has to be right.
        assertEquals(0, Severity.CRITICAL.getRank());
        assertEquals(4, Severity.INFO.getRank());
        assertEquals(true, Severity.CRITICAL.getRank() < Severity.HIGH.getRank());
        assertEquals(true, Severity.HIGH.getRank() < Severity.MEDIUM.getRank());
        assertEquals(true, Severity.MEDIUM.getRank() < Severity.LOW.getRank());
        assertEquals(true, Severity.LOW.getRank() < Severity.INFO.getRank());
    }

    @Test
    void everySeverityHasALabelAndAColour() {
        // The GUI table and the exported reports both rely on these being present.
        for (Severity severity : Severity.values()) {
            assertNotNull(severity.getLabel());
            assertNotNull(severity.getColor());
            assertNotNull(severity.getAnsiColor());
        }
    }
}
