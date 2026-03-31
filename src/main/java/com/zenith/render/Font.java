package com.zenith.render;

import com.zenith.common.utils.InternalLogger;

public abstract class Font {
    protected String fontName;
    protected int fontSize;
    protected float scaleX = 1.0f;
    protected float scaleY = 1.0f;

    protected Font(String fontName, int fontSize) {
        this.fontName = fontName;
        this.fontSize = fontSize;
        InternalLogger.info("Loading font: " + fontName + " at size " + fontSize);
    }

    /**
     * 抽象复制方法。
     * 子类应实现此方法以返回一个包含相同纹理数据但拥有独立状态（如 scale）的新实例。
     */
    public abstract Font copy();

    /**
     * 辅助方法：将当前缩放状态同步到目标 Font 实例
     */
    protected void copyStateTo(Font other) {
        other.scaleX = this.scaleX;
        other.scaleY = this.scaleY;
    }

    public abstract Texture getTexture();

    public abstract Glyph getGlyph(char c);

    public abstract void setFontSize(int fontSize);

    public abstract float getWidth(String text);

    public abstract void dispose();

    public int getSize() {
        return fontSize;
    }

    public void setScale(float scaleX, float scaleY) {
        this.scaleX = scaleX;
        this.scaleY = scaleY;
    }

    public float getScaleX() { return scaleX; }
    public float getScaleY() { return scaleY; }

    public static class Glyph {
        public float u, v, u2, v2;
        public float width, height;
        public float offsetX, offsetY;
        public float advance;
        public Glyph() {}

        public Glyph(Glyph other) {
            this.u = other.u;
            this.v = other.v;
            this.u2 = other.u2;
            this.v2 = other.v2;
            this.width = other.width;
            this.height = other.height;
            this.offsetX = other.offsetX;
            this.offsetY = other.offsetY;
            this.advance = other.advance;
        }

        public void applyScale(float sx, float sy) {
            this.width *= sx;
            this.height *= sy;
            this.offsetX *= sx;
            this.offsetY *= sy;
            this.advance *= sx;
        }
    }
}