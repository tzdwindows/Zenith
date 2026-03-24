package com.zenith.logic.scene.entity;

import com.zenith.logic.scene.entity.Entity;
import com.zenith.common.math.Transform;

/**
 * Zenith 引擎组件基类
 */
public abstract class Component {

    protected Entity owner;         // 宿主实体（肢体所属的身体）
    private boolean enabled = true; // 是否启用

    /**
     * 设置组件的宿主实体
     * 由 Entity.addComponent 自动调用
     */
    public void setOwner(Entity owner) {
        this.owner = owner;
    }

    /**
     * 组件被创建时的初始化逻辑
     */
    public abstract void onCreate();

    /**
     * 每帧执行的逻辑
     */
    public abstract void onUpdate(float deltaTime);

    /**
     * 组件销毁时的清理逻辑
     */
    public abstract void onDestroy();

    /* -------------------------------------------------------------------------- */
    /* 工具方法                                                                    */
    /* -------------------------------------------------------------------------- */

    public Transform getTransform() {
        return owner != null ? owner.getTransform() : null;
    }

    public Entity getOwner() { return owner; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}