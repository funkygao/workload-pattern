package io.github.workload.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.CLASS;

/**
 * Proof of Concept，原型验证阶段.
 */
@Target({TYPE, FIELD, METHOD, CONSTRUCTOR})
@Retention(CLASS)
public @interface PoC {
    String value() default "";
}
