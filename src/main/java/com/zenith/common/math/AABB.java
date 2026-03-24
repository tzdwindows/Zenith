package com.zenith.common.math;

import org.joml.Vector3f;

/**
 * AABB 表示一个 3D 轴对齐包围盒。
 * 使用最小点 (min) 和最大点 (max) 来定义空间范围。
 */
public class AABB {

    private final Vector3f min;
    private final Vector3f max;

    /**
     * 创建一个初始化的 AABB（最小点无穷大，最大点无穷小）。
     * 这样任何点合并进来都会更新范围。
     */
    public AABB() {
        this.min = new Vector3f(Float.POSITIVE_INFINITY);
        this.max = new Vector3f(Float.NEGATIVE_INFINITY);
    }

    public AABB(Vector3f min, Vector3f max) {
        this.min = new Vector3f(min);
        this.max = new Vector3f(max);
    }

    /* -------------------------------------------------------------------------- */
    /* 动态构建方法                                                                */
    /* -------------------------------------------------------------------------- */

    /**
     * 扩展包围盒以包含指定的点。
     */
    public void extend(Vector3f point) {
        min.min(point);
        max.max(point);
    }

    /**
     * 合并另一个 AABB。
     */
    public void union(AABB other) {
        min.min(other.min);
        max.max(other.max);
    }

    /* -------------------------------------------------------------------------- */
    /* 碰撞与包含检测                                                               */
    /* -------------------------------------------------------------------------- */

    /**
     * 检测一个点是否在包围盒内。
     */
    public boolean contains(Vector3f point) {
        return (point.x >= min.x && point.x <= max.x) &&
                (point.y >= min.y && point.y <= max.y) &&
                (point.z >= min.z && point.z <= max.z);
    }

    /**
     * 检测两个 AABB 是否相交。
     */
    public boolean intersects(AABB other) {
        return (min.x <= other.max.x && max.x >= other.min.x) &&
                (min.y <= other.max.y && max.y >= other.min.y) &&
                (min.z <= other.max.z && max.z >= other.min.z);
    }

    /* -------------------------------------------------------------------------- */
    /* 属性获取                                                                   */
    /* -------------------------------------------------------------------------- */

    public Vector3f getMin() { return min; }
    public Vector3f getMax() { return max; }

    public Vector3f getCenter() {
        return new Vector3f(min).add(max).mul(0.5f);
    }

    public Vector3f getSize() {
        return new Vector3f(max).sub(min);
    }

    /**
     * 重置包围盒。
     */
    public void reset() {
        min.set(Float.POSITIVE_INFINITY);
        max.set(Float.NEGATIVE_INFINITY);
    }

    @Override
    public String toString() {
        return "AABB[min=" + min + ", max=" + max + "]";
    }
}