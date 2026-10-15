package io.github.judge4j.matchers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.judge4j.core.Evaluation;
import io.github.judge4j.core.Evaluator;
import java.util.List;
import java.util.regex.PatternSyntaxException;
import org.junit.jupiter.api.Test;

class MatchersEdgeCasesTest {

    // --- null handling -------------------------------------------------------------------------

    @Test
    void everyPositiveMatcherFailsOnNullAndSaysSo() {
        List<Evaluator<String>> all = List.of(
                Matchers.equals(""), Matchers.equalsIgnoreCase(""), Matchers.contains(""), Matchers.icontains(""),
                Matchers.containsAny(""), Matchers.icontainsAny(""), Matchers.containsAll(""),
                Matchers.startsWith(""), Matchers.regex(".*"), Matchers.isJson(), Matchers.containsJson(),
                Matchers.jsonEquals("{}"));

        for (Evaluator<String> matcher : all) {
            Evaluation evaluation = matcher.evaluate(null);
            assertFalse(evaluation.pass(), evaluation.feedback());
            assertEquals(0.0, evaluation.score());
            assertTrue(evaluation.feedback().contains("actual was null"), evaluation.feedback());
        }
    }

    @Test
    void equalsEmptyStringDoesNotAcceptNull() {
        assertTrue(Matchers.equals("").evaluate("").pass());
        assertFalse(Matchers.equals("").evaluate(null).pass());
    }

    @Test
    void negatedMatcherPassesOnNull() {
        assertTrue(Matchers.contains("x").negate().evaluate(null).pass());
    }

    @Test
    void nullArgumentsFailAtCreationNotDuringEvaluation() {
        assertThrows(NullPointerException.class, () -> Matchers.equals(null));
        assertThrows(NullPointerException.class, () -> Matchers.contains(null));
        assertThrows(NullPointerException.class, () -> Matchers.icontains(null));
        assertThrows(NullPointerException.class, () -> Matchers.startsWith(null));
        assertThrows(NullPointerException.class, () -> Matchers.regex(null));
        assertThrows(NullPointerException.class, () -> Matchers.jsonEquals(null));
        assertThrows(NullPointerException.class, () -> Matchers.containsAny((String[]) null));
        assertThrows(NullPointerException.class, () -> Matchers.containsAll("a", null));
    }

    @Test
    void invalidRegexFailsAtCreation() {
        assertThrows(PatternSyntaxException.class, () -> Matchers.regex("("));
    }

    @Test
    void invalidExpectedJsonFailsAtCreation() {
        assertThrows(IllegalArgumentException.class, () -> Matchers.jsonEquals("{not json"));
    }

    // --- term lists ----------------------------------------------------------------------------

    @Test
    void emptyTermListsBehaveAsVacuousLogic() {
        assertFalse(Matchers.containsAny().evaluate("anything").pass());
        assertTrue(Matchers.containsAll().evaluate("anything").pass());
    }

    @Test
    void matchersDoNotSeeLaterChangesToTheTermArray() {
        String[] terms = {"alpha"};
        Evaluator<String> matcher = Matchers.containsAny(terms);
        terms[0] = "omega";

        assertTrue(matcher.evaluate("alpha").pass());
        assertFalse(matcher.evaluate("omega").pass());
    }

    @Test
    void caseInsensitiveMatchersHandleMixedCaseTerms() {
        assertTrue(Matchers.icontains("StRiNg").evaluate("a STRING here").pass());
        assertTrue(Matchers.icontainsAny("NOPE", "HeRe").evaluate("a string here").pass());
    }

    @Test
    void regexMatchesAnywhereUnlessAnchored() {
        assertTrue(Matchers.regex("b+").evaluate("aabbcc").pass());
        assertFalse(Matchers.regex("^b+$").evaluate("aabbcc").pass());
        assertTrue(Matchers.regex("(?m)^- ").evaluate("intro\n- bullet").pass());
    }

    // --- isJson --------------------------------------------------------------------------------

    @Test
    void isJsonRejectsEmptyAndBlankInput() {
        assertFalse(Matchers.isJson().evaluate("").pass());
        assertFalse(Matchers.isJson().evaluate("   \n ").pass());
    }

