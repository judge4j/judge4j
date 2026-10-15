package io.github.judge4j.spring;

/** What a variant's setup step can see: the Spring beans of the running test context. */
public interface EvalVariantContext {

    /**
     * Looks up the bean of the given type.
     *
     * @throws org.springframework.beans.factory.NoSuchBeanDefinitionException if there is none, or
     *         {@link org.springframework.beans.factory.NoUniqueBeanDefinitionException} if there are several
     */
    <T> T bean(Class<T> beanType);
}
