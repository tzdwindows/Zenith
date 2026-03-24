package com.zenith.common.math;

import com.zenith.common.utils.InternalLogger;
import org.joml.Quaternionf;

/**
 * Matrix4f 处理 4x4 变换矩阵。
 */
public class Matrix4f {

    private final org.joml.Matrix4f internal;

    public Matrix4f() {
        this.internal = new org.joml.Matrix4f();
    }

    public Matrix4f(org.joml.Matrix4f jomlMatrix) {
        this.internal = new org.joml.Matrix4f(jomlMatrix);
    }

    public Matrix4f identity() {
        internal.identity();
        return this;
    }

    /**
     * 修正后的变换设置方法
     * @param translation 位移向量
     * @param rotation 四元数旋转对象 (org.joml.Quaternionf)
     * @param scale 缩放向量
     */
    public Matrix4f setTransformation(Vector3f translation, Quaternionf rotation, Vector3f scale) {
        internal.translationRotateScale(
                translation.x, translation.y, translation.z,
                rotation.x, rotation.y, rotation.z, rotation.w,
                scale.x, scale.y, scale.z
        );
        return this;
    }

    public Matrix4f mul(Matrix4f right) {
        internal.mul(right.internal);
        return this;
    }

    public Matrix4f invert() {
        if (Math.abs(internal.determinant()) < 1e-6f) {
            InternalLogger.error("Matrix4f: 尝试翻转一个奇异矩阵（行列式接近0）！");
            return this;
        }
        internal.invert();
        return this;
    }

    public org.joml.Matrix4f getInternal() {
        return internal;
    }
}