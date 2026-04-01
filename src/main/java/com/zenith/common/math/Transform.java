package com.zenith.common.math;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Transform 类管理物体的空间状态。
 * 包含位置、旋转和缩放，并负责生成模型矩阵。
 * * 使用公式: ModelMatrix = Translation * Rotation * Scale
 */
public class Transform {

    private final Vector3f position;
    private final Quaternionf rotation; // 使用四元数避免万向锁
    private final Vector3f scale;

    // 缓存模型矩阵，避免每一帧重复计算（只有在数据变动时才重构）
    private final Matrix4f modelMatrix;
    private boolean dirty = true;

    /**
     * 初始化默认变换：原点位置，无旋转，缩放为 1
     */
    public Transform() {
        this.position = new Vector3f(0.0f, 0.0f, 0.0f);
        this.rotation = new Quaternionf();
        this.scale = new Vector3f(1.0f, 1.0f, 1.0f);
        this.modelMatrix = new Matrix4f();
    }

    /* -------------------------------------------------------------------------- */
    /* 核心矩阵计算                                                                 */
    /* -------------------------------------------------------------------------- */

    /**
     * 获取当前变换的模型矩阵。
     * 如果数据发生过变动，则重新计算并更新缓存。
     * @return 4x4 模型矩阵
     */
    public Matrix4f getModelMatrix() {
        if (dirty) {
            modelMatrix.translationRotateScale(position, rotation, scale);
            dirty = false;
        }
        return modelMatrix;
    }

    /* -------------------------------------------------------------------------- */
    /* Setter 方法 (自动标记数据已脏)                                                */
    /* -------------------------------------------------------------------------- */

    public void setPosition(float x, float y, float z) {
        position.set(x, y, z);
        dirty = true;
    }

    public void addPosition(float x, float y, float z) {
        position.add(x, y, z);
        dirty = true;
    }

    /**
     * 使用欧拉角设置旋转（内部转为四元数）
     * @param x 绕X轴旋转角度（角度制）
     * @param y 绕Y轴旋转角度（角度制）
     * @param z 绕Z轴旋转角度（角度制）
     */
    public void setRotation(float x, float y, float z) {
        rotation.rotationXYZ(
                ZMath.toRadians(x),
                ZMath.toRadians(y),
                ZMath.toRadians(z)
        );
        dirty = true;
    }

    public void setRotation(Quaternionf q) {
        this.rotation.set(q);
        this.dirty = true;
    }

    public void setScale(float x, float y, float z) {
        scale.set(x, y, z);
        dirty = true;
    }

    /**
     * 旋转变换：增加旋转增量
     * @param dx 绕X轴旋转弧度
     * @param dy 绕Y轴旋转弧度
     * @param dz 绕Z轴旋转弧度
     */
    public void rotate(float dx, float dy, float dz) {
        rotation.rotateXYZ(dx, dy, dz);
        dirty = true;
    }

    public void setScale(float s) {
        scale.set(s, s, s);
        dirty = true;
    }

    /* -------------------------------------------------------------------------- */
    /* Getter 方法                                                                 */
    /* -------------------------------------------------------------------------- */

    public Vector3f getPosition() { return position; }
    public Quaternionf getRotation() { return rotation; }
    public Vector3f getScale() { return scale; }

    /**
     * 重置变换状态
     */
    public void identity() {
        position.set(0, 0, 0);
        rotation.identity();
        scale.set(1, 1, 1);
        dirty = true;
    }

    /**
     * 将当前的变换矩阵拷贝到目标矩阵中。
     * 解决了外部调用 "getTransformationMatrix" 无法解析的问题。
     * @param dest 目标矩阵
     * @return 传入的目标矩阵（链式调用支持）
     */
    public Matrix4f getTransformationMatrix(Matrix4f dest) {
        Matrix4f currentModel = getModelMatrix();
        return dest.set(currentModel);
    }

    public Vector3f getForward() {
        return rotation.transform(new Vector3f(0, 0, -1));
    }

    public Vector3f getUp() {
        return rotation.transform(new Vector3f(0, 1, 0));
    }

    public Vector3f getRight() {
        return rotation.transform(new Vector3f(1, 0, 0));
    }

    public void setDirty() {
        this.dirty = true;
    }
}