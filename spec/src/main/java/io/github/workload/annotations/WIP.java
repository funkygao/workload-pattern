package io.github.workload.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.CLASS;

/**
 * Working in progress, NOT production ready.
 */
@Target({TYPE, FIELD, METHOD, CONSTRUCTOR})
@Retention(CLASS)
public @interface WIP {
    String value() default "";
}
