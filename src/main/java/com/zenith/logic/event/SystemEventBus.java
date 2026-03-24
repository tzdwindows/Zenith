package com.zenith.logic.event;

/**
 * 系统事件总线
 */
public class SystemEventBus {

    private static final EventBus bus = new EventBus();

    /**
     * 获取全局事件总线实例
     */
    public static EventBus get() {
        return bus;
    }

    /**
     * 发布事件的快捷方法
     */
    public static void post(Event event) {
        bus.post(event);
    }
}