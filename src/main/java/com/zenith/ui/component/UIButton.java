package com.zenith.ui.component;

import com.zenith.common.math.Color;
import com.zenith.common.math.Rectf;
import com.zenith.ui.render.TextureAtlas;
import com.zenith.ui.render.UIRenderContext;

public class UIButton extends UIComponent {

    private final TextureAtlas atlas;
    private String spriteName;

    // --- 状态 ---
    private boolean isHovered = false;
    private boolean isPressed = false;
    private boolean pressedInside = false;

    // --- 外观配置 ---
    private Color normalColor = Color.WHITE;
    private Color hoverColor = new Color(0.9f, 0.9f, 0.9f, 1.0f);
    private Color pressedColor = new Color(0.75f, 0.75f, 0.75f, 1.0f);

    // 🌟 边框配置
    private boolean showBorder = true; // 新增：边框显示开关
    private Color borderColor = new Color(0f, 0f, 0f, 0.5f);
    private float borderWidth = 1.0f;

    private float pressedScale = 0.96f;

    // 背景扩展量（解决贴图填不满的问题）
    private float bgExpandX = 0f;
    private float bgExpandY = 0f;

    // --- 文本 ---
    private float padding = 6f;
    private float textScale = 0.85f;
    private String labelText = null;

    private String tooltipText = null;
    private float mouseX, mouseY;
    private Runnable onClickAction;

    public UIButton(float x, float y, float width, float height,
                    TextureAtlas atlas, String spriteName) {
        super(x, y, width, height);
        this.atlas = atlas;
        this.spriteName = spriteName;
    }

    // 开启或禁用边框
    public UIButton setBorderEnabled(boolean show) {
        this.showBorder = show;
        return this;
    }

    // 设置背景补偿拉伸
    public UIButton setBgExpand(float expandX, float expandY) {
        this.bgExpandX = expandX;
        this.bgExpandY = expandY;
        return this;
    }

    public UIButton setLabel(String text) {
        this.labelText = text;
        return this;
    }

    public UIButton setOnClick(Runnable action) {
        this.onClickAction = action;
        return this;
    }

    @Override
    public void onRender(UIRenderContext ctx) {
        Color drawColor = isPressed ? pressedColor : (isHovered ? hoverColor : normalColor);
        float scale = isPressed ? pressedScale : 1.0f;

        ctx.pushTransform();

        ctx.translate(bounds.x, bounds.y);

        float halfW = bounds.width / 2f;
        float halfH = bounds.height / 2f;
        ctx.translate(halfW, halfH);
        ctx.scale(scale, scale);
        ctx.translate(-halfW, -halfH);

        if (atlas != null && spriteName != null) {
            ctx.drawSprite(atlas, spriteName,
                    -bgExpandX,
                    -bgExpandY,
                    bounds.width + bgExpandX * 2,
                    bounds.height + bgExpandY * 2,
                    drawColor);
        } else {
            ctx.drawRect(new Rectf(0, 0, bounds.width, bounds.height), drawColor);
        }

        if (showBorder && borderWidth > 0f) {
            ctx.drawRectOutline(0, 0, bounds.width, bounds.height, borderWidth, borderColor);
        }

        if (labelText != null && ctx.getFont() != null) {
            float baseWidth = ctx.getFont().getWidth(labelText);
            float baseHeight = ctx.getFont().getSize();
            float availableWidth = bounds.width - padding * 2;
            float finalScale = textScale;

            if (baseWidth * finalScale > availableWidth) {
                finalScale *= (availableWidth / (baseWidth * finalScale));
            }

            float finalWidth = baseWidth * finalScale;
            float finalHeight = baseHeight * finalScale;
            float tx = (bounds.width - finalWidth) / 2f;
            float ty = (bounds.height - finalHeight) / 2f + baseHeight * 0.75f * finalScale;

            ctx.drawText(labelText, tx, ty, new Color(0.1f, 0.1f, 0.1f, 1.0f));
        }

        ctx.popTransform();
    }

    public void renderOverlay(UIRenderContext ctx) {
        if (isHovered && !pressedInside && tooltipText != null) {
            ctx.drawTooltip(tooltipText, mouseX, mouseY);
        }
    }

    @Override
    public boolean onMouseMove(float mx, float my) {
        this.mouseX = mx;
        this.mouseY = my;
        boolean wasHovered = this.isHovered;
        this.isHovered = bounds.contains(mx, my);

        this.isPressed = this.pressedInside && this.isHovered;

        return isHovered || wasHovered;
    }

    @Override
    public boolean onMouseButton(int action, float mx, float my) {
        boolean inside = bounds.contains(mx, my);

        if (action == 1) {
            if (inside) {
                this.isPressed = true;
                this.pressedInside = true;
                return true;
            }
        } else if (action == 0) {
            if (this.pressedInside) {
                if (inside && onClickAction != null) {
                    onClickAction.run();
                }
                this.isPressed = false;
                this.pressedInside = false;
                return true;
            }
        }
        return false;
    }

    public UIButton setTooltip(String text) {
        this.tooltipText = text;
        return this;
    }

    public UIButton setPressedScale(float scale) {
        this.pressedScale = scale;
        return this;
    }

    public UIButton setColors(Color normal, Color hover, Color pressed) {
        this.normalColor = normal;
        this.hoverColor = hover;
        this.pressedColor = pressed;
        return this;
    }
}