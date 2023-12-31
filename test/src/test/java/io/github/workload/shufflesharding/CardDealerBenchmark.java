package io.github.workload.shufflesharding;

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
public class CardDealerBenchmark {
    private static final int deckSize = 12;
    private static final int handSize = 3;

    @Test
    void s() throws RunnerException {
        Options options = new OptionsBuilder().include(CardDealerBenchmark.class.getSimpleName()).build();
        Collection<RunResult> results = new Runner(options).run();
        System.out.println(results);
    }

    @Benchmark
    public void dealIntoHand() {
        CardDealer dealer = CardDealer.builder()
                .deckSize(deckSize)
                .handSize(handSize)
                .build();
        int[] hands = new int[handSize];
        dealer.dealIntoHand("4_223", hands);
    }
}
