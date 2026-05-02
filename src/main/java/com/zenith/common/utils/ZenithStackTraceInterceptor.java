package com.zenith.common.utils;

import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

public class ZenithStackTraceInterceptor extends OutputStream {
    private final StringBuilder buffer = new StringBuilder();
    private final PrintStream originalErr;

    public ZenithStackTraceInterceptor(PrintStream originalErr) {
        this.originalErr = originalErr;
    }

    /**
     * 安装拦截器，接管 System.err
     */
    public static void install() {
        // 使用 autoFlush = true 确保及时调用 write
        System.setErr(new PrintStream(new ZenithStackTraceInterceptor(System.err), true, StandardCharsets.UTF_8));
    }

    @Override
    public void write(int b) {
        // 实时转发给原始流，保证 IDEA 控制台依然有输出（可选）
        if (originalErr != null) {
            originalErr.write(b);
        }

        if (b == '\n') {
            flushBuffer();
        } else {
            buffer.append((char) b);
        }
    }

    /**
     * 处理每一行数据
     */
    private void flushBuffer() {
        if (buffer.length() == 0) return;

        String line = buffer.toString().replace("\r", "");

        // 核心逻辑：不再过滤，全部交给 InternalLogger
        // 你可以在这里根据关键词提升日志等级
        if (line.contains("Exception") || line.contains("Error") || line.trim().startsWith("at ")) {
            InternalLogger.error("[Intercepted-Error] " + line);
        } else {
            InternalLogger.info("[Intercepted-Log] " + line);
        }

        buffer.setLength(0);
    }

    @Override
    public void flush() {
        flushBuffer(); // 确保强制 flush 时输出缓冲区剩余内容
        if (originalErr != null) {
            originalErr.flush();
        }
    }

    @Override
    public void close() {
        flushBuffer();
        if (originalErr != null) {
            originalErr.close();
        }
    }
}