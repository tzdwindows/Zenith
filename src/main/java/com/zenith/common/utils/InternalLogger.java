package com.zenith.common.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 内部日志系统
 */
public class InternalLogger {
    private static final Logger logger = LoggerFactory.getLogger("ZENITH");

    /**
     * 获取调用此方法的类名和行号
     */
    private static String getCallerInfo() {
        return StackWalker.getInstance()
                .walk(frames -> frames
                        .skip(2)
                        .findFirst()
                        .map(f -> "[" + f.getClassName().substring(f.getClassName().lastIndexOf('.') + 1)
                                + ":" + f.getLineNumber() + "] ")
                        .orElse("[Unknown] "));
    }

    public static void info(String message) {
        logger.info("{}{}", getCallerInfo(), message);
    }

    public static void debug(String message) {
        if (logger.isDebugEnabled()) {
            logger.debug("{}{}", getCallerInfo(), message);
        }
    }

    public static void error(String message) {
        logger.error("{}{}", getCallerInfo(), message);
    }

    public static void error(String message, Throwable t) {
        logger.error("{}{}", getCallerInfo(), message, t);
    }

    public static void warn(String message) {
        logger.warn("{}{}", getCallerInfo(), message);
    }
}