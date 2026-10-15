package io.github.judge4j.spring;

import io.github.judge4j.core.AssertionResult;
import io.github.judge4j.core.EvalAssertion;
import io.github.judge4j.core.EvalAssertions;
import io.github.judge4j.core.EvalResult;
import io.github.judge4j.core.Evaluation;
import io.github.judge4j.core.Evaluator;
import io.github.judge4j.core.TestCase;
import io.github.judge4j.core.TestCaseLoader;
import io.github.judge4j.junit5.EvalRecorder;
import io.github.judge4j.junit5.EvalTest;
import io.github.judge4j.junit5.EvalTestCases;
import io.github.judge4j.junit5.Input;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider;
import org.junit.platform.commons.support.AnnotationSupport;
import org.springframework.core.MethodParameter;
import org.springframework.core.convert.ConversionException;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Runs each {@link EvalTest} method once per test case and variant, applies the variant to the
 * Spring context, collects assertions and records one {@link EvalResult} per run.
 *
 * <p>Registered by {@link EvalVariants}; not meant to be used directly.
 */
public final class SpringEvalTestExtension implements TestTemplateInvocationContextProvider {

    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(SpringEvalTestExtension.class);
    private static final String ACCUMULATOR_KEY = "accumulator";

    /** Total of explicit assertion weights is allowed to exceed 100 by this much (floating point slack). */
    private static final double WEIGHT_EPSILON = 1e-7;

    @Override
    public boolean supportsTestTemplate(ExtensionContext context) {
        return context.getTestMethod().filter(method -> AnnotationSupport.isAnnotated(method, EvalTest.class)).isPresent();
    }

    @Override
    public Stream<TestTemplateInvocationContext> provideTestTemplateInvocationContexts(ExtensionContext context) {
        Class<?> testClass = context.getRequiredTestClass();
        List<TestCase> testCases = testCases(testClass);
        List<EvalVariant> variants = variants(testClass);
        return testCases.stream().flatMap(testCase -> variants.stream()
                .<TestTemplateInvocationContext>map(variant -> new VariantInvocation(testCase, variant)));
    }

