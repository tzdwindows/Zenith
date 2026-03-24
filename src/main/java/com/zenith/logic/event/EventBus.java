package com.zenith.logic.event;

import com.zenith.common.utils.InternalLogger;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class EventBus {

    private final Map<Class<? extends Event>, List<EventExecutor>> listeners = new ConcurrentHashMap<>();

    /**
     * 注册监听器对象
     * @param object 包含 @Subscribe 方法的实例
     */
    public void register(Object object) {
        Class<?> clazz = object.getClass();
        int addedCount = 0;

        for (Method method : clazz.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Subscribe.class) && method.getParameterCount() == 1) {
                Class<?> paramType = method.getParameterTypes()[0];
                if (Event.class.isAssignableFrom(paramType)) {
                    @SuppressWarnings("unchecked")
                    Class<? extends Event> eventType = (Class<? extends Event> ) paramType;
                    Subscribe sub = method.getAnnotation(Subscribe.class);
                    List<EventExecutor> eventExecutors = listeners.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>());
                    eventExecutors.add(new EventExecutor(object, method, sub.priority()));
                    eventExecutors.sort((e1, e2) -> Integer.compare(e2.priority.ordinal(), e1.priority.ordinal()));
                    addedCount++;
                }
            }
        }

        if (addedCount > 0) {
            InternalLogger.debug("已注册监听器类: " + clazz.getSimpleName() + " (共 " + addedCount + " 个订阅方法)");
        }
    }

    /**
     * 发布事件
     * @param event 事件实例
     */
    public void post(Event event) {
        List<EventExecutor> executors = listeners.get(event.getClass());
        if (executors == null || executors.isEmpty()) {
            return;
        }

        for (EventExecutor executor : executors) {
            if (event instanceof CancelableEvent && ((CancelableEvent) event).isCanceled()) {
                InternalLogger.debug("事件 [" + event.getName() + "] 已被拦截，停止分发给后续监听器");
                break;
            }

            try {
                executor.method.setAccessible(true);
                executor.method.invoke(executor.object, event);
            } catch (Exception e) {
                InternalLogger.error("事件分发失败! 事件类型: " + event.getName() +
                        " | 监听器类: " + executor.object.getClass().getName(), e);
            }
        }
    }

    /**
     * 内部包装类
     */
    private static class EventExecutor {
        final Object object;
        final Method method;
        final EventPriority priority;
        EventExecutor(Object object, Method method, EventPriority priority) {
            this.object = object;
            this.method = method;
            this.priority = priority;
        }
    }
}