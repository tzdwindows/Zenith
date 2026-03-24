package com.zenith.logic.scene.entity;

import com.zenith.common.math.Transform;
import com.zenith.common.utils.InternalLogger;
import com.zenith.logic.component.Component;
import com.zenith.logic.component.Collider;
import com.zenith.logic.component.AIController;
import com.zenith.logic.component.RenderComponent;
import com.zenith.logic.event.EntityDamagedEvent;
import com.zenith.logic.event.SystemEventBus;

import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Zenith 引擎实体基类
 * 整合了空间变换、多部位受击判定、AI 感知以及渲染组件关联。
 */
public abstract class Entity {
    /** 实体全局唯一标识 **/
    private final String id;

    /** 实体的显示名称 **/
    private String name;

    /** 控制实体是否参与逻辑更新与渲染 **/
    private boolean active = true;

    /** 空间变换信息：位置、旋转与缩放 **/
    protected final Transform transform;

    /** 渲染组件引用，若为 null 则该实体在世界中不可见 **/
    private RenderComponent renderComponent;

    /** 实体的主碰撞箱，默认受击判定点 **/
    private Collider primaryCollider;

    /** 所有关联的碰撞箱列表，用于精细化部位伤害判定 **/
    private final List<Collider> colliders = new CopyOnWriteArrayList<>();

    /** 关联的 AI 控制器 **/
    private AIController aiController;

    /** 实体的战斗属性数据（生命、攻击、防御、经验值等） **/
    protected final EntityStats stats;

    /** 挂载在实体上的所有组件列表 **/
    private final List<Component> components = new CopyOnWriteArrayList<>();

    /** 用于逻辑分类与检索的标签集合 **/
    private final Set<String> tags = new HashSet<>();

    /** 父级实体引用，用于场景图层级管理 **/
    private Entity parent;

    /** 子实体列表，父实体的变换将影响所有子实体 **/
    private final List<Entity> children = new CopyOnWriteArrayList<>();

