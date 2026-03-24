package com.zenith.common.math;

import org.joml.Vector3f;

/**
 * Ray 表示 3D 空间中的一条射线。
 * 公式: P = origin + t * direction (t >= 0)
 */
public class Ray {

    private final Vector3f origin;
    private final Vector3f direction;

    /**
     * 构造一条射线。
     * @param origin 起点
     * @param direction 方向（内部会自动归一化）
     */
    public Ray(Vector3f origin, Vector3f direction) {
        this.origin = new Vector3f(origin);
        this.direction = new Vector3f(direction).normalize();
    }

    /* -------------------------------------------------------------------------- */
    /* 核心逻辑                                                                    */
    /* -------------------------------------------------------------------------- */

    /**
     * 获取射线上距离 t 处的点。
     * @param t 距离系数 (必须 >= 0)
     * @return 空间中的点坐标
     */
    public Vector3f getPointAt(float t) {
        return new Vector3f(direction).mul(t).add(origin);
    }

    /**
     * 检测射线是否穿过一个 AABB。
     * 使用经典的 Slab 方法 (Amy Williams 算法优化版)。
     * * @param aabb 待检测的包围盒
     * @return 如果相交返回 true
     */
    public boolean intersectsAABB(AABB aabb) {
        float tmin = (aabb.getMin().x - origin.x) / direction.x;
        float tmax = (aabb.getMax().x - origin.x) / direction.x;

        if (tmin > tmax) { float temp = tmin; tmin = tmax; tmax = temp; }

        float tymin = (aabb.getMin().y - origin.y) / direction.y;
        float tymax = (aabb.getMax().y - origin.y) / direction.y;

        if (tymin > tymax) { float temp = tymin; tymin = tymax; tymax = temp; }

        if ((tmin > tymax) || (tymin > tmax)) return false;

        if (tymin > tmin) tmin = tymin;
        if (tymax < tmax) tmax = tymax;

        float tzmin = (aabb.getMin().z - origin.z) / direction.z;
        float tzmax = (aabb.getMax().z - origin.z) / direction.z;

        if (tzmin > tzmax) { float temp = tzmin; tzmin = tzmax; tzmax = temp; }

        if ((tmin > tzmax) || (tzmin > tmax)) return false;

        return tmax >= 0;
    }

    /* -------------------------------------------------------------------------- */
    /* Getter / Setter                                                            */
    /* -------------------------------------------------------------------------- */

    public Vector3f getOrigin() { return origin; }
    public Vector3f getDirection() { return direction; }

    public void setOrigin(float x, float y, float z) {
        this.origin.set(x, y, z);
    }

    public void setDirection(float x, float y, float z) {
        this.direction.set(x, y, z).normalize();
    }

    @Override
    public String toString() {
        return String.format("Ray(Origin: %s, Direction: %s)", origin, direction);
    }
}