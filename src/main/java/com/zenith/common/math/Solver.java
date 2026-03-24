package com.zenith.common.math;

import org.joml.Vector3f;

/**
 * Solver 提供物理约束求解算法。
 * 用于解决两个物体碰撞后的速度分配（冲量求解）和位置分离。
 */
public final class Solver {

    private Solver() {}

    /**
     * 解决两个物体之间的位置重叠（防止穿模）。
     * 按照物体的反质量（Inverse Mass）比例将它们推开。
     * * @param posA    物体 A 的位置
     * @param posB    物体 B 的位置
     * @param invMassA 物体 A 的质量倒数 (1/mass)，静态物体为 0
     * @param invMassB 物体 B 的质量倒数
     * @param data    来自 Intersection 的碰撞数据
     */
    public static void solvePosition(Vector3f posA, Vector3f posB,
                                     float invMassA, float invMassB,
                                     CollisionData data) {
        float totalInvMass = invMassA + invMassB;
        if (totalInvMass <= 0) return;

        // 允许一小部分的穿透（Slop），防止抖动
        float percent = 0.2f; // 修正率 (20% - 80%)
        float slop = 0.01f;   // 穿透容差

        float correctionMagnitude = (Math.max(data.depth - slop, 0.0f) / totalInvMass) * percent;
        Vector3f correction = new Vector3f(data.normal).mul(correctionMagnitude);

        // A 往反方向推，B 往正方向推
        posA.sub(new Vector3f(correction).mul(invMassA));
        posB.add(new Vector3f(correction).mul(invMassB));
    }

    /**
     * 解决两个物体之间的速度碰撞（基于冲量的法向响应）。
     * * @param velA       物体 A 的速度
     * @param velB       物体 B 的速度
     * @param invMassA   物体 A 的质量倒数
     * @param invMassB   物体 B 的质量倒数
     * @param restitution 恢复系数（弹性：0 为不反弹，1 为完全弹性）
     * @param data        碰撞数据
     */
    public static void solveVelocity(Vector3f velA, Vector3f velB,
                                     float invMassA, float invMassB,
                                     float restitution, CollisionData data) {

        // 1. 计算相对速度
        Vector3f relativeVel = new Vector3f(velB).sub(velA);

        // 2. 计算法线方向上的相对速度分量
        float velAlongNormal = relativeVel.dot(data.normal);

        // 3. 如果物体已经在分离（速度方向朝外），则无需处理
        if (velAlongNormal > 0) return;

        // 4. 计算冲量标量 j
        float j = -(1 + restitution) * velAlongNormal;
        j /= (invMassA + invMassB);

        // 5. 应用冲量到两个物体的速度上
        Vector3f impulse = new Vector3f(data.normal).mul(j);

        velA.sub(new Vector3f(impulse).mul(invMassA));
        velB.add(new Vector3f(impulse).mul(invMassB));
    }
}