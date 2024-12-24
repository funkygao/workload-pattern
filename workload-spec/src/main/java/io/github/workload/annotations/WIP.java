package io.github.workload.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.CLASS;

/**
 * Working In Progress, NOT production ready yet!
 *
 * <p>与{@link PoC}相比，{@link WIP}更有可能被采纳，虽然它们目前都无法上线到生产环境.</p>
 */
@Target({TYPE, FIELD, METHOD, CONSTRUCTOR})
@Retention(CLASS)
public @interface WIP {
    String value() default "";
}
