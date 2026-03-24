package com.zenith.common.math;

import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * QuaternionUtils 提供四元数的高级物理运算。
 * 包含从欧拉角、轴角转换以及角速度对旋转的积分。
 */
public final class QuaternionUtils {

    private QuaternionUtils() {}

    /**
     * 将角速度向量应用到当前的四元数旋转上（物理积分）。
     * 公式: Q_new = Q_old + (0.5 * AngularVelocity * Q_old) * dt
     * * @param currentRotation 当前的姿态
     * @param angularVelocity 角速度向量 (弧度/秒)，轴的方向为旋转轴，模长为旋转速率
     * @param deltaTime       帧间隔时间
     * @param dest            存储结果的四元数
     */
    public static void integrate(Quaternionf currentRotation, Vector3f angularVelocity, float deltaTime, Quaternionf dest) {
        // 创建一个临时的四元数表示角速度的变化
        // 角速度四元数 W = [0, wx, wy, wz]
        float qx = currentRotation.x;
        float qy = currentRotation.y;
        float qz = currentRotation.z;
        float qw = currentRotation.w;

        float wx = angularVelocity.x;
        float wy = angularVelocity.y;
        float wz = angularVelocity.z;

        // 计算旋转的变化率 (dQ/dt = 0.5 * W * Q)
        float halfDt = deltaTime * 0.5f;
        float dx = halfDt * ( wx * qw + wy * qz - wz * qy);
        float dy = halfDt * ( wy * qw + wz * qx - wx * qz);
        float dz = halfDt * ( wz * qw + wx * qy - wy * qx);
        float dw = halfDt * (-wx * qx - wy * qy - wz * qz);

        // 更新并归一化（防止浮点数漂移导致的缩放变形）
        dest.set(qx + dx, qy + dy, qz + dz, qw + dw);
        dest.normalize();
    }

    /**
     * 计算两个旋转之间的“差值旋转”。
     * 公式：Relative = End * Inverse(Start)
     */
    public static Quaternionf getDifference(Quaternionf start, Quaternionf end) {
        Quaternionf invStart = new Quaternionf(start).invert();
        return new Quaternionf(end).mul(invStart);
    }

    /**
     * 将世界空间的向量转换到物体的局部空间坐标系中。
     */
    public static Vector3f worldToLocal(Vector3f worldVector, Quaternionf rotation, Vector3f dest) {
        return new Quaternionf(rotation).invert().transform(worldVector, dest);
    }

    /**
     * 将局部空间的向量转换到世界空间。
     */
    public static Vector3f localToWorld(Vector3f localVector, Quaternionf rotation, Vector3f dest) {
        return rotation.transform(localVector, dest);
    }
}