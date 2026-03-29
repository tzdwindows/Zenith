package com.zenith.common.math;

import org.joml.Vector4f;

/**
 * Color 类用于管理 RGBA 颜色。
 * 提供常用颜色常量、十六进制转换以及 sRGB 与 Linear 空间的转换。
 */
public class Color {

    // 预定义常用颜色常量 (RGBA 范围 0.0 - 1.0)
    public static final Color WHITE       = new Color(1.0f, 1.0f, 1.0f, 1.0f);
    public static final Color BLACK       = new Color(0.0f, 0.0f, 0.0f, 1.0f);
    public static final Color RED         = new Color(1.0f, 0.0f, 0.0f, 1.0f);
    public static final Color GREEN       = new Color(0.0f, 1.0f, 0.0f, 1.0f);
    public static final Color BLUE        = new Color(0.0f, 0.0f, 1.0f, 1.0f);
    public static final Color YELLOW      = new Color(1.0f, 1.0f, 0.0f, 1.0f);
    public static final Color CYAN        = new Color(0.0f, 1.0f, 1.0f, 1.0f);
    public static final Color MAGENTA     = new Color(1.0f, 0.0f, 1.0f, 1.0f);
    public static final Color TRANSPARENT = new Color(0.0f, 0.0f, 0.0f, 0.0f);

    public static final Color GRAY       = new Color(0.5f, 0.5f, 0.5f, 1.0f);
    public static final Color LIGHT_GRAY = new Color(0.75f, 0.75f, 0.75f, 1.0f);
    public static final Color DARK_GRAY  = new Color(0.25f, 0.25f, 0.25f, 1.0f);

    public float r, g, b, a;

    /** 默认构造函数：白色 */
    public Color() {
        this(1.0f, 1.0f, 1.0f, 1.0f);
    }

    public Color(Color other) {
        this.r = other.r;
        this.g = other.g;
        this.b = other.b;
        this.a = other.a;
    }

    /** 基础构造函数 */
    public Color(float r, float g, float b, float a) {
        this.r = ZMath.clamp(r, 0.0f, 1.0f);
        this.g = ZMath.clamp(g, 0.0f, 1.0f);
        this.b = ZMath.clamp(b, 0.0f, 1.0f);
        this.a = ZMath.clamp(a, 0.0f, 1.0f);
    }

    /** 从 0-255 整数值构造 */
    public Color(int r, int g, int b, int a) {
        this(r / 255.0f, g / 255.0f, b / 255.0f, a / 255.0f);
    }

    public Color(float v, float v1, float v2) {
        this(v, v1, v2, 1.0f);
    }

    /* -------------------------------------------------------------------------- */
    /* 静态工厂方法                                                                */
    /* -------------------------------------------------------------------------- */

    /**
     * 从十六进制字符串解析颜色 (支持 #RRGGBB 或 #RRGGBBAA)
     */
    public static Color fromHex(String hex) {
        if (hex.startsWith("#")) hex = hex.substring(1);

        long val = Long.parseLong(hex, 16);
        if (hex.length() == 6) {
            return new Color(
                    (int)(val >> 16 & 0xFF),
                    (int)(val >> 8 & 0xFF),
                    (int)(val & 0xFF),
                    255
            );
        } else if (hex.length() == 8) {
            return new Color(
                    (int)(val >> 24 & 0xFF),
                    (int)(val >> 16 & 0xFF),
                    (int)(val >> 8 & 0xFF),
                    (int)(val & 0xFF)
            );
        }
        throw new IllegalArgumentException("Invalid hex color format: " + hex);
    }

    /**
     * 将 RGB 通道乘以一个系数（不改变 Alpha）。
     * 用于实现悬停变亮或按下变暗的效果。
     */
    public void multiply(float factor) {
        this.r = ZMath.clamp(this.r * factor, 0.0f, 1.0f);
        this.g = ZMath.clamp(this.g * factor, 0.0f, 1.0f);
        this.b = ZMath.clamp(this.b * factor, 0.0f, 1.0f);
    }

    /* -------------------------------------------------------------------------- */
    /* 空间转换 (伽马校正相关)                                                       */
    /* -------------------------------------------------------------------------- */

    /**
     * 将当前颜色从 sRGB 空间转换到 Linear 空间。
     * 渲染器在进行光照计算前通常需要此转换。
     */
    public Color toLinear() {
        return new Color(
                (float) Math.pow(r, 2.2),
                (float) Math.pow(g, 2.2),
                (float) Math.pow(b, 2.2),
                a
        );
    }

    /**
     * 将当前颜色从 Linear 空间转换回 sRGB 空间。
     */
    public Color toGamma() {
        return new Color(
                (float) Math.pow(r, 1.0 / 2.2),
                (float) Math.pow(g, 1.0 / 2.2),
                (float) Math.pow(b, 1.0 / 2.2),
                a
        );
    }

    /* -------------------------------------------------------------------------- */
    /* 工具转换                                                                   */
    /* -------------------------------------------------------------------------- */

    /** 转换为 JOML 的 Vector4f，方便直接传给 Shader */
    public Vector4f toVector() {
        return new Vector4f(r, g, b, a);
    }

    @Override
    public String toString() {
        return String.format("Color(%.2f, %.2f, %.2f, %.2f)", r, g, b, a);
    }
}