package com.zenith.common.math;

import org.joml.Vector3f;

/**
 * Intersection 提供不仅判断相交，还能计算碰撞流形（法线、深度）的算法。
 */
public final class Intersection {

    private Intersection() {}

    /**
     * 检测两个球体的碰撞细节。
     */
    public static boolean testSphereSphere(Vector3f c1, float r1, Vector3f c2, float r2, CollisionData data) {
        Vector3f delta = new Vector3f(c2).sub(c1);
        float distSq = delta.lengthSquared();
        float radiusSum = r1 + r2;

        if (distSq > radiusSum * radiusSum) return false;

        float dist = (float) Math.sqrt(distSq);
        data.intersected = true;
        // 法线从 1 指向 2
        data.normal.set(delta).div(dist);
        data.depth = radiusSum - dist;
        // 碰撞点位于两个球体接触的中间位置
        data.contactPoint.set(c1).add(new Vector3f(data.normal).mul(r1));

        return true;
    }

    /**
     * 检测球体与 AABB 的碰撞细节。
     * 这是 3D 角色在场景中行走（不穿墙）的核心逻辑。
     */
    public static boolean testSphereAABB(Vector3f sphereCenter, float radius, AABB aabb, CollisionData data) {
        // 1. 找到 AABB 上距离球心最近的点
        float closestX = ZMath.clamp(sphereCenter.x, aabb.getMin().x, aabb.getMax().x);
        float closestY = ZMath.clamp(sphereCenter.y, aabb.getMin().y, aabb.getMax().y);
        float closestZ = ZMath.clamp(sphereCenter.z, aabb.getMin().z, aabb.getMax().z);

        Vector3f closestPoint = new Vector3f(closestX, closestY, closestZ);
        Vector3f delta = new Vector3f(sphereCenter).sub(closestPoint);
        float distSq = delta.lengthSquared();

        if (distSq > radius * radius) return false;

        float dist = (float) Math.sqrt(distSq);

        data.intersected = true;
        data.contactPoint.set(closestPoint);

        // 特殊处理：如果球心正好在 AABB 内部（dist 为 0）
        if (dist < 1e-6f) {
            // 此时需要寻找球心距离 AABB 哪个面最近，将球推出去
            // 为简单起见，这里假设法线向上
            data.normal.set(0, 1, 0);
            data.depth = radius;
        } else {
            data.normal.set(delta).div(dist); // 法线从 AABB 指向球心
            data.depth = radius - dist;
        }

        return true;
    }
}