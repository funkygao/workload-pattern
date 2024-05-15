package io.github.workload.helper;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

public class LogUtil {
    public static ListAppender<ILoggingEvent> setupAppender(Class<?> clazz) {
        try {
            Logger logger = (Logger) LoggerFactory.getLogger(clazz);
            ListAppender<ILoggingEvent> appender = new ListAppender<>();
            appender.start();
            logger.addAppender(appender);
            appender.list.clear();
            return appender;
        } catch (ClassCastException e) {
            throw new RuntimeException("log4j-core cannot coexist with logback, pom exclude it before logging test");
        }
    }
}
