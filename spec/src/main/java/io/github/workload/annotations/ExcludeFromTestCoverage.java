package io.github.workload.annotations;

import java.lang.annotation.Retention;

import static java.lang.annotation.RetentionPolicy.CLASS;

/**
 * Annotation filtering out methods and classes.
 *
 * <p>You can also filter out snippet as follows:</p>
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
@Retention(CLASS)
public @interface ExcludeFromTestCoverage {
}
