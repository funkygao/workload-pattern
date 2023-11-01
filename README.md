<h1 align="center">Service Pattern</h1>

<div align="center">

Service patterns in distributed environment.

</div>

<div align="center">

Languages： English | [中文](README.zh-cn.md)
</div>

----

## Overview

![](assets/bigpicture.svg)

## Shuffle Sharding

```
(deckSize, handSize, requestIdentifier) -> int[handSize]
                                              |
                                         ProviderDiscovery
                                              |
                                          LoadBalance
                                              |
                                              | (random/roundrobin/leastconn/...)
                                              |
                                         target provider
```

## Overload Control

```
                overloaded
RequestPriority ----------> deny/accept -> run
                                            |
                recordQueuingTime <---------+
```

### Features

- fine grained request priority based
- respect user expected SLO under recursive microservice environment
- best effortly minimize resource waste under overload

## Instant Queue

```
                +- complete message set MQ topic
                |
busness event --+- instant message subset MQ topicA
                |
                +- instant message subset MQ topicB
```
