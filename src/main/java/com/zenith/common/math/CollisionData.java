package com.zenith.common.math;

import org.joml.Vector3f;

/**
 * 存储碰撞发生的详细信息。
 */
public class CollisionData {
    public boolean intersected = false;
    public Vector3f normal = new Vector3f(); // 碰撞表面的法线
    public float depth = 0.0f;               // 穿透深度
    public Vector3f contactPoint = new Vector3f(); // 具体的碰撞点坐标

    public void reset() {
        intersected = false;
        normal.set(0);
        depth = 0;
        contactPoint.set(0);
    }
}