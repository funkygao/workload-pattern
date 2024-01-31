package io.github.workload.agent;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;

import java.lang.instrument.Instrumentation;

import static net.bytebuddy.matcher.ElementMatchers.any;

/**
 * 虚拟机级别的AOP，监控请求处理过程中的循环次数.
 *
 * <p>对于一个业务系统，高成本请求，主要是因为：依赖的服务慢，数据库慢查询，大循环</p>
 * <p>而只有大循环是在吃JVM的CPU，容易导致过载</p>
 */
public class LoopMonitorAgent {

    public static void premain(String agentArgs, Instrumentation instrumentation) {
        if (agentArgs != null) {
            for (String token : agentArgs.split(";")) {
                String[] args = token.split("=");
                if (args.length != 2) {
                }
            }
        }

        new AgentBuilder.Default()
                .type(any())
                .transform((builder, typeDescription, classLoader, javaModule) ->
                        builder.method(any())
                                .intercept(Advice.to(LoopMonitorAdvice.class))
                ).installOn(instrumentation);
    }
}
