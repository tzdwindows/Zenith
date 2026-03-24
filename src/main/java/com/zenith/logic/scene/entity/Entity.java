package com.zenith.logic.scene;

import com.zenith.common.math.Transform;
import com.zenith.common.utils.InternalLogger;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Zenith 引擎实体基类
 * 游戏世界中所有物体的抽象，持有空间属性和组件列表
 */
public abstract class Entity {

    private final String id;
    private String name;
    private boolean active = true;

    protected final Transform transform;
    private final List<Object> components = new ArrayList<>();

    public Entity(String name) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.transform = new Transform();
    }

    /**
     * 实体被创建时的回调
     */
    public abstract void onCreate();

    /**
     * 每帧执行的逻辑。
     * 子类重写此方法以实现具体的 AI、移动或交互逻辑。
     */
    public abstract void onUpdate(float deltaTime);

    /**
     * 实体销毁时的清理回调
     */
    public abstract void onDestroy();

    /* -------------------------------------------------------------------------- */
    /* 组件管理                                                                    */
    /* -------------------------------------------------------------------------- */

    public void addComponent(Object component) {
        if (!components.contains(component)) {
            components.add(component);
            InternalLogger.debug("实体 [" + name + "] 挂载了组件: " + component.getClass().getSimpleName());
        }
    }

    /**
     * 获取指定类型的组件
     */
    public <T> T getComponent(Class<T> componentClass) {
        for (Object component : components) {
            if (componentClass.isInstance(component)) {
                return componentClass.cast(component);
            }
        }
        return null;
    }

    /* -------------------------------------------------------------------------- */
    /* Getter & Setter                                                           */
    /* -------------------------------------------------------------------------- */

    public Transform getTransform() { return transform; }
    public String getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}