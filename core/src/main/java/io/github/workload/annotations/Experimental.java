package io.github.workload.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.CLASS;

/**
 * 标记为实验性质，即目前还不稳定，尚未经过严格测试，后面可能废弃或改动.
 */
@Target({TYPE, FIELD, METHOD, CONSTRUCTOR})
@Retention(CLASS)
public @interface Experimental {
    String value() default "";
}
