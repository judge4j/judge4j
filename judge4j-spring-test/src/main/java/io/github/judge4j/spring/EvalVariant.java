package io.github.judge4j.spring;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import org.springframework.context.ApplicationContext;

/**
 * One configuration of the system under test that an evaluation compares against the others - for
 * example "system prompt A" versus "system prompt B", or one model versus another. A variant is a
 * label plus setup steps that run against the Spring context just before each test method
 * invocation; the steps are ordinary Java and may use Mockito.
 *
 * <pre>{@code
 * EvalVariant.named("terse-prompt")
 *         .configure(ctx -> Mockito.doReturn(terse).when(ctx.bean(MyService.class)).systemPrompt());
 * }</pre>
 *
 * <p>Variants are immutable. The Spring context is shared by all runs, so a variant must set up
 * everything it differs on: state left behind by the previous variant is not reset for you.
 */
public final class EvalVariant {

    private final String label;
    private final List<Consumer<EvalVariantContext>> setupSteps;

    private EvalVariant(String label, List<Consumer<EvalVariantContext>> setupSteps) {
        this.label = label;
        this.setupSteps = List.copyOf(setupSteps);
    }

    /**
     * Starts a variant with the given label, which names its column in the report.
     *
     * @throws IllegalArgumentException if {@code label} is null or blank
     */
    public static EvalVariant named(String label) {
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("A variant label must not be blank");
        }
        return new EvalVariant(label, List.of());
    }

    /** Returns a copy of this variant with {@code setupStep} appended; steps run in the order added. */
    public EvalVariant configure(Consumer<EvalVariantContext> setupStep) {
        List<Consumer<EvalVariantContext>> steps = new ArrayList<>(setupSteps);
        steps.add(Objects.requireNonNull(setupStep, "setupStep must not be null"));
        return new EvalVariant(label, steps);
    }

    public String label() {
        return label;
    }

    void apply(ApplicationContext context) {
        EvalVariantContext variantContext = new SpringEvalVariantContext(context);
        setupSteps.forEach(setupStep -> setupStep.accept(variantContext));
    }
}
