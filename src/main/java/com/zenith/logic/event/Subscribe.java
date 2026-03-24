package com.zenith.logic.event;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 模仿 MC 的事件订阅注解
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Subscribe {
    // 可以扩展优先级，类似 MC 的 EventPriority
    EventPriority priority() default EventPriority.NORMAL;
}