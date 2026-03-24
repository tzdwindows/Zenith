package com.zenith.ui;

import com.zenith.ui.component.UIComponent;
import com.zenith.ui.render.UIRenderContext;
import java.util.ArrayList;
import java.util.List;

/**
 * UI 屏幕/画布抽象类，用于管理一组 UI 组件
 */
public abstract class UIScreen {
    protected final List<UIComponent> components = new ArrayList<>();
    protected boolean visible = true;

    public void addComponent(UIComponent component) {
        components.add(component);
    }

    public void update(float deltaTime) {
        if (!visible) return;
        for (UIComponent comp : components) {
            comp.update(deltaTime);
        }
    }

    public void render(UIRenderContext ctx) {
        if (!visible) return;
        for (UIComponent comp : components) {
            comp.render(ctx);
        }
    }

    /**
     * @return 如果事件被消耗（点击到了组件），返回 true
     */
    public boolean onMouseMove(float mx, float my) {
        if (!visible) return false;
        boolean consumed = false;
        for (int i = components.size() - 1; i >= 0; i--) {
            UIComponent comp = components.get(i);
            float bx = mx - comp.getBounds().x;
            float by = my - comp.getBounds().y;
            if (comp.onMouseMove(bx, by)) {
                consumed = true;
            }
        }
        return consumed;
    }

    /**
     * @return 如果点击了组件或 UI 背景，返回 true 以拦截事件
     */
    public boolean onMouseButton(int action, float mx, float my) {
        if (!visible) return false;

        // 1. 检查是否点击到了某个组件
        for (int i = components.size() - 1; i >= 0; i--) {
            UIComponent comp = components.get(i);
            // 简单的碰撞检测：检查鼠标是否在组件边界内
            if (comp.getBounds().contains(mx, my)) {
                float bx = mx - comp.getBounds().x;
                float by = my - comp.getBounds().y;
                comp.onMouseButton(action, bx, by);
                return true; // 拦截事件，不再传给下层 Screen
            }
        }

        // 2. 如果你的 Screen 是全屏模态窗口（如主菜单），即使没点到按钮也要拦截
        return isModal();
    }

    /**
     * 子类可覆盖此方法。如果返回 true，即使没点到按钮，点击也不会穿透到下层。
     */
    public boolean isModal() { return false; }

    public void setVisible(boolean visible) { this.visible = visible; }
    public boolean isVisible() { return visible; }
}