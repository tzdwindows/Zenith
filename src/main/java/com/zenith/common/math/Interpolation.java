package com.zenith.common.math;

import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Interpolation 提供高级插值算法。
 * 包含常用的缓动函数、矢量插值和四元数球面插值。
 */
public final class Interpolation {

    private Interpolation() {}

    /* -------------------------------------------------------------------------- */
    /* 1. 基础线性插值 (矢量支持)                                                 */
    /* -------------------------------------------------------------------------- */

    /** 3D 向量线性插值 */
    public static Vector3f lerp(Vector3f start, Vector3f end, float t, Vector3f dest) {
        return start.lerp(end, t, dest);
    }

    /* -------------------------------------------------------------------------- */
    /* 2. 旋转专用插值 (Slerp)                                                    */
    /* -------------------------------------------------------------------------- */

    /**
     * 球面线性插值 (Spherical Linear Interpolation)。
     * 用于平滑地旋转物体，确保旋转速度恒定且路径最短。
     */
    public static Quaternionf slerp(Quaternionf start, Quaternionf end, float t, Quaternionf dest) {
        return start.slerp(end, t, dest);
    }

    /* -------------------------------------------------------------------------- */
    /* 3. 常用缓动函数 (Easing Functions)                                         */
    /* -------------------------------------------------------------------------- */

    /** 平滑开始（二次方） */
    public static float easeIn(float t) {
        return t * t;
    }

    /** 平滑结束（二次方） */
    public static float easeOut(float t) {
        return 1.0f - (1.0f - t) * (1.0f - t);
    }

    /** 平滑开始和结束 */
    public static float easeInOut(float t) {
        return t < 0.5f ? 2.0f * t * t : 1.0f - (float) Math.pow(-2.0f * t + 2.0f, 2.0f) / 2.0f;
    }

    /** 弹簧效果（常用于 UI 弹出动画） */
    public static float elasticOut(float t) {
        float c4 = (2.0f * (float)Math.PI) / 3.0f;
        return t == 0 ? 0 : t == 1 ? 1 :
                (float) Math.pow(2.0f, -10.0f * t) * (float) Math.sin((t * 10.0f - 0.75f) * c4) + 1.0f;
    }

    /* -------------------------------------------------------------------------- */
    /* 4. 物理模拟插值                                                            */
    /* -------------------------------------------------------------------------- */

    /**
     * 阻尼平滑跟随 (Damped Spring)。
     * 比普通的 lerp 更具真实感，常用于摄像机跟随主角。
     * @param current 当前值
     * @param target 目标值
     * @param smoothing 平滑系数 (0-1)
     * @param deltaTime 每帧间隔
     */
    public static float smoothDamp(float current, float target, float smoothing, float deltaTime) {
        return ZMath.lerp(current, target, 1.0f - (float) Math.pow(smoothing, deltaTime));
    }
}