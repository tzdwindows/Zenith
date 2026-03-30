package com.zenith.common.utils;

import com.zenith.common.utils.InternalLogger;

/**
 * 专门供脚本调用的日志包装类
 */
public class ScriptLogger {

    private static final String PREFIX = "[JS] ";

    public void info(Object message) {
        InternalLogger.info(PREFIX + String.valueOf(message));
    }

    public void debug(Object message) {
        InternalLogger.debug(PREFIX + String.valueOf(message));
    }

    public void warn(Object message) {
        InternalLogger.warn(PREFIX + String.valueOf(message));
    }

    public void error(Object message) {
        InternalLogger.error(PREFIX + String.valueOf(message));
    }

    /**
     * 兼容 JS 习惯的 log 方法
     */
    public void log(Object message) {
        info(message);
    }
}