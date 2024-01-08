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

- [https://github.com/zeromicro/go-zero/blob/master/core/load/adaptiveshedder.go](go-zero adaptive load shedding)
   - 仅仅以SlidingAverage CPU load作为过载
   - 没有`priority`概念
