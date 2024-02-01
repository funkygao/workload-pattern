package io.github.workload.tail.agent;

import net.bytebuddy.asm.Advice;

class LoopMonitorAdvice {
    private static final int THRESHOLD = 2;
    private static long counter = 0;

    @Advice.OnMethodEnter
    public static void enter(@Advice.Origin String method) {
        System.out.println("enter: " + method);

        counter++;
        if (counter >= THRESHOLD) {
            System.out.println("Warning: Method " + method + " has been called " + counter + " times.");
        }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void exit() {

    }
}

