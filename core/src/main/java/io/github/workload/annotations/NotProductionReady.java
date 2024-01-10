package io.github.workload.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.CLASS;

/**
 * 目前还不成熟，禁止在生产环境使用!
 */
@Target({TYPE, FIELD, METHOD, CONSTRUCTOR})
@Retention(CLASS)
public @interface NotProductionReady {
    String value() default "";
}
