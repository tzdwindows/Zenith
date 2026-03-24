package com.zenith.common.math;

import org.joml.Vector2f;

/**
 * Rectf 表示一个 2D 矩形区域。
 * 使用左上角坐标 (x, y) 以及宽高 (width, height) 定义。
 */
public class Rectf {

    public float x, y, width, height;

    /** 默认构造函数：位于原点的 0x0 矩形 */
    public Rectf() {
        this(0, 0, 0, 0);
    }

    /** * 基础构造函数
     */
    public Rectf(float x, float y, float width, float height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    /* -------------------------------------------------------------------------- */
    /* 几何属性获取                                                               */
    /* -------------------------------------------------------------------------- */

    public float getLeft() { return x; }
    public float getRight() { return x + width; }
    public float getTop() { return y; }
    public float getBottom() { return y + height; }

    public Vector2f getCenter() {
        return new Vector2f(x + width / 2f, y + height / 2f);
    }

    /* -------------------------------------------------------------------------- */
    /* 碰撞与包含检测                                                             */
    /* -------------------------------------------------------------------------- */

    /**
     * 检测一个点是否在矩形内。
     */
    public boolean contains(float px, float py) {
        return px >= x && px <= x + width && py >= y && py <= y + height;
    }

    /**
     * 检测另一个矩形是否与本矩形相交 (AABB 碰撞检测)。
     */
    public boolean intersects(Rectf other) {
        return x < other.x + other.width &&
                x + width > other.x &&
                y < other.y + other.height &&
                y + height > other.y;
    }

    /* -------------------------------------------------------------------------- */
    /* 变换操作                                                                   */
    /* -------------------------------------------------------------------------- */

    /**
     * 缩放矩形（以左上角为基准）。
     */
    public void scale(float factor) {
        this.width *= factor;
        this.height *= factor;
    }

    /**
     * 移动矩形位置。
     */
    public void offset(float dx, float dy) {
        this.x += dx;
        this.y += dy;
    }

    /**
     * 融合：将两个矩形合并为一个能容纳两者的最小矩形。
     */
    public Rectf union(Rectf other) {
        float newX = Math.min(x, other.x);
        float newY = Math.min(y, other.y);
        float newMaxX = Math.max(getRight(), other.getRight());
        float newMaxY = Math.max(getBottom(), other.getBottom());
        return new Rectf(newX, newY, newMaxX - newX, newMaxY - newY);
    }

    @Override
    public String toString() {
        return String.format("Rectf(x:%.2f, y:%.2f, w:%.2f, h:%.2f)", x, y, width, height);
    }
}