    private static List<TestCase> testCases(Class<?> testClass) {
        EvalTestCases annotation = testClass.getAnnotation(EvalTestCases.class);
        if (annotation == null) {
            throw new IllegalStateException(testClass.getName() + " has @EvalTest methods but no @EvalTestCases(\"...\") "
                    + "annotation naming its dataset");
        }
        try {
            return new TestCaseLoader().loadFromClasspath(annotation.value());
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load Judge4j test cases for " + testClass.getName() + ": "
                    + exception.getMessage(), exception);
        }
    }

    private static List<EvalVariant> variants(Class<?> testClass) {
        EvalVariants annotation = testClass.getAnnotation(EvalVariants.class);
        if (annotation == null) {
            throw new IllegalStateException(testClass.getName() + " needs an @EvalVariants(...) annotation");
        }
        Class<? extends EvalVariantFactory> factoryType = annotation.value();
        List<? extends EvalVariant> created;
        try {
            Constructor<? extends EvalVariantFactory> constructor = factoryType.getDeclaredConstructor();
            constructor.setAccessible(true);
            created = constructor.newInstance().createVariants();
        } catch (NoSuchMethodException exception) {
            throw new IllegalStateException(factoryType.getName() + " needs a no-argument constructor to be used "
                    + "with @EvalVariants", exception);
        } catch (InvocationTargetException exception) {
            throw new IllegalStateException("Creating variants with " + factoryType.getName() + " failed: "
                    + exception.getCause(), exception.getCause());
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot instantiate " + factoryType.getName(), exception);
        }
        if (created == null || created.isEmpty()) {
            throw new IllegalStateException(factoryType.getName() + " returned no variants; an evaluation needs at least one");
        }
        Set<String> seen = new HashSet<>();
        for (EvalVariant variant : created) {
            if (variant == null) {
                throw new IllegalStateException(factoryType.getName() + " returned a null variant");
            }
            if (!seen.add(variant.label())) {
                throw new IllegalStateException(factoryType.getName() + " returned two variants labelled '"
                        + variant.label() + "'; labels must be unique because the report keys results by label");
            }
        }
        return List.copyOf(created);
    }

    private record VariantInvocation(TestCase testCase, EvalVariant variant) implements TestTemplateInvocationContext {

        @Override
        public String getDisplayName(int invocationIndex) {
            return "[" + testCase.id() + ", " + variant.label() + "]";
        }

        @Override
        public List<Extension> getAdditionalExtensions() {
            return List.of(new EvaluationLifecycle(testCase, variant), new Parameters(testCase));
        }
    }

    /**
     * Prepares a fresh {@link EvaluationAccumulator}, applies the variant, then after the method
     * finishes turns the accumulated assertions into one recorded {@link EvalResult}. Failures are
     * reported here, not by the individual assertions, so every assertion in the method gets to run.
     */
    private record EvaluationLifecycle(TestCase testCase, EvalVariant variant)
            implements BeforeTestExecutionCallback, AfterTestExecutionCallback {

        @Override
        public void beforeTestExecution(ExtensionContext context) {
            context.getStore(NAMESPACE).put(ACCUMULATOR_KEY, new EvaluationAccumulator());
            // Applied after the accumulator exists so that a failing variant setup is still recorded.
            variant.apply(SpringExtension.getApplicationContext(context));
        }

        @Override
        public void afterTestExecution(ExtensionContext context) {
            EvaluationAccumulator accumulator = context.getStore(NAMESPACE).get(ACCUMULATOR_KEY, EvaluationAccumulator.class);
            String testCaseId = testCaseId(context);
            long latencyMs = accumulator == null ? 0 : accumulator.latencyMs();

            if (context.getExecutionException().isPresent()) {
                Throwable failure = context.getExecutionException().get();
                List<AssertionResult> soFar = accumulator == null ? List.of() : accumulator.snapshot();
                EvalRecorder.record(context, errorResult(testCaseId, latencyMs, failure.toString(), soFar));
                return; // JUnit reports the original exception
            }
            if (accumulator == null || accumulator.isEmpty()) {
                String message = "No assertions were recorded for " + testCaseId + " [" + variant.label()
                        + "]. Declare an EvalAssertions parameter and call evaluations.assertThat(...).satisfies(...).";
                EvalRecorder.record(context, errorResult(testCaseId, latencyMs, message, List.of()));
                throw new IllegalStateException(message);
            }
            EvalResult result = accumulator.finalizeResult(testCaseId, testCase, variant.label(), latencyMs);
            EvalRecorder.record(context, result);
            if (!result.pass()) {
                throw new AssertionError("Judge4j evaluation failed for " + testCaseId + " [" + variant.label() + "]: "
                        + failingAssertionSummary(result));
            }
        }

        private EvalResult errorResult(String testCaseId, long latencyMs, String error, List<AssertionResult> assertions) {
            return new EvalResult(testCaseId, testCase.description(), testCase.vars(), variant.label(), null, 0.0, false,
                    latencyMs, error, assertions);
        }

        private static String failingAssertionSummary(EvalResult result) {
            return result.assertions().stream()
                    .filter(assertion -> !assertion.pass())
                    .map(assertion -> assertion.label() + (assertion.feedback() != null ? " (" + assertion.feedback() + ")" : ""))
                    .reduce((a, b) -> a + "; " + b)
                    .orElse("no assertions recorded");
        }

        private String testCaseId(ExtensionContext context) {
            return context.getRequiredTestClass().getSimpleName() + "#"
                    + context.getRequiredTestMethod().getName() + ":" + testCase.id();
        }
    }

    /**
     * Collects the assertions recorded during one test method invocation and resolves their weights
     * once it completes. Explicit weights must total at most 100; any remainder is split equally
     * across assertions without one (if none has one, 100 is split equally across all of them).
     */
    private static final class EvaluationAccumulator {
        private final List<PendingAssertion> pending = new ArrayList<>();
        private final long startNanos = System.nanoTime();
        private double explicitWeight;
        private long evaluatorNanos;

        /** Checks, before any evaluator runs, that adding {@code weight} keeps the explicit total within 100. */
        synchronized void reserveWeight(Double weight) {
            if (weight == null) {
                return;
            }
            if (explicitWeight + weight > 100.0 + WEIGHT_EPSILON) {
                throw new IllegalArgumentException("Assertion weights total " + (explicitWeight + weight)
                        + "% which exceeds 100%; lower the explicit .weight(...) values of this test method");
            }
            explicitWeight += weight;
        }

        synchronized void add(PendingAssertion assertion, long evaluatorDurationNanos) {
            pending.add(assertion);
            evaluatorNanos += evaluatorDurationNanos;
        }

        synchronized boolean isEmpty() {
            return pending.isEmpty();
        }

        /** Time since the method started, excluding time spent inside evaluators (judge and embedding calls). */
        synchronized long latencyMs() {
            return Math.max(0, (System.nanoTime() - startNanos - evaluatorNanos) / 1_000_000);
        }

        synchronized List<AssertionResult> snapshot() {
            return resolve();
        }

        synchronized EvalResult finalizeResult(String testCaseId, TestCase testCase, String variantLabel, long latencyMs) {
            List<AssertionResult> assertions = resolve();
            double totalWeight = assertions.stream().mapToDouble(AssertionResult::weight).sum();
            boolean pass = assertions.stream().allMatch(AssertionResult::pass);
            double score = totalWeight <= 0
                    ? 0.0
                    : assertions.stream().mapToDouble(a -> a.pass() ? a.weight() : 0.0).sum() / totalWeight;
            Object output = assertions.get(0).input();
            return new EvalResult(testCaseId, testCase.description(), testCase.vars(), variantLabel, output,
                    score, pass, latencyMs, null, assertions);
        }

        private List<AssertionResult> resolve() {
            long unweightedCount = pending.stream().filter(assertion -> assertion.weight() == null).count();
            double remaining = Math.max(0.0, 100.0 - explicitWeight);
            double sharePerUnweighted = unweightedCount == 0 ? 0.0 : remaining / unweightedCount;
            List<AssertionResult> results = new ArrayList<>(pending.size());
            for (PendingAssertion assertion : pending) {
                double weight = assertion.weight() != null ? assertion.weight() : sharePerUnweighted;
                results.add(new AssertionResult(assertion.label(), assertion.expectation(), assertion.input(),
                        weight, assertion.pass(), assertion.score(), assertion.feedback()));
            }
            return results;
        }
    }

    private record PendingAssertion(String label, String expectation, Object input, Double weight, boolean pass,
            double score, String feedback) {
    }

    private record Parameters(TestCase testCase) implements ParameterResolver {

        private static final DefaultConversionService CONVERSION = new DefaultConversionService();

        @Override
        public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
            return parameterContext.getParameter().getType() == EvalAssertions.class || parameterContext.isAnnotated(Input.class);
        }

        @Override
        public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
            if (parameterContext.isAnnotated(Input.class)) {
                return resolveInput(parameterContext);
            }
            EvaluationAccumulator accumulator = extensionContext.getStore(NAMESPACE).get(ACCUMULATOR_KEY, EvaluationAccumulator.class);
            return new Assertions(testCase, accumulator);
        }

        private Object resolveInput(ParameterContext parameterContext) {
            String key = parameterContext.findAnnotation(Input.class).orElseThrow().value();
            if (!testCase.vars().containsKey(key)) {
                throw new ParameterResolutionException("Test case '" + testCase.id() + "' has no variable '" + key
                        + "' (it defines: " + testCase.vars().keySet() + ")");
            }
            Object value = testCase.vars().get(key);
            Parameter parameter = parameterContext.getParameter();
            if (value == null) {
                if (parameter.getType().isPrimitive()) {
                    throw new ParameterResolutionException("Variable '" + key + "' of test case '" + testCase.id()
                            + "' is null but the parameter is the primitive " + parameter.getType().getSimpleName());
                }
                return null;
            }
            if (parameter.getParameterizedType() instanceof Class<?> type && boxed(type).isInstance(value)) {
                return value;
            }
            TypeDescriptor source = TypeDescriptor.forObject(value);
            TypeDescriptor target = new TypeDescriptor(MethodParameter.forParameter(parameter));
            try {
                if (!CONVERSION.canConvert(source, target)) {
                    throw new ParameterResolutionException(cannotConvert(key, value, parameter));
                }
                return CONVERSION.convert(value, source, target);
            } catch (ConversionException exception) {
                throw new ParameterResolutionException(cannotConvert(key, value, parameter), exception);
            }
        }

        private String cannotConvert(String key, Object value, Parameter parameter) {
            return "Variable '" + key + "' of test case '" + testCase.id() + "' is a " + value.getClass().getSimpleName()
                    + " (" + value + ") and cannot be converted to " + parameter.getParameterizedType().getTypeName();
        }

        private static Class<?> boxed(Class<?> type) {
            return org.springframework.util.ClassUtils.resolvePrimitiveIfNecessary(type);
        }
    }

    private record Assertions(TestCase testCase, EvaluationAccumulator accumulator) implements EvalAssertions {
        @Override
        public <T> EvalAssertion<T> assertThat(T actual) {
            return new AssertionBuilder<>(actual, accumulator, testCase);
        }
    }

    /** {@code named}/{@code expecting} default to the test case's id/description. */
    private static final class AssertionBuilder<T> implements EvalAssertion<T> {
        private final T actual;
        private final EvaluationAccumulator accumulator;
        private String label;
        private String expectation;
        private Double weight;

        private AssertionBuilder(T actual, EvaluationAccumulator accumulator, TestCase testCase) {
            this.actual = actual;
            this.accumulator = accumulator;
            this.label = testCase.id();
            this.expectation = testCase.description().isEmpty() ? null : testCase.description();
        }

        @Override
        public EvalAssertion<T> named(String label) {
            if (label == null || label.isBlank()) {
                throw new IllegalArgumentException("Assertion name must not be blank");
            }
            this.label = label;
            return this;
        }

        @Override
        public EvalAssertion<T> expecting(String expectation) {
            this.expectation = expectation;
            return this;
        }

        @Override
        public EvalAssertion<T> weight(double weightPercent) {
            if (Double.isNaN(weightPercent) || weightPercent <= 0 || weightPercent > 100) {
                throw new IllegalArgumentException("Assertion weight must be greater than 0 and at most 100, was " + weightPercent);
            }
            this.weight = weightPercent;
            return this;
        }

        @Override
        public void satisfies(Evaluator<? super T> evaluator) {
            if (evaluator == null) {
                throw new IllegalArgumentException("evaluator must not be null");
            }
            accumulator.reserveWeight(weight);
            long started = System.nanoTime();
            Evaluation evaluation = evaluator.evaluate(actual);
            long duration = System.nanoTime() - started;
            if (evaluation == null) {
                throw new IllegalStateException("Evaluator " + evaluator.getClass().getName()
                        + " returned null for assertion '" + label + "'");
            }
            accumulator.add(new PendingAssertion(label, expectation, actual, weight, evaluation.pass(),
                    evaluation.score(), evaluation.feedback()), duration);
        }
    }
}
