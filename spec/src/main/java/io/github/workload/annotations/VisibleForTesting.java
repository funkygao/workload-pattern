package io.github.workload.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.CLASS;

@Target({TYPE, FIELD, METHOD, CONSTRUCTOR})
@Retention(CLASS)
public @interface VisibleForTesting {
    String value() default "";
}
