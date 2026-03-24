package com.zenith.render;

/**
 * FrameBuffer 允许将渲染结果存储在纹理中，而不是直接显示在屏幕。
 */
public abstract class FrameBuffer {
    protected int width, height;

    public abstract void bind();
    public abstract void unbind();
    public abstract Texture getColorAttachment();
    public abstract void dispose();
}