    @Test
    void isJsonRejectsTrailingGarbage() {
        assertFalse(Matchers.isJson().evaluate("{\"a\":1} and then some prose").pass());
        assertFalse(Matchers.isJson().evaluate("{\"a\":1}{\"b\":2}").pass());
    }

    @Test
    void isJsonAcceptsArraysScalarsAndSurroundingWhitespace() {
        assertTrue(Matchers.isJson().evaluate("[1, 2, 3]").pass());
        assertTrue(Matchers.isJson().evaluate("  {\"a\": [true, null]}\n").pass());
        assertTrue(Matchers.isJson().evaluate("42").pass());
        assertTrue(Matchers.isJson().evaluate("\"text\"").pass());
    }

    @Test
    void isJsonRejectsMarkdownFencesAndProse() {
        assertFalse(Matchers.isJson().evaluate("```json\n{\"a\":1}\n```").pass());
        assertFalse(Matchers.isJson().evaluate("Sure! {\"a\":1}").pass());
    }

    // --- containsJson --------------------------------------------------------------------------

    @Test
    void containsJsonFindsJsonInsideAMarkdownFence() {
        assertTrue(Matchers.containsJson().evaluate("Here you go:\n```json\n{\"a\": [1,2]}\n```\nEnjoy").pass());
    }

    @Test
    void containsJsonSkipsAnEarlierBracketThatIsNotJson() {
        assertTrue(Matchers.containsJson().evaluate("[note] the payload is {\"ok\": true}").pass());
        assertTrue(Matchers.containsJson().evaluate("{not json} but then [1, 2]").pass());
    }

    @Test
    void containsJsonIsNotFooledByBracesInsideStrings() {
        assertTrue(Matchers.containsJson().evaluate("x {\"a\": \"}\"} y").pass());
        assertTrue(Matchers.containsJson().evaluate("x {\"a\": \"{[\"} y").pass());
    }

    @Test
    void containsJsonRejectsScalarsAndUnclosedStructures() {
        assertFalse(Matchers.containsJson().evaluate("the answer is 42").pass());
        assertFalse(Matchers.containsJson().evaluate("start {\"a\": 1").pass());
        assertFalse(Matchers.containsJson().evaluate("").pass());
    }

    @Test
    void containsJsonStaysFastOnHostileInput() {
        String hostile = "{".repeat(50_000);

        long start = System.nanoTime();
        boolean pass = Matchers.containsJson().evaluate(hostile).pass();
        long millis = (System.nanoTime() - start) / 1_000_000;

        assertFalse(pass);
        assertTrue(millis < 5_000, "took " + millis + " ms");
    }

    // --- jsonEquals ----------------------------------------------------------------------------

    @Test
    void jsonEqualsComparesStructurallyIncludingNesting() {
        Evaluator<String> matcher = Matchers.jsonEquals("{\"a\": {\"b\": [1, 2]}, \"c\": null}");

        assertTrue(matcher.evaluate("{\"c\":null,\"a\":{\"b\":[1,2]}}").pass());
        assertFalse(matcher.evaluate("{\"c\":null,\"a\":{\"b\":[2,1]}}").pass());
    }

    @Test
    void jsonEqualsFailsWithAMessageWhenActualIsNotJson() {
        Evaluation evaluation = Matchers.jsonEquals("{}").evaluate("definitely not json");

        assertFalse(evaluation.pass());
        assertTrue(evaluation.feedback().contains("not valid JSON"), evaluation.feedback());
    }

    @Test
    void jsonEqualsRejectsTrailingGarbageAndEmptyActual() {
        assertFalse(Matchers.jsonEquals("{}").evaluate("{} trailing").pass());
        assertFalse(Matchers.jsonEquals("{}").evaluate("").pass());
    }

    // --- scoring contract ----------------------------------------------------------------------

    @Test
    void scoresAreExactlyOneOrZeroAndFeedbackNamesTheOutcome() {
        Evaluation pass = Matchers.contains("a").evaluate("a");
        Evaluation fail = Matchers.contains("a").evaluate("b");

        assertEquals(1.0, pass.score());
        assertEquals(0.0, fail.score());
        assertTrue(pass.feedback().endsWith("PASS"));
        assertTrue(fail.feedback().endsWith("FAIL"));
        assertTrue(pass.feedback().contains("contains \"a\""));
    }
}
