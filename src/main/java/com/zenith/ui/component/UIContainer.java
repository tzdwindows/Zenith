package com.zenith.ui.component;

import com.zenith.common.math.Rectf;
import com.zenith.render.backend.opengl.GLMaterial;
import com.zenith.render.backend.opengl.shader.GLShaderRegistry;
import com.zenith.ui.render.UIRenderContext;
import com.zenith.render.backend.opengl.shader.SolidColorUIShader;

import java.util.ArrayList;
import java.util.List;

/**
 * 可以包含其他组件的容器组件。
 */
public class UIContainer extends UIComponent {

    protected final List<UIComponent> children = new ArrayList<>();

    public UIContainer(float x, float y, float width, float height) {
        super(x, y, width, height);
    }

    public void addChild(UIComponent child) {
        child.setParent(this);
        children.add(child);
    }

    public void removeChild(UIComponent child) {
        if (children.remove(child)) {
            child.setParent(null);
        }
    }

    @Override
    protected void onRender(UIRenderContext ctx) {
        ctx.pushMaterial(new GLMaterial(GLShaderRegistry.get(GLShaderRegistry.UI_SOLID)));
        if (backgroundColor.a > 0) {
            ctx.drawRect(new Rectf(bounds.x, bounds.y, bounds.width, bounds.height), backgroundColor);
        }
        ctx.popMaterial();
        for (UIComponent child : children) {
            child.render(ctx);
        }

    }

    @Override
    public void update(float deltaTime) {
        for (UIComponent child : children) {
            child.update(deltaTime);
        }
    }

    public List<UIComponent> getChildren() {
        return children;
    }
}