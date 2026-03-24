package com.zenith.logic.scene;

import com.zenith.common.utils.InternalLogger;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Zenith 引擎场景基类
 * 负责管理实体的生命周期和逻辑更新
 */
public abstract class Scene {

    private final String name;
    private boolean initialized = false;

    // 使用线程安全的列表，防止在 update 循环中修改列表导致崩溃
    // 如果后续为了高性能追求 ECS，这里可以替换为更复杂的存储结构
    private final List<Object> entities = new CopyOnWriteArrayList<>();

    public Scene(String name) {
        this.name = name;
    }

    /**
     * 场景初始化逻辑（加载资源、创建初始实体）
     */
    public abstract void onLoad();

    /**
     * 场景卸载逻辑（释放资源、清理监听器）
     */
    public abstract void onUnload();

    /**
     * 每帧调度的逻辑更新
     * @param deltaTime 帧间隔时间（秒）
     */
    public void update(float deltaTime) {
        if (!initialized) {
            InternalLogger.warn("场景 [" + name + "] 尚未初始化便开始更新！");
            return;
        }

        for (Object entity : entities) {
            // TODO: 调用实体的 update 逻辑
        }
    }

    /**
     * 向场景添加一个物体
     */
    public void addEntity(Object entity) {
        if (!entities.contains(entity)) {
            entities.add(entity);
            InternalLogger.debug("场景 [" + name + "]: 已添加实体 " + entity.getClass().getSimpleName());
        }
    }

    /**
     * 从场景移除一个物体
     */
    public void removeEntity(Object entity) {
        if (entities.remove(entity)) {
            InternalLogger.debug("场景 [" + name + "]: 已移除实体 " + entity.getClass().getSimpleName());
        }
    }

    /**
     * 标记场景为已就绪
     */
    public void setInitialized(boolean initialized) {
        this.initialized = initialized;
    }

    public String getName() {
        return name;
    }

    public List<Object> getEntities() {
        return entities;
    }

    public int getEntityCount() {
        return entities.size();
    }
}