# References

## Key Takeaways

### Priority

- HBase RPC
  - Ingteger priority
  - `@QosPriority`应用在RpcMethod，事先创建不同的线程池RpcExecutor，并根据优先级分配到相应的RpcExecutor
    - AdaptiveLifoCoDelCallQueue
  - has deadline mechanism without propagation
- Kanaloa
  - 无优先级机制
- Sentinel and the alike
  - Boolean priority
  - 系统写死了两个shedder：优先的，不优先的

### 如何判断是否过载

- HBase AdaptiveLifoCoDelCallQueue: 根据排队时间
- Sentinel: 根据CPU使用率阈值，priority=true/false分别对应2个阈值

## AQM

| Algorithm | 启发性参数                              | 如何判断拥塞 congestion | 何时drop | 丢包算法                                          |
| --------- | ---------------------------------- | ----------------- | ------ | --------------------------------------------- |
| RED       | (CongestionQueueSizeRange，maxDropProbability) | 移动平均的队列长度位于拥塞区间   | enque  | 随机丢包概率：maxDropProbability * 当前平均队列长度占拥塞区间的百分比 |
| CoDel     |                                    |                   | deque  |                                               |
| PIE       |                                    |                   |        |                                               |


- [Proportional Integral controller Enhanced(PIE algorithm)](https://github.com/iheartradio/kanaloa/blob/0.5.x/core/src/main/scala/kanaloa/queue/Regulator.scala)
- [AQM-PIE RFC](https://datatracker.ietf.org/doc/html/draft-ietf-aqm-pie-03)
- [Cgroup - Linux的网络资源隔离](https://github.com/zorrozou/zorrozou.github.io/blob/master/docs/books/cgroup_linux_network_control_group.md)
- [Google TCP BBR](https://cloud.google.com/blog/products/networking/tcp-bbr-congestion-control-comes-to-gcp-your-internet-just-got-faster)
- [HBase AdaptiveLifoCoDelCallQueue](https://github.com/apache/hbase/blob/master/hbase-server/src/main/java/org/apache/hadoop/hbase/ipc/AdaptiveLifoCoDelCallQueue.java)

## [K8S APF](https://github.com/kubernetes/enhancements/blob/master/keps/sig-api-machinery/1040-priority-and-fairness/README.md)

## HBase RPC基于优先级的过载保护

Server(HMaster/HRegionServer)处理RPC的请求。

### 优先级定义

```java
class HConstants {
    public static final int PRIORITY_UNSET = -1;

    // 值越大优先级越高
    public static final int NORMAL_QOS = 0;
    public static final int BULKLOAD_QOS = 4;
    public static final int REPLICATION_QOS = 5;

    public static final int ADMIN_QOS = 100;
    public static final int HIGH_QOS = 200;
}
```

### Under the hood

`RpcScheduler`根据RPC请求的优先级交给不同的RpcExecutor处理，RpcExecutor把请求放入CallQueue，RpcHandler从CallQueue拿请求并执行。

一个Handler就是一个线程。

```java
NettyServerRpcConnection#process(ByteBuf buf)
    processOneRpc
        processRequest {
            ServerCall<?> call = createCall();
            if (!rpcServer.scheduler.dispatch(new CallRunner(this.rpcServer, call))) {
                call.setResponse("Call queue is full");
            }
        }

class SimpleRpcScheduler {
    private final PriorityFunction priority;

    // RpcExecutor -> BalancedQueueRpcExecutor -> FastPathBalancedQueueRpcExecutor
    // RpcExecutor -> RWQueueRpcExecutor -> FastPathRWQueueRpcExecutor/MetaRWQueueRpcExecutor

    private final RpcExecutor callExecutor;
    private final RpcExecutor priorityExecutor;
    private final RpcExecutor replicationExecutor;
    private final RpcExecutor metaTransitionExecutor;
    private final RpcExecutor bulkloadExecutor;

    bool dispatch(CallRunner callTask) {
        int level = priority.getPriority(callTask); // AnnotationReadingPriorityFunction#getPriority 根据注解 QosPriority
        RpcExecutor theExecutor;
        switch {
            case level == META_TRANSITION_QOS:
                theExecutor = metaTransitionExecutor; break;
            case level > highPriorityLevel:
                theExecutor = priorityExecutor; break;
            case level == REPLICATION_QOS:
                theExecutor = replicationExecutor; break;
            case level == BULKLOAD_QOS:
                theExecutor = bulkloadExecutor; break;
            default:
                theExecutor = callExecutor;
        }

        return theExecutor.dispatch(callTask);
    }
}

class BalancedQueueRpcExecutor {
    boolean dispatch(final CallRunner callTask) {
        // random LB
        int queueIndex = balancer.getNextQueue(callTask);
        BlockingQueue<CallRunner> callQueue = queues.get(queueIndex);
        //               offer                take
        // RpcExecutor ---------> CallQueue --------> RpcHandler
        //                          |
        //     AdaptiveLifoCoDelCallQueue
        return callQueue.offer(callTask)
    }
}

class RpcHandler extends Thread {
    final BlockingQueue<CallRunner> callQueue;

    public void run() {
        while (running) {
            run(getCallRunner());
        }
    }

    protected CallRunner getCallRunner() {
        return callQueue.take();
    }
}
```

## Sentinel自适应限流算法

priority是boolean类型，系统写死了两个shedder：优先的，不优先的，区别是优先的CPU阈值更高(容忍更高的负载)，根据REST请求的打标决定使用哪一个shedder。

典型应用：

- [Sentinel BBR](https://github.com/alibaba/Sentinel/blob/a524ab3bb3364818e292e1255480d20845e77c89/sentinel-core/src/main/java/com/alibaba/csp/sentinel/slots/system/SystemRuleManager.java#L290)
  - [设计文档](https://github.com/alibaba/Sentinel/wiki/%E7%B3%BB%E7%BB%9F%E8%87%AA%E9%80%82%E5%BA%94%E9%99%90%E6%B5%81)
- [go-zero adaptive load shedding](https://github.com/zeromicro/go-zero/blob/9a671f6059791206b20cd3f1fa1f437c87b7b8ea/core/load/adaptiveshedder.go#L119)
  - [SheddingHandler责任链处理REST请求](https://github.com/zeromicro/go-zero/blob/master/rest/handler/sheddinghandler.go)
- [Kratos核心算法](https://github.com/go-kratos/aegis/blob/99110a3f05f44234f21d65f79be71d1e2706937d/ratelimit/bbr/bbr.go#L120)

默认情况下，SlidingWindow保存最近5s数据，切分成50个bucket，即每个bucket 100ms，每秒10个bucket。
如何判断当前可以处理的max inflight requests per second？

假设，某1个bucket成功处理的请求数量最大，为30；某1个bucket平均RT最小，为60ms，那么当下可接受的inflight request数：

```
30(bucketMaxQPS) * 10(buckets per second) * 60(bucketMinAvgRt) / 1000(1s has 1000ms) = 18
```

它的理论依据是`Little's Law`在系统吞度量方面的应用：

```
QPS(TPS) = 并发数 / 平均响应时长 => 并发数 = QPS * 平均响应时长
```

## Kanaloa PIE

![](/doc/img/Kanaloa.svg)

与HBase相比，Worker相当于RpcHandler，Queue相当于BlockingQueue，Dispatcher相当于RpcScheduler， QueueSampler统计(队列长度，出队速度)为Regulator(PIE算法)提供算法依据
