package io.github.judge4j.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EvaluationTest {

    @Test
    void acceptsScoresOnTheClosedUnitInterval() {
        assertEquals(0.0, new Evaluation(false, 0.0, null).score());
        assertEquals(1.0, new Evaluation(true, 1.0, null).score());
        assertEquals(0.37, new Evaluation(true, 0.37, "x").score());
    }

    @Test
    void rejectsScoresOutsideTheUnitInterval() {
        assertThrows(IllegalArgumentException.class, () -> new Evaluation(true, 1.0001, "x"));
        assertThrows(IllegalArgumentException.class, () -> new Evaluation(false, -0.0001, "x"));
        assertThrows(IllegalArgumentException.class, () -> new Evaluation(true, Double.POSITIVE_INFINITY, "x"));
    }

    @Test
    void rejectsNaN() {
        assertThrows(IllegalArgumentException.class, () -> new Evaluation(false, Double.NaN, "x"));
    }

    @Test
    void negateInvertsPassScoreAndFeedback() {
        Evaluator<String> evaluator = actual -> new Evaluation(true, 0.25, "missing required text");

        Evaluation negated = evaluator.negate().evaluate("anything");

        assertFalse(negated.pass());
        assertEquals(0.75, negated.score(), 1e-12);
        assertEquals("Negated: the original check passed - missing required text", negated.feedback());
    }

    @Test
    void negateTwiceRestoresTheOriginalVerdict() {
        Evaluator<String> evaluator = actual -> new Evaluation(false, 0.0, "nope");

        Evaluation twice = evaluator.negate().negate().evaluate("x");

        assertFalse(twice.pass());
        assertEquals(0.0, twice.score(), 1e-12);
    }

    @Test
    void negateToleratesMissingFeedback() {
        Evaluator<String> evaluator = actual -> new Evaluation(false, 0.0, null);

        assertEquals("Negated: the original check failed", evaluator.negate().evaluate("x").feedback());
    }

    @Test
    void negateOfAFailureIsAPass() {
        Evaluator<String> evaluator = actual -> new Evaluation(false, 0.0, "fail");

        assertTrue(evaluator.negate().evaluate("x").pass());
        assertEquals(1.0, evaluator.negate().evaluate("x").score(), 1e-12);
    }
}
