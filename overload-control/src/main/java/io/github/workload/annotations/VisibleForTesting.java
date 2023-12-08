package io.github.workload.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.SOURCE;

@Target({TYPE, FIELD, METHOD, CONSTRUCTOR})
@Retention(SOURCE)
public @interface VisibleForTesting {
}
