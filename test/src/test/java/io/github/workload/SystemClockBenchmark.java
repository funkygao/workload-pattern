package io.github.workload;

import org.junit.jupiter.api.Test;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(value = Mode.All)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 1)
@Measurement(iterations = 1)
@State(Scope.Thread)
public class SystemClockBenchmark {
    @Test
    void s() throws RunnerException {
        Options options = new OptionsBuilder().include(SystemClockBenchmark.class.getSimpleName()).build();
        Collection<RunResult> results = new Runner(options).run();
        System.out.println(results);
    }

    @Benchmark
    public void nanoTime() {
        System.nanoTime();
    }

    @Benchmark
    public void currentTimeMillis() {
        System.currentTimeMillis();
    }
}
