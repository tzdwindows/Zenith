package com.zenith.ui.component;

import com.zenith.common.math.Color;
import com.zenith.common.math.Rectf;
import com.zenith.ui.render.UIRenderContext;

public abstract class UIComponent {

    protected Rectf bounds;
    protected boolean visible = true;
    protected Color backgroundColor = new Color(0, 0, 0, 0); // 默认透明
    protected UIComponent parent;

    public UIComponent(float x, float y, float width, float height) {
        this.bounds = new Rectf(x, y, width, height);
    }

    public final void render(UIRenderContext ctx) {
        if (!visible) return;
        ctx.pushTransform(bounds.x, bounds.y);
        onRender(ctx);
        ctx.popTransform();
    }

    protected abstract void onRender(UIRenderContext ctx);

    public void update(float deltaTime) {}


    public boolean onMouseMove(float mx, float my) {
        return false;
    }

    // 增加了 button 参数 (0:左键, 1:右键, 2:中键)
    public boolean onMouseButton(int button, int action, float mx, float my) {
        return false;
    }

    // 新增：键盘功能键事件 (退格、回车、方向键等)
    public boolean onKey(int key, int scancode, int action, int mods) {
        return false;
    }

    // 新增：字符输入事件 (打字)
    public boolean onChar(int codepoint) {
        return false;
    }

    /* --- Getters & Setters --- */
    public Rectf getBounds() { return bounds; }
    public void setVisible(boolean visible) { this.visible = visible; }
    public boolean isVisible() { return visible; }
    public void setBackgroundColor(Color color) { this.backgroundColor = color; }
    public UIComponent getParent() { return parent; }
    public void setParent(UIComponent parent) { this.parent = parent; }

    public boolean onScroll(float dx, float dy) {
        return false;
    }
}