package com.zenith.common.utils;

public class AWTDebugHandler {
    public void handle(Throwable t) {
        InternalLogger.error("检测到 AWT 内部崩溃: ", t);
    }
}
