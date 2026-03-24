package com.zenith.common.math;

import org.joml.Vector3f;

/**
 * Geometry 提供 3D 几何体之间的精细碰撞检测算法。
 * 包含球体 (Sphere)、胶囊体 (Capsule) 与 AABB 的相交判定。
 */
public final class Geometry {

    private Geometry() {}

    /* -------------------------------------------------------------------------- */
    /* 1. 球体 (Sphere) 逻辑                                                       */
    /* -------------------------------------------------------------------------- */

    /**
     * 检测两个球体是否相交。
     * 公式: 两圆心距离 < 半径之和
     */
    public static boolean intersectSpheres(Vector3f c1, float r1, Vector3f c2, float r2) {
        float distanceSq = c1.distanceSquared(c2);
        float radiusSum = r1 + r2;
        return distanceSq <= (radiusSum * radiusSum);
    }

    /**
     * 检测球体与 AABB 是否相交。
     * 算法：在 AABB 上找到距离球心最近的点，计算该点到球心的距离。
     */
    public static boolean intersectSphereAABB(Vector3f sphereCenter, float radius, AABB aabb) {
        // 找到 AABB 内最接近球心的点
        float closestX = ZMath.clamp(sphereCenter.x, aabb.getMin().x, aabb.getMax().x);
        float closestY = ZMath.clamp(sphereCenter.y, aabb.getMin().y, aabb.getMax().y);
        float closestZ = ZMath.clamp(sphereCenter.z, aabb.getMin().z, aabb.getMax().z);

        float distanceSq = (sphereCenter.x - closestX) * (sphereCenter.x - closestX) +
                (sphereCenter.y - closestY) * (sphereCenter.y - closestY) +
                (sphereCenter.z - closestZ) * (sphereCenter.z - closestZ);

        return distanceSq <= (radius * radius);
    }

    /* -------------------------------------------------------------------------- */
    /* 2. 点与几何体关系                                                            */
    /* -------------------------------------------------------------------------- */

    /**
     * 计算点到平面的投影点。
     */
    public static Vector3f projectPointOnPlane(Vector3f point, Plane plane, Vector3f dest) {
        float dist = plane.getSignedDistance(point);
        return dest.set(point).sub(new Vector3f(plane.getNormal()).mul(dist));
    }

    /* -------------------------------------------------------------------------- */
    /* 3. 胶囊体 (Capsule) 逻辑 - 角色控制器的标配                                   */
    /* -------------------------------------------------------------------------- */

    /**
     * 检测点到线段的最短距离平方。
     * 胶囊体本质上是线段的“膨胀”，这是胶囊体碰撞的基础。
     */
    public static float distPointSegmentSq(Vector3f p, Vector3f a, Vector3f b) {
        Vector3f ab = new Vector3f(b).sub(a);
        Vector3f ap = new Vector3f(p).sub(a);
        float t = ap.dot(ab) / ab.lengthSquared();
        t = ZMath.clamp(t, 0.0f, 1.0f);
        Vector3f closestPoint = new Vector3f(a).add(ab.mul(t));
        return p.distanceSquared(closestPoint);
    }

    /* -------------------------------------------------------------------------- */
    /* 4. 射线 (Ray) 扩展逻辑                                                       */
    /* -------------------------------------------------------------------------- */

    /**
     * 射线与平面的交点检测。
     * @return 如果平行则返回 -1，否则返回相交距离 t
     */
    public static float intersectRayPlane(Ray ray, Plane plane) {
        float denom = plane.getNormal().dot(ray.getDirection());
        if (Math.abs(denom) > 1e-6f) {
            float t = -(plane.getNormal().dot(ray.getOrigin()) + plane.getDistance()) / denom;
            if (t >= 0) return t;
        }
        return -1.0f;
    }
}