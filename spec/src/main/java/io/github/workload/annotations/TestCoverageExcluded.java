package io.github.workload.annotations;

import lombok.Generated;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.CLASS;

/**
 * Annotation filtering out methods and classes, buddied with {@link Generated}.
 * <p>
 * <p>Besides, you can also filter out snippet as follows:</p>
 * <pre>
 * {@code
 *
 * catch (SomeException e) {
 *     dohandle(e); // $COVERAGE-IGNORE$
 * }
 *
 * }
 * </pre>
 *
 * @see <a href="https://github.com/jacoco/jacoco/wiki/FilteringOptions#annotation-based-filtering">Jacoco Annotation-Based Filtering</a>
 */
@Target({TYPE, METHOD})
@Retention(CLASS)
public @interface TestCoverageExcluded {
}
