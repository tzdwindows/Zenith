package com.zenith.ui.component;

import com.zenith.common.math.Color;
import com.zenith.render.Font;
import com.zenith.ui.render.UIRenderContext;

/**
 * 文本渲染组件
 * 集成 ZenithEngine 的 UIRenderContext 绘图系统
 */
public class UITextComponent extends UIComponent {
    private String text;
    private float fontSize;
    private Color textColor = Color.WHITE;
    private Font font;

    /**
     * @param x 初始 X 坐标
     * @param y 初始 Y 坐标
     * @param text 显示文本
     * @param fontSize 字号（用于计算边界）
     * @param font 字体资源对象
     */
    public UITextComponent(float x, float y, String text, float fontSize, Font font) {
        super(x, y, 0, 0);
        this.text = text;
        this.fontSize = fontSize;
        this.font = font;
        updateBounds();
    }

    /**
     * 更新组件逻辑边界。
     * 这里的宽度计算应尽量贴合 UIRenderContext 的渲染表现。
     */
    private void updateBounds() {
        if (text == null || font == null) return;
        this.bounds.width = font.getWidth(text);
        this.bounds.height = font.getSize();
    }

    @Override
    protected void onRender(UIRenderContext ctx) {
        if (text == null || text.isEmpty()) return;
        Font font = ctx.getFont();
        ctx.setFont(this.font);
        ctx.drawText(text, bounds.x, bounds.y, textColor);
        ctx.setFont(font);
    }

    public String getText() {
        return text;
    }

    public UITextComponent setText(String text) {
        this.text = text;
        updateBounds();
        return this;
    }

    public UITextComponent setColor(Color color) {
        this.textColor = color;
        return this;
    }

    public UITextComponent setFontSize(float size) {
        this.fontSize = size;
        updateBounds();
        return this;
    }

    public Font getFont() {
        return font;
    }

    public void setFont(Font font) {
        this.font = font;
        updateBounds();
    }
}