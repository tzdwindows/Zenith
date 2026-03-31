package com.zenith.common.utils;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

public class ScriptLogger {

    private static final String PREFIX = "[JS]";

    /**
     * 获取 JS 的位置信息（文件名:行:列）
     */
    private String getCallerInfo() {
        try {
            Context context = Context.getCurrent();
            if (context == null) return "";
            Value stack = context.eval("js", "(new Error().stack.split('\\n')[2] || '')");
            String stackLine = stack.asString();
            if (stackLine.contains("(") && stackLine.contains(")")) {
                return " [" + stackLine.substring(stackLine.lastIndexOf("(") + 1, stackLine.lastIndexOf(")")) + "] ";
            }
            return " " + stackLine.trim() + " ";
        } catch (Exception e) {
            return "";
        }
    }

    public void info(Object message) {
        InternalLogger.info(getCallerInfo() + PREFIX + " " + String.valueOf(message));
    }

    public void debug(Object message) {
        InternalLogger.debug(getCallerInfo() + PREFIX + " " + String.valueOf(message));
    }

    public void warn(Object message) {
        InternalLogger.warn(getCallerInfo() + PREFIX + " " + String.valueOf(message));
    }

    public void error(Object message) {
        InternalLogger.error(getCallerInfo() + PREFIX + " " + String.valueOf(message));
    }

    public void log(Object message) {
        info(message);
    }
}