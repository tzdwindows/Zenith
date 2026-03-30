package com.zenith.render;

import com.zenith.common.utils.InternalLogger;
import org.joml.Vector2f;

/**
 * Viewport 定义了渲染在屏幕上的区域。
 * 负责处理窗口缩放、视口比例计算以及像素坐标转换。
 */
public class Viewport {

    private int x, y;
    private int width, height;
    private float aspectRatio;

    /**
     * 构造一个新的视口。
     * @param width  视口宽度（像素）
     * @param height 视口高度（像素）
     */
    public Viewport(int width, int height) {
        this(0, 0, width, height);
    }

    public Viewport(int x, int y, int width, int height) {
        set(x, y, width, height);
    }

    /**
     * 更新视口尺寸，并自动计算宽高比。
     */
    public void set(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = Math.max(1, width); // 防止除零异常
        this.height = Math.max(1, height);
        this.aspectRatio = (float) this.width / (float) this.height;

        InternalLogger.debug(String.format("Viewport updated: [%d, %d, %d, %d] AspectRatio: %.2f",
                x, y, width, height, aspectRatio));
    }

    /* -------------------------------------------------------------------------- */
    /* 坐标转换工具                                                                */
    /* -------------------------------------------------------------------------- */

    /**
     * 将屏幕像素坐标转换为归一化设备坐标 (NDC: -1 到 1)。
     * 用于拾取渲染物体或处理鼠标输入。
     */
    public Vector2f screenToNDC(float screenX, float screenY) {
        float nx = (2.0f * (screenX - x) / width) - 1.0f;
        float ny = 1.0f - (2.0f * (screenY - y) / height); // 翻转 Y 轴，屏幕坐标 Y 向下，NDC Y 向上
        return new Vector2f(nx, ny);
    }

    /* -------------------------------------------------------------------------- */
    /* Getter                                                                     */
    /* -------------------------------------------------------------------------- */

    public int getX() { return x; }
    public int getY() { return y; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public float getAspectRatio() { return aspectRatio; }

    /**
     * 检查一个像素点是否在视口范围内
     */
    public boolean contains(float px, float py) {
        return px >= x && px <= x + width && py >= y && py <= y + height;
    }

    @Override
    public String toString() {
        return String.format("Viewport(%d, %d, %d, %d)", x, y, width, height);
    }
}