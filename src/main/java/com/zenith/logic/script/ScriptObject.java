package com.zenith.logic.script;

import org.graalvm.polyglot.Value;

/**
 * 代表一个可运行的脚本实例，通常包含特定的回调函数（如 onUpdate, onInit）
 */
public class ScriptObject {
    private final Value jsInstance;

    public ScriptObject(Value jsInstance) {
        this.jsInstance = jsInstance;
    }

    public void invoke(String methodName, Object... args) {
        Value method = jsInstance.getMember(methodName);
        if (method != null && method.canExecute()) {
            method.execute(args);
        }
    }
}