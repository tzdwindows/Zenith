package com.zenith.common.math;

import org.joml.Vector2f;
import org.joml.Vector3f;

/**
 * Barycentric 提供重心坐标计算工具。
 * 用于计算点在三角形内的位置比例，常用于地形高度采样和纹理插值。
 */
public final class Barycentric {

    private Barycentric() {}

    /**
     * 计算 3D 空间中点 P 在三角形 (A, B, C) 上的重心坐标。
     * @return 返回 Vector3f(u, v, w)，满足 P = uA + vB + wC 且 u+v+w=1
     */
    public static Vector3f calculate(Vector3f p, Vector3f a, Vector3f b, Vector3f c) {
        Vector3f v0 = new Vector3f(b).sub(a);
        Vector3f v1 = new Vector3f(c).sub(a);
        Vector3f v2 = new Vector3f(p).sub(a);

        float d00 = v0.dot(v0);
        float d01 = v0.dot(v1);
        float d11 = v1.dot(v1);
        float d20 = v2.dot(v0);
        float d21 = v2.dot(v1);

        float denom = d00 * d11 - d01 * d01;

        // 防止除以零（退化三角形）
        if (Math.abs(denom) < 1e-6f) return new Vector3f(-1, -1, -1);

        float v = (d11 * d20 - d01 * d21) / denom;
        float w = (d00 * d21 - d01 * d20) / denom;
        float u = 1.0f - v - w;

        return new Vector3f(u, v, w);
    }

    /**
     * 判断点是否在三角形内部。
     */
    public static boolean isInside(Vector3f barycentric) {
        return barycentric.x >= 0 && barycentric.y >= 0 && barycentric.z >= 0;
    }
}