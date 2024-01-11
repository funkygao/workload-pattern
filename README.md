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

### Sentinel自适应限流算法

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

### [Proportional Integral controller Enhanced(PIE algorithm)](https://github.com/iheartradio/kanaloa/blob/0.5.x/core/src/main/scala/kanaloa/queue/Regulator.scala)

- [AQM-PIE RFC](https://datatracker.ietf.org/doc/html/draft-ietf-aqm-pie-03)
- [Cgroup - Linux的网络资源隔离](https://github.com/zorrozou/zorrozou.github.io/blob/master/docs/books/cgroup_linux_network_control_group.md)


### [K8S APF](https://github.com/kubernetes/enhancements/blob/master/keps/sig-api-machinery/1040-priority-and-fairness/README.md)