    /**
     * 构造一个新的 Zenith 实体并初始化基础属性
     * @param name 实体名称
     * @param maxHealth 初始生命上限
     * @param baseAttack 基础攻击力
     * @param baseDefense 基础防御力
     */
    public Entity(String name, float maxHealth, float baseAttack, float baseDefense) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.transform = new Transform();
        this.stats = new EntityStats(maxHealth, baseAttack, baseDefense);
        InternalLogger.debug("创建实体: " + name + " [ID: " + id.substring(0, 8) + "...]");
    }

    /* -------------------------------------------------------------------------- */
    /* 核心战斗与伤害分发逻辑                                                       */
    /* -------------------------------------------------------------------------- */

    /**
     * 默认受击接口，将伤害应用至主碰撞箱（通常为身体）
     * @param amount 原始伤害
     * @param source 攻击者
     */
    public void takeDamage(float amount, Entity source) {
        takeDamage(amount, source, primaryCollider);
    }

    /**
     * 精确受击接口，支持基于碰撞部位的伤害加成算法
     * @param amount 原始伤害
     * @param source 攻击者
     * @param hitCollider 实际被击中的碰撞组件
     */
    public void takeDamage(float amount, Entity source, Collider hitCollider) {
        if (!active || stats.isDead()) return;

        /** 计算部位倍率（如头部暴击） **/
        float partMultiplier = 1.0f;
        if (hitCollider != null) {
            partMultiplier = hitCollider.getPart().getMultiplier();
            if (hitCollider.getPart() == Collider.HitPart.HEAD) {
                InternalLogger.debug("!!! [爆头] !!! 实体 [" + name + "] 受到致命打击");
            }
        }

        float finalRawAmount = amount * partMultiplier;
        float calculatedDamage = stats.calculateDamage(finalRawAmount);

        /** 发布受击事件，允许外部系统拦截或修改伤害数值 **/
        EntityDamagedEvent event = new EntityDamagedEvent(this, source, calculatedDamage);
        SystemEventBus.post(event);

        if (event.isCanceled()) {
            InternalLogger.debug("实体 [" + name + "] 的伤害已被拦截");
            return;
        }

        float actualDamage = stats.takeDamage(event.getAmount());

        if (actualDamage > 0) {
            InternalLogger.debug("实体 [" + name + "] 受伤: " + String.format("%.1f", actualDamage));
        }

        /** 感知反馈给 AI 控制器 **/
        if (hasAI()) {
            aiController.onDamaged(source, actualDamage);
        }

        if (stats.isDead()) {
            onDeath(source);
        }
    }

    /**
     * 实体死亡时的逻辑结算
     * 负责发布死亡事件并进行清理工作
     * @param killer 击杀者引用
     */
    protected void onDeath(Entity killer) {
        InternalLogger.info("实体 [" + name + "] 已死亡" +
                (killer != null ? "，被 " + killer.getName() + " 击杀" : ""));
        onDestroy();
    }

    /* -------------------------------------------------------------------------- */
    /* 组件生命周期管理                                                            */
    /* -------------------------------------------------------------------------- */

    /**
     * 实体被创建时的初始化钩子，由子类实现
     */
    public abstract void onCreate();

    /**
     * 驱动实体及其所有组件的逻辑更新
     * @param deltaTime 帧步进时间
     */
    public void onUpdate(float deltaTime) {
        if (!active) return;
        for (Component component : components) {
            if (component.isEnabled()) {
                component.onUpdate(deltaTime);
            }
        }
        for (Entity child : children) {
            child.onUpdate(deltaTime);
        }
    }

    /**
     * 执行实体的彻底销毁与资源释放
     */
    public void onDestroy() {
        InternalLogger.debug("正在注销实体资源: " + name);
        for (Entity child : children) {
            child.onDestroy();
        }
        children.clear();
        for (Component component : components) {
            component.onDestroy();
        }
        components.clear();
        colliders.clear();

        if (parent != null) {
            parent.children.remove(this);
        }

        primaryCollider = null;
        aiController = null;
        renderComponent = null;
    }

    /**
     * 向实体注入新组件并自动维护核心系统引用
     * @param component 目标组件
     */
    public void addComponent(Component component) {
        if (component == null) return;
        if (!components.contains(component)) {
            components.add(component);
            component.setOwner(this);

            /** 自动分类组件引用 **/
            switch (component) {
                case Collider c -> {
                    this.colliders.add(c);
                    if (primaryCollider == null) primaryCollider = c;
                }
                case AIController controller -> this.aiController = controller;
                case RenderComponent rc -> this.renderComponent = rc;
                default -> {
                }
            }

            component.onCreate();
            InternalLogger.debug("组件已挂载: " + component.getClass().getSimpleName());
        }
    }

    /**
     * 移除组件并更新相关系统引用
     * @param component 待移除的组件
     */
    public void removeComponent(Component component) {
        if (components.remove(component)) {
            if (component instanceof Collider) {
                colliders.remove(component);
                if (primaryCollider == component) {
                    primaryCollider = colliders.isEmpty() ? null : colliders.get(0);
                }
            }
            if (component == aiController) aiController = null;
            if (component == renderComponent) renderComponent = null;

            component.onDestroy();
            component.setOwner(null);
        }
    }

    /* -------------------------------------------------------------------------- */
    /* 空间查询与感知工具                                                           */
    /* -------------------------------------------------------------------------- */

    /**
     * 计算到另一个实体的平方距离，避免了开方运算以提升性能
     * @param other 目标实体
     * @return 欧氏距离的平方
     */
    public float getDistanceSq(Entity other) {
        return transform.getPosition().distanceSquared(other.getTransform().getPosition());
    }

    /**
     * 判断特定世界坐标是否落在实体的任何碰撞箱内
     * @param worldPoint 世界空间位置
     * @return 是否发生重叠
     */
    public boolean isPointInside(org.joml.Vector3f worldPoint) {
        if (!active || colliders.isEmpty()) return false;
        for (Collider c : colliders) {
            if (c.isEnabled() && c.containsPoint(worldPoint)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取指定点命中的具体碰撞体组件
     * @param worldPoint 世界空间位置
     * @return 命中的 Collider 实例，未命中则返回 null
     */
    public Collider getHitColliderAt(org.joml.Vector3f worldPoint) {
        if (!active) return null;
        for (Collider c : colliders) {
            if (c.isEnabled() && c.containsPoint(worldPoint)) {
                return c;
            }
        }
        return null;
    }

    /* -------------------------------------------------------------------------- */
    /* 属性访问器与快捷工具                                                         */
    /* -------------------------------------------------------------------------- */

    public RenderComponent getRenderComponent() { return renderComponent; }

    /** 检查实体是否具备渲染能力 **/
    public boolean isRenderable() { return renderComponent != null && active; }

    public Collider getCollider() { return primaryCollider; }
    public List<Collider> getColliders() { return colliders; }
    public AIController getAI() { return aiController; }
    public EntityStats getStats() { return stats; }
    public Entity getParent() { return parent; }
    public List<Entity> getChildren() { return children; }

    public boolean hasCollider() { return !colliders.isEmpty(); }
    public boolean hasAI() { return aiController != null && aiController.isEnabled(); }

    public <T extends Component> T getComponent(Class<T> componentClass) {
        for (Component component : components) {
            if (componentClass.isInstance(component)) {
                return componentClass.cast(component);
            }
        }
        return null;
    }

    public Transform getTransform() { return transform; }
    public String getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public void setParent(Entity parent) {
        if (this.parent != null) this.parent.children.remove(this);
        this.parent = parent;
        if (this.parent != null) this.parent.children.add(this);
    }

    public void addTag(String tag) { tags.add(tag); }
    public void removeTag(String tag) { tags.remove(tag); }
    public boolean hasTag(String tag) { return tags.contains(tag); }
}