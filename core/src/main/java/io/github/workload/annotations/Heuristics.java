package io.github.workload.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.SOURCE;

/**
 * 启发性参数/阈值.
 */
@Target(FIELD)
@Retention(SOURCE)
public @interface Heuristics {
}
