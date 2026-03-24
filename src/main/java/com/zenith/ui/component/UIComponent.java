package com.zenith.ui.component;

import com.zenith.common.math.Color;
import com.zenith.common.math.Rectf;
import com.zenith.ui.render.UIRenderContext;

/**
 * UI 系统所有组件的基类。
 */
public abstract class UIComponent {

    protected Rectf bounds;
    protected boolean visible = true;
    protected Color backgroundColor = new Color(0, 0, 0, 0); // 默认透明
    protected UIComponent parent;

    public UIComponent(float x, float y, float width, float height) {
        this.bounds = new Rectf(x, y, width, height);
    }

    /**
     * 核心渲染入口（模板方法模式）
     * 负责管理坐标空间的切换。
     */
    public final void render(UIRenderContext ctx) {
        if (!visible) return;

        // 1. 进入局部坐标系：将原点移至组件的左上角 (bounds.x, bounds.y)
        // 这样子类在 onRender 中只需要关注 (0, 0) 开始的坐标
        ctx.pushTransform(bounds.x, bounds.y);

        // 2. 调用子类实现的绘制逻辑
        onRender(ctx);

        // 3. 恢复之前的坐标系
        ctx.popTransform();
    }

    /**
     * 子类实现此方法。
     * 重要：在此方法内绘制时，请使用局部坐标 (0, 0, width, height)。
     */
    protected abstract void onRender(UIRenderContext ctx);

    /**
     * 逻辑更新（处理动画、状态改变等）
     */
    public void update(float deltaTime) {
        // 默认不执行任何操作
    }

    /* --- 事件处理 --- */

    /**
     * 处理鼠标移动。
     * @return 如果组件消耗了此事件（例如处理了 Hover 状态），返回 true。
     */
    public boolean onMouseMove(float mx, float my) {
        return false;
    }

    /**
     * 处理鼠标点击。
     * @param action GLFW_PRESS 或 GLFW_RELEASE
     * @param mx 相对组件自身的局部坐标 X
     * @param my 相对组件自身的局部坐标 Y
     * @return 如果点击发生在组件有效区域并被处理，返回 true。
     */
    public boolean onMouseButton(int action, float mx, float my) {
        return false;
    }

    /* --- Getters & Setters --- */

    public Rectf getBounds() { return bounds; }
    public void setVisible(boolean visible) { this.visible = visible; }
    public boolean isVisible() { return visible; }
    public void setBackgroundColor(Color color) { this.backgroundColor = color; }
    public UIComponent getParent() { return parent; }
    public void setParent(UIComponent parent) { this.parent = parent; }
}