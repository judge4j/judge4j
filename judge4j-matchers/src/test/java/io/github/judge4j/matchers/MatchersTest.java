package io.github.judge4j.matchers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.judge4j.core.Evaluation;
import org.junit.jupiter.api.Test;

class MatchersTest {

    @Test
    void equalsPassesOnExactMatch() {
        assertTrue(Matchers.equals("hello").evaluate("hello").pass());
        assertFalse(Matchers.equals("hello").evaluate("Hello").pass());
    }

    @Test
    void equalsIgnoreCaseIgnoresCase() {
        assertTrue(Matchers.equalsIgnoreCase("hello").evaluate("HELLO").pass());
    }

    @Test
    void containsPassesWhenSubstringPresent() {
        assertTrue(Matchers.contains("world").evaluate("hello world").pass());
    }

    @Test
    void containsFailsWhenSubstringAbsent() {
        assertFalse(Matchers.contains("world").evaluate("hello there").pass());
    }

    @Test
    void icontainsIgnoresCase() {
        assertTrue(Matchers.icontains("WORLD").evaluate("hello world").pass());
    }

    @Test
    void containsAnyPassesOnFirstMatch() {
        assertTrue(Matchers.containsAny("foo", "world").evaluate("hello world").pass());
    }

    @Test
    void containsAnyFailsWhenNoneMatch() {
        assertFalse(Matchers.containsAny("foo", "bar").evaluate("hello world").pass());
    }

    @Test
    void icontainsAnyIgnoresCase() {
        assertTrue(Matchers.icontainsAny("FOO", "World").evaluate("hello world").pass());
        assertFalse(Matchers.icontainsAny("foo", "bar").evaluate("hello world").pass());
    }

    @Test
    void containsAllPassesWhenEveryTermPresent() {
        assertTrue(Matchers.containsAll("hello", "world").evaluate("hello world").pass());
    }

    @Test
    void containsAllFailsWhenOneMissing() {
        assertFalse(Matchers.containsAll("hello", "there").evaluate("hello world").pass());
    }

    @Test
    void startsWithChecksPrefixOnly() {
        assertTrue(Matchers.startsWith("hello").evaluate("hello world").pass());
        assertFalse(Matchers.startsWith("world").evaluate("hello world").pass());
    }

    @Test
    void regexMatchesPattern() {
        assertTrue(Matchers.regex("\\d{3}-\\d{4}").evaluate("call 555-1234 now").pass());
        assertFalse(Matchers.regex("\\d{3}-\\d{4}").evaluate("no numbers here").pass());
    }

    @Test
    void isJsonPassesOnValidObject() {
        assertTrue(Matchers.isJson().evaluate("{\"a\": 1}").pass());
    }

    @Test
    void isJsonFailsOnMalformedInput() {
        assertFalse(Matchers.isJson().evaluate("not json").pass());
    }

    @Test
    void containsJsonFindsEmbeddedObject() {
        assertTrue(Matchers.containsJson().evaluate("here is the result: {\"a\": 1} thanks").pass());
    }

    @Test
    void containsJsonFailsWhenNoJsonPresent() {
        assertFalse(Matchers.containsJson().evaluate("no json here").pass());
    }

    @Test
    void jsonEqualsIgnoresKeyOrderAndWhitespace() {
        assertTrue(Matchers.jsonEquals("{\"a\": 1, \"b\": 2}").evaluate("{ \"b\":2,\"a\":1 }").pass());
    }

    @Test
    void jsonEqualsFailsOnDifferentValues() {
        assertFalse(Matchers.jsonEquals("{\"a\": 1}").evaluate("{\"a\": 2}").pass());
    }

    @Test
    void nullActualNeverSatisfiesAPositiveMatcher() {
        Evaluation evaluation = Matchers.contains("x").evaluate(null);
        assertFalse(evaluation.pass());
    }
}
