package com.zenith.render;

import com.zenith.common.math.Transform;
import com.zenith.common.math.Projection;
import com.zenith.common.utils.InternalLogger;
import org.joml.Vector3f;

/**
 * Camera 负责管理观察者视角。
 * 它结合 Transform (位置/旋转) 和 Projection (透视/正交) 生成 MVP 矩阵。
 */
public abstract class Camera {
    protected Transform transform;
    protected Projection projection;
    protected Camera() {
        this.transform = new Transform();
        this.projection = new Projection();
        InternalLogger.info("Camera initialized.");
    }

    /**
     * 获取观察矩阵 (View Matrix)
     */
    public abstract org.joml.Matrix4f getViewMatrix();

    /**
     * 让相机看向目标点
     * @param target 观察的目标点 (世界坐标)
     * @param up 向上向量，通常为 (0, 1, 0)
     */
    public abstract void lookAt(Vector3f target, Vector3f up);

    /**
     * 获取投影矩阵 (Projection Matrix)
     */
    public org.joml.Matrix4f getProjectionMatrix() {
        return projection.getMatrix();
    }

    public Transform getTransform() {
        return transform;
    }

    public Projection getProjection() {
        return projection;
    }

    public Vector3f getForward() {
        return transform.getForward();
    }

    public Vector3f getUp() {
        return transform.getUp();
    }

    /**
     * 获取相机的右向量 (Right Vector)
     */
    public Vector3f getRight() {
        return transform.getRight();
    }

    public org.joml.Matrix4f getViewProjectionMatrix() {
        org.joml.Matrix4f vpm = new org.joml.Matrix4f();
        return getProjectionMatrix().mul(getViewMatrix(), vpm);
    }
}