package io.github.workload.shufflesharding;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(value = Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 1)
@Measurement(iterations = 1)
@State(Scope.Thread)
public class CardDealerBenchmark {
    private static final int deckSize = 12;
    private static final int handSize = 3;

    @Test
    @Disabled
    void main() throws RunnerException {
        Options options = new OptionsBuilder()
                .include(CardDealerBenchmark.class.getSimpleName())
                .resultFormat(ResultFormatType.TEXT)
                .result("CardDealer.bench")
                .build();
        new Runner(options).run();
    }

    // 341.506 ns/op
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
