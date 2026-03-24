package com.zenith.render;

import com.zenith.common.utils.InternalLogger;

public abstract class Font {
    protected String fontName;
    protected int fontSize;

    protected Font(String fontName, int fontSize) {
        this.fontName = fontName;
        this.fontSize = fontSize;
        InternalLogger.info("Loading font: " + fontName + " at size " + fontSize);
    }

    /** 获取包含所有字符的纹理 (对应报错：getTexture) */
    public abstract Texture getTexture();

    public abstract Glyph getGlyph(char c);
    public abstract float getWidth(String text);
    public abstract void dispose();

    public int getSize() {
        return fontSize;
    }

    public static class Glyph {
        public float u, v, u2, v2;
        public int width, height;
        public int offsetX, offsetY;
        public int advance;
    }
}