package com.zenith.common.math;

import org.joml.Matrix4f;

/**
 * Projection 类管理摄像机的投影矩阵。
 * 支持透视投影 (Perspective) 和 正交投影 (Orthographic)。
 */
public class Projection {

    private final Matrix4f projectionMatrix;

    // 透视投影参数
    private float fov;    // 视野角度 (Field of View)
    private float zNear;  // 近裁剪面
    private float zFar;   // 远裁剪面

    // 正交投影参数
    private float width;
    private float height;

    private boolean isPerspective;
    private boolean dirty = true;

    public Projection() {
        this(1280, 720);
    }

    /**
     * 初始化为默认透视投影
     */
    public Projection(int width, int height) {
        this.projectionMatrix = new Matrix4f();
        this.fov = ZMath.toRadians(60.0f); // 默认 60 度 FOV
        this.zNear = 0.01f;
        this.zFar = 1000.0f;
        this.width = (float) width;
        this.height = (float) height;
        this.isPerspective = true;
    }

    /* -------------------------------------------------------------------------- */
    /* 核心矩阵计算                                                                */
    /* -------------------------------------------------------------------------- */

    /**
     * 获取更新后的投影矩阵
     */
    public Matrix4f getProjectionMatrix() {
        if (dirty) {
            float aspectRatio = width / height;
            projectionMatrix.identity();

            if (isPerspective) {
                projectionMatrix.perspective(fov, aspectRatio, zNear, zFar);
            } else {
                // 正交投影：左, 右, 下, 上, 近, 远
                projectionMatrix.ortho(0, width, height, 0, zNear, zFar);
            }
            dirty = false;
        }
        return projectionMatrix;
    }

    /* -------------------------------------------------------------------------- */
    /* 设置与更新                                                                  */
    /* -------------------------------------------------------------------------- */

    /**
     * 当窗口大小改变时调用，确保投影不会拉伸
     */
    public void updateSize(int width, int height) {
        this.width = (float) width;
        this.height = (float) height;
        this.dirty = true;
    }

    public void setFOV(float degrees) {
        this.fov = ZMath.toRadians(degrees);
        this.dirty = true;
    }

    public void setPerspective(boolean usePerspective) {
        if (this.isPerspective != usePerspective) {
            this.isPerspective = usePerspective;
            this.dirty = true;
        }
    }

    public void setClippingPlanes(float near, float far) {
        this.zNear = near;
        this.zFar = far;
        this.dirty = true;
    }

    /* -------------------------------------------------------------------------- */
    /* Getter                                                                     */
    /* -------------------------------------------------------------------------- */

    public boolean isPerspective() { return isPerspective; }

    public Matrix4f getMatrix() {
        if (dirty) {
            float aspectRatio = width / height;
            projectionMatrix.identity();

            if (isPerspective) {
                projectionMatrix.perspective(fov, aspectRatio, zNear, zFar);
            } else {
                projectionMatrix.ortho(0, width, height, 0, zNear, zFar);
            }
            dirty = false;
        }
        return projectionMatrix;
    }

    public void setFar(float far) {
        this.zFar = far;
        this.dirty = true;
    }

    public void setNear(float near) {
        this.zNear = near;
        this.dirty = true;
    }
    public float getFar() {
        return zFar;
    }
}