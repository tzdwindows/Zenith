package com.zenith.common.utils;

/**
 * 专门用于记录网页内部 (Chromium) 输出的日志类
 */
public class HTMLLogger {

    private static final String PREFIX = "[HTML]";

    /**
     * 格式化输出网页日志
     * @param level 日志级别 (info, warn, error)
     * @param message 网页控制台消息
     * @param source 源代码文件名
     * @param line 行号
     */
    public static void log(String level, String message, String source, int line) {
        // 模仿 ScriptLogger 的格式: [文件名:行号] [HTML] 消息
        String location = "";
        if (source != null && !source.isEmpty()) {
            // 只保留文件名，去掉冗长的完整路径（可选）
            String fileName = source.contains("/") ? source.substring(source.lastIndexOf("/") + 1) : source;
            location = " [" + fileName + ":" + line + "] ";
        }

        String formattedMessage = location + PREFIX + " " + message;

        switch (level.toLowerCase()) {
            case "warning":
            case "logseverity_warning":
                InternalLogger.warn(formattedMessage);
                break;
            case "error":
            case "logseverity_error":
                InternalLogger.error(formattedMessage);
                break;
            default:
                InternalLogger.info(formattedMessage);
                break;
        }
    }
}