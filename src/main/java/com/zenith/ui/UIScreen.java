package com.zenith.ui;

import com.zenith.ui.component.UIButton;
import com.zenith.ui.component.UIComponent;
import com.zenith.ui.render.UIRenderContext;
import java.util.ArrayList;
import java.util.List;

/**
 * UI 屏幕基类 - 彻底修复右下角偏移问题
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
            if (comp instanceof UIButton btn) {
                btn.onRender(ctx);
            } else {
                comp.render(ctx);
            }
        }

        for (UIComponent comp : components) {
            if (comp instanceof UIButton btn) {
                btn.renderOverlay(ctx);
            }
        }
    }

    /**
     * 鼠标移动：直接传递绝对屏幕坐标
     */
    public boolean onMouseMove(float mx, float my) {
        if (!visible) return false;
        boolean consumed = false;
        for (int i = components.size() - 1; i >= 0; i--) {
            UIComponent comp = components.get(i);
            // 绝对不能减去 bounds.x，直接传 mx, my
            if (comp.onMouseMove(mx, my)) {
                consumed = true;
            }
        }
        return consumed;
    }

    /**
     * 鼠标点击：直接传递绝对屏幕坐标
     */
    public boolean onMouseButton(int action, float mx, float my) {
        if (!visible) return false;

        for (int i = components.size() - 1; i >= 0; i--) {
            UIComponent comp = components.get(i);
            if (comp.getBounds().contains(mx, my)) {
                // 绝对不能减去 bounds.x，直接传 mx, my
                comp.onMouseButton(action, mx, my);
                return true;
            }
        }
        return isModal();
    }

    public boolean isModal() { return false; }
    public void setVisible(boolean visible) { this.visible = visible; }
    public boolean isVisible() { return visible; }
}