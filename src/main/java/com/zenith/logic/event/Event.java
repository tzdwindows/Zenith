package com.zenith.logic.event;

/**
 * Zenith 引擎所有事件的基类
 */
public abstract class Event {

    /**
     * 获取事件的名称，默认返回类名
     */
    public String getName() {
        return getClass().getSimpleName();
    }

    /**
     * 判断该事件是否可以被取消
     * 子类如果需要被取消，应当重写此方法或继承 CancelableEvent
     */
    public boolean isCancelable() {
        return false;
    }
}