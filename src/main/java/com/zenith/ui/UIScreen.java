package com.zenith.ui;

import com.zenith.ui.component.UIButton;
import com.zenith.ui.component.UIComponent;
import com.zenith.ui.render.UIRenderContext;
import java.util.ArrayList;
import java.util.List;

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

    public boolean onMouseMove(float mx, float my) {
        if (!visible) return false;
        boolean consumed = false;
        for (int i = components.size() - 1; i >= 0; i--) {
            UIComponent comp = components.get(i);
            float localX = mx - comp.getBounds().x;
            float localY = my - comp.getBounds().y;
            if (comp.onMouseMove(localX, localY)) {
                consumed = true;
            }
        }
        return consumed;
    }

    // 接收并向下传递 button 参数
    public boolean onMouseButton(int button, int action, float mx, float my) {
        if (!visible) return false;
        for (int i = components.size() - 1; i >= 0; i--) {
            UIComponent comp = components.get(i);
            if (comp.getBounds().contains(mx, my)) {
                float localX = mx - comp.getBounds().x;
                float localY = my - comp.getBounds().y;
                if (comp.onMouseButton(button, action, localX, localY)) {
                    return true;
                }
            }
        }
        return isModal();
    }

    // 新增：向下分发键盘按键
    public boolean onKey(int key, int scancode, int action, int mods) {
        if (!visible) return false;
        for (int i = components.size() - 1; i >= 0; i--) {
            if (components.get(i).onKey(key, scancode, action, mods)) return true;
        }
        return false;
    }

    // 新增：向下分发字符输入
    public boolean onChar(int codepoint) {
        if (!visible) return false;
        for (int i = components.size() - 1; i >= 0; i--) {
            if (components.get(i).onChar(codepoint)) return true;
        }
        return false;
    }

    public boolean onScroll(float dx, float dy, float mx, float my) {
        if (!visible) return false;
        for (int i = components.size() - 1; i >= 0; i--) {
            UIComponent comp = components.get(i);
            if (comp.getBounds().contains(mx, my)) {
                float localX = mx - comp.getBounds().x;
                float localY = my - comp.getBounds().y;
                if (comp.onScroll(dx, dy)) return true;
            }
        }
        return false;
    }


    public boolean isModal() { return false; }
    public void setVisible(boolean visible) { this.visible = visible; }
    public boolean isVisible() { return visible; }
}