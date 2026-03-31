package com.zenith.ui.component;

import com.zenith.common.math.Color;
import com.zenith.ui.render.TextureAtlas;
import com.zenith.ui.render.UIRenderContext;

public class UIButton extends UIComponent {
    private final TextureAtlas atlas;
    private String spriteName;

    private boolean isHovered = false;
    private boolean isPressed = false;

    // 样式参数
    private Color normalColor = Color.WHITE;
    private Color hoverColor = new Color(0.85f, 0.85f, 0.85f, 1.0f);
    private Color pressedColor = new Color(0.6f, 0.6f, 0.6f, 1.0f);
    private float pressedScale = 0.95f;

    // Tooltip 与交互位置
    private String tooltipText = null;
    private float mouseX, mouseY;

    // 点击事件回调
    private Runnable onClickAction;
    private String labelText = null;

    public UIButton(float x, float y, float width, float height, TextureAtlas atlas, String spriteName) {
        super(x, y, width, height);
        this.atlas = atlas;
        this.spriteName = spriteName;
    }

    public UIButton setLabel(String text) {
        this.labelText = text;
        return this;
    }


    /* --- 实现缺失的方法 (支持链式调用) --- */

    public UIButton setColors(Color normal, Color hover, Color pressed) {
        this.normalColor = normal;
        this.hoverColor = hover;
        this.pressedColor = pressed;
        return this;
    }

    public UIButton setPressedScale(float scale) {
        this.pressedScale = scale;
        return this;
    }

    public UIButton setTooltip(String text) {
        this.tooltipText = text;
        return this;
    }

    public UIButton setOnClick(Runnable action) {
        this.onClickAction = action;
        return this;
    }

    @Override
    public void onRender(UIRenderContext ctx) {
        // --- 只渲染按钮背景和文字 ---
        Color drawColor = isPressed ? pressedColor : (isHovered ? hoverColor : normalColor);
        float scale = isPressed ? pressedScale : 1.0f;

        ctx.pushTransform();
        float cx = bounds.x + bounds.width / 2f;
        float cy = bounds.y + bounds.height / 2f;
        ctx.translate(cx, cy);
        ctx.scale(scale, scale);
        ctx.translate(-bounds.width / 2f, -bounds.height / 2f);

        if (atlas != null && spriteName != null) {
            ctx.drawSprite(atlas, spriteName, 0, 0, bounds.width, bounds.height, drawColor);
        }

        if (labelText != null && ctx.getFont() != null) {
            float textWidth = ctx.getFont().getWidth(labelText);
            float textHeight = ctx.getFont().getSize();
            float tx = (bounds.width - textWidth) / 2f;
            float ty = (bounds.height + textHeight * 0.7f) / 2f;
            ctx.drawText(labelText, tx, ty, new Color(0.1f, 0.1f, 0.1f, 1.0f));
        }
        ctx.popTransform();
    }

    /**
     * 新增：专门用于渲染 Tooltip 的方法
     */
    public void renderOverlay(UIRenderContext ctx) {
        if (isHovered && tooltipText != null) {
            ctx.drawTooltip(tooltipText, mouseX, mouseY);
        }
    }

    @Override
    public boolean onMouseMove(float mx, float my) {
        this.mouseX = mx;
        this.mouseY = my;
        this.isHovered = bounds.contains(mx, my);
        return isHovered;
    }

    @Override
    public boolean onMouseButton(int action, float mx, float my) {
        boolean inside = bounds.contains(mx, my);
        if (action == 1 && inside) { // 按下
            isPressed = true;
            return true;
        } else if (action == 0) { // 抬起
            if (isPressed && inside && onClickAction != null) {
                onClickAction.run();
            }
            isPressed = false;
        }
        return false;
    }
}