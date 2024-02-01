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

### Heavy Tail

### Shuffle Sharding

### Overload Control

A hosting platform can take one or more of three actions during an overload:
- add capacity to the application by allocating idle or under-used servers
- turn away excess requests and preferentially service only “important” requests
- degrade the performance of admitted requests in order to service a larger number of aggregate requests

### Tail Latency

## References

- [Google Cloud Platform SLA](https://cloud.google.com/terms/sla)
