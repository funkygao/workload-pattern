package io.github.workload.shufflesharding;

import io.github.workload.annotations.Heuristics;
import io.github.workload.annotations.VisibleForTesting;
import lombok.Getter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.function.IntConsumer;

/**
 * A shuffle sharder based on probabilistic hashing for better isolation and reducing blast radius of an outage.
 *
 * <p>它是比{@code 单元化 Set}更细粒度的资源分配算法，有效降低爆炸半径.</p>
 * <ul>本质是在一个大的共享集群里建立多个隔离仓，为每类请求分配不同的隔离仓，同时保证不同类别请求隔离仓尽可能不重叠
 * <li>请求故障隔离，某类bad request导致的故障传染到整个集群，例如：OOM/Stackoverflow/线程池满/dead loop/FullGC/etc</li>
 * <li>机器故障隔离，一台服务器故障可能导致所有租户都受影响，因为它接收所有租户的请求</li>
 * <li>资源/配额隔离，例如：基于单机的服务端限流(e,g. 库存服务)，不同租户争抢token，某个租户突发流量会导致其他租户被限流，即使其他租户请求量很低(但它可能很重要)</li>
 * <li>扩展点运行时隔离</li>
 * </ul>
 * <pre>
 * Shuffle sharding is a technique that isolates different tenant's workloads and gives each tenant a single-tenant experience, even if they're running in a shared cluster.
 * This technique minimizes the number of overlapping instances between two tenants.
 * </pre>
 *
 * @see <a href="https://github.com/kubernetes/kubernetes/issues/77723">APF Github issue</a>
 * @see <a href="https://grafana.com/docs/mimir/latest/configure/configure-shuffle-sharding/">Grafana Ingester/Query-frontend/Store-gateway/Compactor/Alertmanager</a>
 * @see <a href="https://github.com/grafana/dskit/blob/main/ring/ring.go#L676">Grafana的底层实现</a>
 * @see <a href="https://github.com/kubernetes/enhancements/blob/master/keps/sig-api-machinery/1040-priority-and-fairness/README.md">APF Design</a>
 * @see <a href="https://cloud.redhat.com/blog/surviving-the-api-storm-with-api-priority-fairness">APF配置</a>
 */
public class CardDealer {
    /**
     * MaxHashBits is the max bit length which can be used from hash value(source of entropy).
     */
    @VisibleForTesting
    @Heuristics
    static final int MaxHashBits = 60;

    /**
     * 总共有多少张牌.
     *
     * <p>具体应用时，该值通常代表集群总共有多少个节点.</p>
     */
    @Getter
    private final int deckSize;

    /**
     * 手里要抓几张牌.
     *
     * <p>具体应用该算法时，该值通常代表为某租户分配多少个节点，即{@code NodeSubset.size}.</p>
     * <p>在{@code load balance}场景，{@link #handSize}表示对某类请求分配多少台服务器实例处理。如果为1，那么在部署过程中，该类请求无法处理；为2，只允许step=1的incremental deployment</p>
     */
    @Getter
    private final int handSize;

