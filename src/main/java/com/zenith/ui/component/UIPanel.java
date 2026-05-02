package com.zenith.ui.component;

import com.zenith.common.math.Color;
import com.zenith.common.math.Rectf;
import com.zenith.ui.layout.VerticalLayout;
import com.zenith.ui.render.UIRenderContext;

/**
 * UIPanel 是最基础的容器组件，通常用于作为背景、窗口或布局容器。
 */
public class UIPanel extends UIContainer {
    private VerticalLayout layout;
    private boolean drawBackground = true;
    private boolean clipChildren = false; // 是否裁剪超出边界的子组件

    public UIPanel(float x, float y, float width, float height) {
        super(x, y, width, height);
        // 默认面板背景色（深灰色半透明，适合 UI）
        this.backgroundColor = new Color(0.15f, 0.15f, 0.15f, 0.8f);
    }

    public UIPanel(float x, float y, float width, float height, Color backgroundColor) {
        this(x, y, width, height);
        this.backgroundColor = backgroundColor;
    }

    public void setLayout(VerticalLayout layout) {
        this.layout = layout;
    }

    @Override
    protected void onRender(UIRenderContext ctx) {
        // 1. 绘制面板背景
        // 此时 Transform 已经在 (bounds.x, bounds.y)，所以画 (0, 0, width, height)
        if (drawBackground && backgroundColor.a > 0) {
            ctx.drawRect(new Rectf(0, 0, bounds.width, bounds.height), backgroundColor);
        }

        // 2. 如果开启了裁剪，此处应通知 Context 开启 Scissor Test
        // 目前你的 Context 还没实现 Scissor，先预留逻辑位置
        if (clipChildren) {
            // ctx.pushScissor(0, 0, bounds.width, bounds.height);
        }

        // 3. 调用父类 UIContainer 的逻辑来渲染所有子组件
        super.onRender(ctx);

        if (clipChildren) {
            // ctx.popScissor();
        }
    }

    @Override
    public void update(float deltaTime) {
        // 在更新子组件之前应用布局
        if (layout != null) {
            layout.updateLayout(this);
        }

        for (UIComponent child : children) {
            child.update(deltaTime);
        }
    }

    /* --- 功能扩展 --- */

    public void setDrawBackground(boolean drawBackground) {
        this.drawBackground = drawBackground;
    }

    public void setClipChildren(boolean clipChildren) {
        this.clipChildren = clipChildren;
    }

    /**
     * 辅助方法：快速添加多个组件
     */
    public void addChildren(UIComponent... components) {
        for (UIComponent c : components) {
            this.addChild(c);
        }
    }

    @Override
    public boolean onMouseMove(float mx, float my) {
        boolean consumed = false;
        for (int i = children.size() - 1; i >= 0; i--) {
            UIComponent child = children.get(i);
            if (!child.isVisible()) continue;
            float childX = mx - child.getBounds().x;
            float childY = my - child.getBounds().y;
            if (child.onMouseMove(childX, childY)) {
                consumed = true;
                break;
            }
        }
        boolean insideSelf = mx >= 0 && mx <= bounds.width && my >= 0 && my <= bounds.height;
        return consumed || insideSelf;
    }

    @Override
    public boolean onMouseButton(int button, int action, float mx, float my) {
        for (int i = children.size() - 1; i >= 0; i--) {
            UIComponent child = children.get(i);
            if (!child.isVisible()) continue;
            if (child.getBounds().contains(mx, my)) {
                float childX = mx - child.getBounds().x;
                float childY = my - child.getBounds().y;
                if (child.onMouseButton(button, action, childX, childY)) {
                    return true;
                }
            }
        }
        return mx >= 0 && mx <= bounds.width && my >= 0 && my <= bounds.height;
    }


    @Override
    public boolean onScroll(float dx, float dy) {
        for (int i = children.size() - 1; i >= 0; i--) {
            if (children.get(i).isVisible() && children.get(i).onScroll(dx, dy)) {
                return true;
            }
        }
        return false;
    }
}