package io.github.workload.simulate;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * LatencySimulator 是一个用于模拟不同延迟场景的类。
 * 它允许用户指定一个延迟范围，并生成一个随机延迟列表，
 * 其中每个延迟值都倾向于更接近范围的下限。
 * 通过调整陡峭程度参数（steepness），可以控制生成的延迟值分布的陡峭程度。
 */
public class LatencySimulator implements Iterable<Integer> {
    private final int lowerBound;
    private final int upperBound;

    private List<Integer> latencies;

    public LatencySimulator(int lowerBound, int upperBound) {
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
    }


    /**
     * 生成陡峭的latency数据集.
     *
     * <ul>steepness
     * <li>steepness = 1：接近均匀分布</li>
     * <li>0 < steepness < 1：分布将变得更加陡峭</li>
     * <li>steepness > 1：分布将变得更加平缓，但仍然会倾向于 lowerBound</li>
     * </ul>
     *
     * @param N         数据集大小
     * @param steepness 控制延迟分布陡峭程度的参数
     * @return
     */
    public LatencySimulator simulate(int N, double steepness) {
        latencies = new ArrayList<>(N);
        for (int i = 0; i < N; i++) {
            double randomFactor = Math.random(); // [0.0, 1.0)
            double adjustedRandomFactor = 1 - Math.pow(randomFactor, steepness);
            int latency = lowerBound + (int) (adjustedRandomFactor * (upperBound - lowerBound));
            if (latency < lowerBound) {
                latency = lowerBound;
            } else if (latency > upperBound) {
                latency = upperBound;
            }

            latencies.add(latency);
        }
        return this;

    }

    @Override
    public Iterator<Integer> iterator() {
        return latencies.iterator();
    }
}
