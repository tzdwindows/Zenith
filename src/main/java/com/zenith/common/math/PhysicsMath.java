package com.zenith.common.math;

import org.joml.Vector3f;

/**
 * PhysicsMath 提供物理模拟核心算法。
 * 包含运动积分、碰撞响应、向量投影及反射等。
 */
public final class PhysicsMath {

    private PhysicsMath() {}

    /* -------------------------------------------------------------------------- */
    /* 1. 向量物理运算                                                             */
    /* -------------------------------------------------------------------------- */

    /**
     * 计算反射向量 (Reflection)。
     * 公式: R = I - 2 * (I · N) * N
     * @param incoming 入射向量 (从起点指向碰撞点)
     * @param normal   碰撞表面的法线 (必须已归一化)
     * @param dest     存储结果的向量
     * @return 反射后的向量
     */
    public static Vector3f reflect(Vector3f incoming, Vector3f normal, Vector3f dest) {
        float factor = 2.0f * incoming.dot(normal);
        dest.set(normal).mul(factor);
        return incoming.sub(dest, dest);
    }

    /**
     * 计算投影向量 (Projection)。
     * 将向量 v 投影到向量 onto 上。常用于计算斜坡上的重力分量。
     */
    public static Vector3f project(Vector3f v, Vector3f onto, Vector3f dest) {
        float dot = v.dot(onto);
        float magSq = onto.lengthSquared();
        return dest.set(onto).mul(dot / magSq);
    }

    /* -------------------------------------------------------------------------- */
    /* 2. 运动积分 (Integration)                                                   */
    /* -------------------------------------------------------------------------- */

    /**
     * 欧拉积分 (Euler Integration)。
     * 最简单但精度较低，适用于大多数非精确物理模拟。
     * 公式: v = v + a * dt; p = p + v * dt;
     */
    public static void integrateEuler(Vector3f pos, Vector3f vel, Vector3f acc, float dt) {
        vel.add(acc.x * dt, acc.y * dt, acc.z * dt);
        pos.add(vel.x * dt, vel.y * dt, vel.z * dt);
    }

    /**
     * 韦尔莱积分 (Verlet Integration)。
     * 比欧拉积分稳定得多，常用于粒子系统、布料模拟和绳索。
     * 它不存储速度，而是根据当前位置和上一次位置推算。
     */
    public static void integrateVerlet(Vector3f currentPos, Vector3f lastPos, Vector3f acc, float dt) {
        Vector3f temp = new Vector3f(currentPos);
        // 公式: x(t+dt) = 2x(t) - x(t-dt) + a * dt^2
        currentPos.add(currentPos).sub(lastPos).add(acc.x * dt * dt, acc.y * dt * dt, acc.z * dt * dt);
        lastPos.set(temp);
    }

    /* -------------------------------------------------------------------------- */
    /* 3. 碰撞反馈 (Collision Response)                                            */
    /* -------------------------------------------------------------------------- */

    /**
     * 计算两个圆球碰撞后的分离速度 (考虑恢复系数)。
     * @param v1Rel    物体1相对于物体2的速度
     * @param normal   碰撞法线
     * @param bounciness 弹性系数 (0: 没反应, 1: 完全弹性碰撞)
     * @return 冲量标量
     */
    public static float calculateImpulse(Vector3f v1Rel, Vector3f normal, float bounciness) {
        float velAlongNormal = v1Rel.dot(normal);
        // 如果物体正在分离，不需要冲量
        if (velAlongNormal > 0) return 0;

        return -(1 + bounciness) * velAlongNormal;
    }

    /* -------------------------------------------------------------------------- */
    /* 4. 常量与环境                                                               */
    /* -------------------------------------------------------------------------- */

    /** 地球标准重力加速度 (m/s^2) */
    public static final Vector3f GRAVITY_EARTH = new Vector3f(0, -9.81f, 0);
}