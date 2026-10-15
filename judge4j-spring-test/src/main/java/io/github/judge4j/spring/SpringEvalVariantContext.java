package io.github.judge4j.spring;

import java.util.Objects;
import org.springframework.context.ApplicationContext;

final class SpringEvalVariantContext implements EvalVariantContext {
    private final ApplicationContext applicationContext;

    SpringEvalVariantContext(ApplicationContext applicationContext) {
        this.applicationContext = Objects.requireNonNull(applicationContext, "applicationContext must not be null");
    }

    @Override
    public <T> T bean(Class<T> beanType) {
        return applicationContext.getBean(beanType);
    }
}