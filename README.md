<h1 align="center">Workload Pattern</h1>

<div align="center">

Workload scheduling patterns in distributed environment.

</div>

<div align="center">

Languages： English | [中文](README.zh-cn.md)
</div>

----

## Overview

The following metrics are often used as scheduling objectives for a workload scheduling strategy: load balancing, economic principles, time horizon minimization, and quality of service (QoS).

## Patterns

### Shuffle Sharding

### Overload Control

### Tail Latency

## References

### [go-zero adaptive load shedding](https://github.com/zeromicro/go-zero/blob/9a671f6059791206b20cd3f1fa1f437c87b7b8ea/core/load/adaptiveshedder.go#L119)

#### Under the hood

```golang
// 写死了2个优先级，它们的CPU阈值不同，根据REST请求的打标决定使用哪一个shedder
// 即 priority=true，则它被drop的CPU负载条件更宽松：默认情况下，非优先要求50%，优先可以撑到75%
func newEngine(c RestConf) *engine {
    if c.CpuThreshold > 0 {
        engine.shedder = load.NewAdaptiveShedder(load.WithCpuThreshold(c.CpuThreshold))
        engine.priorityShedder = load.NewAdaptiveShedder(load.WithCpuThreshold((c.CpuThreshold + 1000) / 2))
    }
}
```

[SheddingHandler](https://github.com/zeromicro/go-zero/blob/master/rest/handler/sheddinghandler.go)，责任链模式来处理REST请求，来了解该机制是如何集成到REST请求处理过程的。

#### Conclusion

默认情况下，SlidingWindow(与Sentinel的LeapArray基本相同)保存最近5s数据，切分成50个bucket，即每个bucket 100ms，每秒10个bucket。
如何判断当前可以处理的max inflight requests per second？

假设，某1个bucket成功处理的请求数量最大，为30；某1个bucket平均RT最小，为60ms，那么当下可接受的inflight request数：
```
30(bucketMaxQPS) * 10(buckets per second) * 60(bucketMinAvgRt) / 1000(1s has 1000ms) = 18
```

### [Sentinel](https://github.com/alibaba/Sentinel/)

- [SystemRuleManager](https://github.com/alibaba/Sentinel/blob/a524ab3bb3364818e292e1255480d20845e77c89/sentinel-core/src/main/java/com/alibaba/csp/sentinel/slots/system/SystemRuleManager.java#L290)
   - 人为设定的阈值(qps, maxThread, 平均响应时长, cpuUsage, cpuLoad)
- [TrafficShapingController](https://github.com/alibaba/Sentinel/blob/master/sentinel-core/src/main/java/com/alibaba/csp/sentinel/slots/block/flow/TrafficShapingController.java)

### [K8S APF](https://github.com/kubernetes/enhancements/blob/master/keps/sig-api-machinery/1040-priority-and-fairness/README.md)