    CardDealer(int deckSize, int handSize) throws IllegalArgumentException {
        if (deckSize < 1 || handSize < 1) {
            throw new IllegalArgumentException("deckSize or handSize is not positive");
        }
        if (handSize > deckSize) {
            throw new IllegalArgumentException("handSize is greater than deckSize");
        }
        if (deckSize > 1 << 26) {
            throw new IllegalArgumentException("deckSize is impractically large");
        }
        if (requiredEntropyBits(deckSize, handSize) > MaxHashBits) {
            // handSize太大就起不到租户隔离的作用了，如果deckSize=handSize，就退化到非shuffle sharding模式了
            // 此外，handSize通常与失败重试次数密切相关
            throw new IllegalArgumentException(String.format("required entropy bits of deckSize %d and handSize %d is greater than %d", deckSize, handSize, MaxHashBits));
        }

        this.deckSize = deckSize;
        this.handSize = handSize;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 将一副大小为{@code deckSize}的牌洗牌并分成手中大小为{@code handSize}的牌时，所需的哈希值中消耗的比特数.
     *
     * @param deckSize 有多少张牌
     * @param handSize 抓到手中多少张牌
     * @return 哈希值消耗的位数
     */
    @VisibleForTesting
    static int requiredEntropyBits(int deckSize, int handSize) {
        // 在概率论中，当选择有deckSize个可能选项时，每个选项需要消耗log_e(deckSize)比特的信息
        // 但因为计算机中常用的比特数是以二进制表示的，所以转换为以2为底的对数
        double entropyBitsPerCard = Math.log(deckSize) / Math.log(2); // 选择一张牌所需的比特数
        return (int) (Math.ceil(entropyBitsPerCard * handSize));
    }

    // handSize: H/deckSize: D, find the unique set of integers:
    // A[0] in [0, D)
    // A[1] in [0, D-1)
    // …
    // A[H-1] in [0, D-(H-1))
    // A[H] >= 0 such that V = sum[i=0, 1, ...H] A[i] * ff(deckSize, i)
    // where ff(N, M) is the falling factorial N!/(N-M)!
    // The probability distributions of each of these A’s will not be perfectly even
    // but we constrain the configuration such that ff(D, H) is less than 2^60 to keep the unevenness small
    private void deal(long hashValue, IntConsumer cardAcceptor) {
        // 洗牌，但洗牌结果shuffled里可能有重复值，主要原因是hashValue小
        int[] shuffled = new int[handSize];
        for (int i = 0; i < handSize; i++) {
            int remains = deckSize - i;
            long hashValueNext = hashValue / remains;
            shuffled[i] = (int) (hashValue - remains * hashValueNext);
            hashValue = hashValueNext;
        }

        // 发牌，确保不重复
        for (int i = 0; i < handSize; i++) {
            int card = shuffled[i];
            // 使用内层循环与前面已经发出的牌的发牌顺序进行比较，保证当前牌的发牌顺序不重复
            // {3, 0, 1} => {3, 0, 2}
            // {1, 4, 5} => {1, 5, 7}
            // {2, 0, 2} => {2, 0, 4}
            // {0, 0, 0} => {0, 1, 2}
            // {1, 0, 0} => {1, 0, 2}
            for (int j = i - 1; j >= 0; j--) {
                if (card >= shuffled[j]) {
                    // 不会出现 card > deckSize
                    card++;
                }
            }

            cardAcceptor.accept(card);
        }
    }

    /**
     * 根据抓牌者哈希值，将一副牌洗牌并分成手中牌，每张牌的选择是随机的，且不重复.
     *
     * @param hashValue 抓牌者哈希值，source of entropy.
     * @param hand      手中牌. Cannot be null, and size must equal {@code handSize}
     * @throws IllegalArgumentException
     */
    @VisibleForTesting
    void dealIntoHand(long hashValue, int[] hand) throws IllegalArgumentException {
        if (hand == null || hand.length != this.handSize) {
            throw new IllegalArgumentException("hand cannot be null and must have length of handSize");
        }

        // zero out the sign bit
        this.deal(hashValue & Long.MAX_VALUE, new Acceptor(hand));
    }

    /**
     * 根据抓牌者特征标识，将一副牌洗牌并分成手中牌，每张牌的选择是随机的，且不重复.
     *
     * @param identifier 抓牌者特征标识. Cannot be null
     * @param hand       手中牌. Cannot be null, and size must equal {@code handSize}
     * @throws IllegalArgumentException
     */
    public void dealIntoHand(String identifier, int[] hand) throws IllegalArgumentException {
        if (identifier == null) {
            throw new IllegalArgumentException("identifier cannot be null");
        }

        dealIntoHand(hash(identifier), hand);
    }

    @VisibleForTesting
    static long hash(String identifier) {
        long hashValue = 0;
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            digest.update(identifier.getBytes(StandardCharsets.UTF_8));
            byte[] checksum = digest.digest();
            // use the first 64 bits of the checksum as hashValue
            for (int i = 0; i < 8; i++) {
                hashValue += ((long) checksum[i] & 0xFFL) << (8 * i);
            }
        } catch (NoSuchAlgorithmException ignored) {
            // degrade
            hashValue = identifier.hashCode();
        }

        return hashValue;
    }

    private static class Acceptor implements IntConsumer {
        private int idx = 0;

        /**
         * 手中牌.
         */
        private final int[] hand;

        Acceptor(int[] hand) {
            this.hand = hand;
        }

        @Override
        public void accept(int card) {
            this.hand[idx] = card;
            this.idx++;
        }
    }

    /**
     * {@link CardDealer} builder.
     */
    public static class Builder {
        private int deckSize;
        private int handSize;

        /**
         * 设置总共有多少张牌.
         */
        public Builder deckSize(int deckSize) {
            this.deckSize = deckSize;
            return this;
        }

        /**
         * 设置手中发多少张牌.
         */
        public Builder handSize(int handSize) {
            this.handSize = handSize;
            return this;
        }

        public CardDealer build() {
            return new CardDealer(deckSize, handSize);
        }
    }

}

