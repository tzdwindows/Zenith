package com.zenith.common.math;

import org.joml.Matrix4f;

/**
 * Frustum 类表示摄像机的视锥体。
 * 由 6 个裁剪平面组成：左、右、底、顶、近、远。
 */
public class Frustum {

    private final Plane[] planes;

    // 平面索引常量
    private static final int LEFT   = 0;
    private static final int RIGHT  = 1;
    private static final int BOTTOM = 2;
    private static final int TOP    = 3;
    private static final int NEAR   = 4;
    private static final int FAR    = 5;

    public Frustum() {
        this.planes = new Plane[6];
        for (int i = 0; i < 6; i++) {
            this.planes[i] = new Plane(0, 0, 0, 0);
        }
    }

    /**
     * 根据视图投影矩阵 (View-Projection Matrix) 更新视锥体平面。
     * 通常在摄像机移动或投影属性改变后调用。
     * * @param m 组合矩阵 (Projection * View)
     */
    public void update(Matrix4f m) {
        // 左平面
        planes[LEFT].set(m.m03() + m.m00(), m.m13() + m.m10(), m.m23() + m.m20(), m.m33() + m.m30());
        // 右平面
        planes[RIGHT].set(m.m03() - m.m00(), m.m13() - m.m10(), m.m23() - m.m20(), m.m33() - m.m30());
        // 底平面
        planes[BOTTOM].set(m.m03() + m.m01(), m.m13() + m.m11(), m.m23() + m.m21(), m.m33() + m.m31());
        // 顶平面
        planes[TOP].set(m.m03() - m.m01(), m.m13() - m.m11(), m.m23() - m.m21(), m.m33() - m.m31());
        // 近平面
        planes[NEAR].set(m.m03() + m.m02(), m.m13() + m.m12(), m.m23() + m.m22(), m.m33() + m.m32());
        // 远平面
        planes[FAR].set(m.m03() - m.m02(), m.m13() - m.m12(), m.m23() - m.m22(), m.m33() - m.m32());
    }

    /**
     * 检测一个 AABB 是否在视锥体内（即是否可见）。
     * 只要物体在任意一个平面的完全外面，则不可见。
     * * @param aabb 待检测物体的包围盒
     * @return true 如果物体可能可见（部分或全部在视锥体内）
     */
    public boolean isVisible(AABB aabb) {
        for (Plane plane : planes) {
            // 如果 AABB 在任何一个平面的外面，则剔除
            if (plane.isOutside(aabb)) {
                return false;
            }
        }
        return true;
    }
}