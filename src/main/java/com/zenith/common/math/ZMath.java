package com.zenith.common.math;

/**
 * ZMath 是 Zenith 引擎的数学工具类。
 * 提供了针对游戏开发优化的浮点数运算、插值算法及几何辅助函数。
 * * @author tzdwindows 7
 */
public final class ZMath {

    /** 常用数学常量 */
    public static final float PI = (float) Math.PI;
    public static final float TWO_PI = PI * 2.0f;
    public static final float HALF_PI = PI / 2.0f;
    public static final float EPSILON = 1e-6f; // 用于浮点数比较的极小值

    /** 私有构造函数，防止实例化 */
    private ZMath() {}

    /* -------------------------------------------------------------------------- */
    /* 基础运算                                   */
    /* -------------------------------------------------------------------------- */

    /**
     * 将数值限制在指定的最小和最大范围内。
     * * @param value 待检查的值
     * @param min 最小值
     * @param max 最大值
     * @return 限制后的值
     */
    public static float clamp(float value, float min, float max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    /**
     * 检查两个浮点数在误差允许范围内是否相等。
     */
    public static boolean equals(float a, float b) {
        return Math.abs(a - b) < EPSILON;
    }

    /* -------------------------------------------------------------------------- */
    /* 插值算法                                   */
    /* -------------------------------------------------------------------------- */

    /**
     * 线性插值 (Linear Interpolation)。
     * 在 a 和 b 之间根据 t 进行平滑过渡。
     * * @param a 起始值
     * @param b 结束值
     * @param t 插值因子 [0, 1]
     * @return 插值结果
     */
    public static float lerp(float a, float b, float t) {
        return a + t * (b - a);
    }

    /**
     * 平滑阶梯插值 (Smoothstep)。
     * 比线性插值更加平滑，常用于动画启动和停止。
     * * @param t 输入值 [0, 1]
     * @return 平滑后的值
     */
    public static float smoothstep(float t) {
        t = clamp(t, 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    /* -------------------------------------------------------------------------- */
    /* 转换与映射                                 */
    /* -------------------------------------------------------------------------- */

    /**
     * 将一个范围内的值映射到另一个范围。
     * 例如：将 [0, 1] 的值映射到 [0, 255] 用于颜色转换。
     */
    public static float map(float value, float inMin, float inMax, float outMin, float outMax) {
        return outMin + (value - inMin) * (outMax - outMin) / (inMax - inMin);
    }

    /**
     * 角度转弧度。
     */
    public static float toRadians(float degrees) {
        return degrees * (PI / 180.0f);
    }

    /**
     * 弧度转角度。
     */
    public static float toDegrees(float radians) {
        return radians * (180.0f / PI);
    }

    /* -------------------------------------------------------------------------- */
    /* 几何辅助                                   */
    /* -------------------------------------------------------------------------- */

    /**
     * 计算两点之间的距离（2D）。
     */
    public static float distance(float x1, float y1, float x2, float y2) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * 计算点是否在矩形范围内。
     */
    public static boolean isPointInRect(float px, float py, float rx, float ry, float rw, float rh) {
        return px >= rx && px <= rx + rw && py >= ry && py <= ry + rh;
    }
}