package com.zenith.common.math;

import org.joml.Vector3f;

/**
 * Plane 表示 3D 空间中的一个无穷大平面。
 * 平面方程: ax + by + cz + d = 0
 * 其中 (a, b, c) 是法线，d 是平面到原点的有向距离。
 */
public class Plane {

    private final Vector3f normal;
    private float distance;

    /**
     * 通过法线和平面上的一个点构造平面
     */
    public Plane(Vector3f normal, Vector3f point) {
        this.normal = new Vector3f(normal).normalize();
        this.distance = -this.normal.dot(point);
    }

    /**
     * 直接通过法线分量和距离 D 构造
     */
    public Plane(float nx, float ny, float nz, float d) {
        this.normal = new Vector3f(nx, ny, nz).normalize();
        this.distance = d;
    }

    /* -------------------------------------------------------------------------- */
    /* 核心逻辑                                                                    */
    /* -------------------------------------------------------------------------- */

    /**
     * 计算点到平面的带符号距离。
     * 返回 > 0: 点在平面正面（法线指向的一侧）
     * 返回 < 0: 点在平面反面
     * 返回 = 0: 点在平面上
     */
    public float getSignedDistance(Vector3f point) {
        return normal.dot(point) + distance;
    }

    /**
     * 检测 AABB 是否在平面的“外面”（完全在反面）。
     * 这是视锥体剔除的关键算法。
     * * @return true 如果 AABB 完全在平面的负半空间
     */
    public boolean isOutside(AABB aabb) {
        // 找到 AABB 上最靠近法线方向的“最远点”
        float x = (normal.x >= 0) ? aabb.getMax().x : aabb.getMin().x;
        float y = (normal.y >= 0) ? aabb.getMax().y : aabb.getMin().y;
        float z = (normal.z >= 0) ? aabb.getMax().z : aabb.getMin().z;

        // 如果这个最靠近法线方向的点都在平面后面，那整个 AABB 都在后面
        return (normal.x * x + normal.y * y + normal.z * z + distance) < 0;
    }

    /* -------------------------------------------------------------------------- */
    /* Getter / Setter                                                            */
    /* -------------------------------------------------------------------------- */

    public Vector3f getNormal() { return normal; }
    public float getDistance() { return distance; }

    /**
     * 重新设置平面的参数。
     * @param nx 法线 X 分量
     * @param ny 法线 Y 分量
     * @param nz 法线 Z 分量
     * @param d  平面到原点的距离
     */
    public void set(float nx, float ny, float nz, float d) {
        // 设置法线
        this.normal.set(nx, ny, nz);

        // 计算法线的长度，用于归一化
        float length = normal.length();

        // 归一化法线和距离（这一步对视锥体剔除的准确性至关重要）
        this.normal.div(length);
        this.distance = d / length;
    }

    public void set(Vector3f normal, float distance) {
        this.normal.set(normal).normalize();
        this.distance = distance;
    }

    @Override
    public String toString() {
        return String.format("Plane(Normal: %s, D: %.2f)", normal, distance);
    }
}