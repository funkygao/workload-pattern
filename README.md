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

- [go-zero adaptive load shedding](https://github.com/zeromicro/go-zero/blob/master/core/load/adaptiveshedder.go)
   - (CPU load, 当前并发请求数超过max concurrency)则drop request
      - max concurrency = max requests (qps) * min response time (rt)
   - 没有`priority`概念
- [Sentinel](https://github.com/alibaba/Sentinel/)
   - [SystemRuleManager](https://github.com/alibaba/Sentinel/blob/a524ab3bb3364818e292e1255480d20845e77c89/sentinel-core/src/main/java/com/alibaba/csp/sentinel/slots/system/SystemRuleManager.java#L290)
      - 人为设定的阈值(qps, maxThread, 平均响应时长, cpuUsage, cpuLoad)
   - [TrafficShapingController](https://github.com/alibaba/Sentinel/blob/master/sentinel-core/src/main/java/com/alibaba/csp/sentinel/slots/block/flow/TrafficShapingController.java)
