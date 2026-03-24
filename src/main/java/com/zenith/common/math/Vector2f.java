package com.zenith.common.math;

import com.zenith.common.utils.InternalLogger;
import org.joml.Vector2fc; // JOML 的只读接口

/**
 * Vector2f 表示二维浮点向量。
 * 已增强对 JOML 的原生支持，可实现无缝互转。
 */
public class Vector2f {

    public float x, y;

    /* -------------------------------------------------------------------------- */
    /* 1. 增强型构造函数 (支持 JOML)                                               */
    /* -------------------------------------------------------------------------- */

    public Vector2f() {
        this(0, 0);
    }

    public Vector2f(float x, float y) {
        this.x = x;
        this.y = y;
    }

    /** 从另一个 Zenith Vector2f 构造 */
    public Vector2f(Vector2f other) {
        this.x = other.x;
        this.y = other.y;
    }

    /** 从 JOML 向量构造 (支持 Vector2f, Vector2d, Vector2i 等通过其只读接口) */
    public Vector2f(org.joml.Vector2fc jomlVector) {
        this.x = jomlVector.x();
        this.y = jomlVector.y();
    }

    /* -------------------------------------------------------------------------- */
    /* 2. JOML 互操作方法                                                         */
    /* -------------------------------------------------------------------------- */

    /** * 将当前向量转换为 JOML 的 Vector2f 对象。
     * 常用于调用 JOML 提供的复杂数学库。
     */
    public org.joml.Vector2f toJOML() {
        return new org.joml.Vector2f(this.x, this.y);
    }

    /** * 将当前数据写入已有的 JOML 向量对象中。
     * 避免了频繁创建新对象的开销（GC 友好）。
     */
    public org.joml.Vector2f settoJOML(org.joml.Vector2f dest) {
        return dest.set(this.x, this.y);
    }

    /** * 从 JOML 向量更新当前数据。
     */
    public Vector2f setFromJOML(org.joml.Vector2fc jomlVector) {
        this.x = jomlVector.x();
        this.y = jomlVector.y();
        return this;
    }

    /* -------------------------------------------------------------------------- */
    /* 3. 核心运算 (保持链式调用)                                                  */
    /* -------------------------------------------------------------------------- */

    public Vector2f set(float x, float y) {
        this.x = x;
        this.y = y;
        return this;
    }

    public Vector2f add(Vector2f other) {
        this.x += other.x;
        this.y += other.y;
        return this;
    }

    public Vector2f sub(Vector2f other) {
        this.x -= other.x;
        this.y -= other.y;
        return this;
    }

    public Vector2f mul(float scalar) {
        this.x *= scalar;
        this.y *= scalar;
        return this;
    }

    public Vector2f div(float scalar) {
        if (scalar == 0) {
            InternalLogger.error("Vector2f: Division by zero during operation!");
            return this;
        }
        this.x /= scalar;
        this.y /= scalar;
        return this;
    }

    public float length() {
        return (float) Math.sqrt(x * x + y * y);
    }

    public Vector2f normalize() {
        float len = length();
        if (len > 1e-6f) return div(len);
        return this;
    }

    /* -------------------------------------------------------------------------- */
    /* 4. 系统支持                                                                */
    /* -------------------------------------------------------------------------- */

    @Override
    public String toString() {
        return String.format("[%s] Vector2f(%.3f, %.3f)", "Zenith", x, y);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Vector2f)) return false;
        Vector2f v = (Vector2f) obj;
        return Float.compare(v.x, x) == 0 && Float.compare(v.y, y) == 0;
    }
}