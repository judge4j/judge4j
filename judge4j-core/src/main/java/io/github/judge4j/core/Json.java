package io.github.judge4j.core;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Shared JSON configuration for everything Judge4j writes or reads. */
final class Json {

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private Json() {
    }

    static ObjectMapper mapper() {
        return MAPPER;
    }

    /**
     * Returns a copy of {@code run} in which every arbitrary object (outputs, inputs, assertion
     * inputs) that Jackson cannot serialize has been replaced by its {@code toString()}, so one
     * unserializable value can never cost the whole report.
     */
    static RunResult sanitize(RunResult run) {
        List<EvalResult> results = run.results().stream().map(Json::sanitize).toList();
        return new RunResult(run.runId(), run.label(), run.timestamp(), results);
    }

    private static EvalResult sanitize(EvalResult result) {
        Map<String, Object> inputs = new LinkedHashMap<>();
        result.inputs().forEach((key, value) -> inputs.put(key, safe(value)));
        List<AssertionResult> assertions = result.assertions().stream()
                .map(a -> new AssertionResult(a.label(), a.expectation(), safe(a.input()), a.weight(), a.pass(),
                        a.score(), a.feedback()))
                .toList();
        return new EvalResult(result.testCaseId(), result.testCaseDescription(), inputs, result.variantLabel(),
                safe(result.output()), result.score(), result.pass(), result.latencyMs(), result.error(), assertions);
    }

    static Object safe(Object value) {
        if (value == null || value instanceof CharSequence || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        try {
            return MAPPER.valueToTree(value);
        } catch (Exception | StackOverflowError e) {
            try {
                return String.valueOf(value);
            } catch (Exception | StackOverflowError inner) {
                return "<unprintable " + value.getClass().getName() + ">";
            }
        }
    }
}
