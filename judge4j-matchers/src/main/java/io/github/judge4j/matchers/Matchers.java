package io.github.judge4j.matchers;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.judge4j.core.Evaluation;
import io.github.judge4j.core.Evaluator;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Ready-made deterministic {@link Evaluator}s for plain strings and JSON, so common checks need no
 * hand-written evaluator. Each returns score {@code 1.0} on a pass and {@code 0.0} on a fail, with
 * feedback naming the check.
 *
 * <p>Conventions shared by all matchers:
 * <ul>
 *   <li>A {@code null} actual value never satisfies a matcher (it fails with "actual was null").
 *       Combined with {@link Evaluator#negate()}, a {@code null} therefore <em>passes</em> the
 *       negated check.</li>
 *   <li>Arguments are validated when the matcher is created, so a mistake surfaces at the call
 *       site rather than during an evaluation run: {@code null} arguments and invalid regular
 *       expressions or JSON throw immediately.</li>
 *   <li>Use {@code .negate()} for the "not" case, e.g. {@code Matchers.contains("x").negate()}.</li>
 * </ul>
 */
public final class Matchers {

    /** Strict: a document followed by anything but whitespace is not valid JSON. */
    private static final ObjectMapper STRICT_JSON =
            new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    /** Lenient about what follows the first JSON value, used to find JSON embedded in prose. */
    private static final ObjectMapper PREFIX_JSON = new ObjectMapper();

    /** {@link #containsJson()} tries at most this many '{' / '[' positions before giving up. */
    private static final int MAX_JSON_CANDIDATES = 1000;

    private Matchers() {
    }

    /** Passes iff the actual string equals {@code expected} exactly (case-sensitive). */
    public static Evaluator<String> equals(String expected) {
        Objects.requireNonNull(expected, "expected must not be null");
        return check("equals \"%s\"".formatted(expected), actual -> actual.equals(expected));
    }

    /** Passes iff the actual string equals {@code expected}, ignoring case. */
    public static Evaluator<String> equalsIgnoreCase(String expected) {
        Objects.requireNonNull(expected, "expected must not be null");
        return check("equalsIgnoreCase \"%s\"".formatted(expected), actual -> actual.equalsIgnoreCase(expected));
    }

    /** Passes iff the actual string contains {@code substring} (case-sensitive). */
    public static Evaluator<String> contains(String substring) {
        Objects.requireNonNull(substring, "substring must not be null");
        return check("contains \"%s\"".formatted(substring), actual -> actual.contains(substring));
    }

    /** Passes iff the actual string contains {@code substring}, ignoring case. */
    public static Evaluator<String> icontains(String substring) {
        Objects.requireNonNull(substring, "substring must not be null");
        String lowered = substring.toLowerCase();
        return check("icontains \"%s\"".formatted(substring), actual -> actual.toLowerCase().contains(lowered));
    }

    /** Passes iff the actual string contains at least one of {@code substrings} (case-sensitive). */
    public static Evaluator<String> containsAny(String... substrings) {
        String[] terms = terms(substrings);
        return check("containsAny %s".formatted(Arrays.toString(terms)),
                actual -> Arrays.stream(terms).anyMatch(actual::contains));
    }

    /** Passes iff the actual string contains at least one of {@code substrings}, ignoring case. */
    public static Evaluator<String> icontainsAny(String... substrings) {
        String[] terms = terms(substrings);
        String[] lowered = Arrays.stream(terms).map(String::toLowerCase).toArray(String[]::new);
        return check("icontainsAny %s".formatted(Arrays.toString(terms)), actual -> {
            String text = actual.toLowerCase();
            return Arrays.stream(lowered).anyMatch(text::contains);
        });
    }

    /** Passes iff the actual string contains every one of {@code substrings} (case-sensitive). */
    public static Evaluator<String> containsAll(String... substrings) {
        String[] terms = terms(substrings);
        return check("containsAll %s".formatted(Arrays.toString(terms)),
                actual -> Arrays.stream(terms).allMatch(actual::contains));
    }

    /** Passes iff the actual string starts with {@code prefix}. */
    public static Evaluator<String> startsWith(String prefix) {
        Objects.requireNonNull(prefix, "prefix must not be null");
        return check("startsWith \"%s\"".formatted(prefix), actual -> actual.startsWith(prefix));
    }

    /**
     * Passes iff the regular expression matches somewhere in the actual string (use {@code ^...$}
     * to require the whole string).
     *
     * @throws java.util.regex.PatternSyntaxException if {@code pattern} is not a valid regular expression
     */
    public static Evaluator<String> regex(String pattern) {
        Objects.requireNonNull(pattern, "pattern must not be null");
        Pattern compiled = Pattern.compile(pattern);
        return check("regex \"%s\"".formatted(pattern), actual -> compiled.matcher(actual).find());
    }

    /**
     * Passes iff the actual string is exactly one valid JSON value (object, array or scalar) with
     * nothing but whitespace around it. Empty and blank strings fail.
     */
    public static Evaluator<String> isJson() {
        return actual -> {
            if (actual == null) {
                return result(false, "isJson (actual was null)");
            }
            try {
                return result(parseStrict(actual) != null, "isJson");
            } catch (Exception e) {
                return result(false, "isJson: " + firstLine(e));
            }
        };
    }

    /**
     * Passes iff the actual string contains a valid JSON object or array anywhere inside it,
     * e.g. in prose or a markdown code fence. Scans at most the first 1000 positions holding an
     * opening brace or bracket.
     */
    public static Evaluator<String> containsJson() {
        return check("containsJson", Matchers::containsJsonContainer);
    }

    /**
     * Passes iff the actual string is JSON structurally equal to {@code expectedJson}; key order
     * and whitespace do not matter.
     *
     * @throws IllegalArgumentException if {@code expectedJson} is not valid JSON
     */
    public static Evaluator<String> jsonEquals(String expectedJson) {
        Objects.requireNonNull(expectedJson, "expectedJson must not be null");
        JsonNode expected;
        try {
            expected = parseStrict(expectedJson);
        } catch (Exception e) {
            throw new IllegalArgumentException("expectedJson is not valid JSON: " + firstLine(e), e);
        }
        return actual -> {
            if (actual == null) {
                return result(false, "jsonEquals (actual was null)");
            }
            try {
                return result(expected.equals(parseStrict(actual)), "jsonEquals " + expectedJson);
            } catch (Exception e) {
                return result(false, "jsonEquals: actual is not valid JSON: " + firstLine(e));
            }
        };
    }

    private static Evaluator<String> check(String description, Predicate<String> predicate) {
        return actual -> actual == null
                ? result(false, description + " (actual was null)")
                : result(predicate.test(actual), description);
    }

    private static String[] terms(String[] substrings) {
        Objects.requireNonNull(substrings, "substrings must not be null");
        for (String substring : substrings) {
            Objects.requireNonNull(substring, "substrings must not contain null");
        }
        return substrings.clone();
    }

    /** Parses exactly one JSON value; returns {@code null} for empty or blank input. */
    private static JsonNode parseStrict(String text) throws Exception {
        if (text.isBlank()) {
            return null;
        }
        return STRICT_JSON.readTree(text);
    }

    private static boolean containsJsonContainer(String text) {
        int attempts = 0;
        for (int i = 0; i < text.length() && attempts < MAX_JSON_CANDIDATES; i++) {
            char c = text.charAt(i);
            if (c != '{' && c != '[') {
                continue;
            }
            attempts++;
            try {
                JsonNode node = PREFIX_JSON.readTree(text.substring(i));
                if (node != null && node.isContainerNode()) {
                    return true;
                }
            } catch (Exception ignored) {
                // not JSON starting here; try the next candidate
            }
        }
        return false;
    }

    private static String firstLine(Exception e) {
        String message = e.getMessage();
        if (message == null) {
            return e.getClass().getSimpleName();
        }
        int newline = message.indexOf('\n');
        return newline < 0 ? message : message.substring(0, newline);
    }

    private static Evaluation result(boolean pass, String description) {
        return new Evaluation(pass, pass ? 1.0 : 0.0, "%s: %s".formatted(description, pass ? "PASS" : "FAIL"));
    }
}
