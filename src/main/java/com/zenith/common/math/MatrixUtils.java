package com.zenith.common.math;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * MatrixUtils 提供针对渲染管线优化的矩阵生成工具。
 * 主要用于创建视图矩阵、投影矩阵以及 UI 专用矩阵。
 */
public final class MatrixUtils {

    private MatrixUtils() {}

    /**
     * 创建一个“观察”矩阵 (View Matrix)。
     * 模拟摄像机在空间中的位置和朝向。
     * * @param eye    摄像机位置
     * @param center 摄像机注视的目标点
     * @param up     世界向上的向量 (通常为 0, 1, 0)
     * @return 视图矩阵
     */
    public static Matrix4f createViewMatrix(Vector3f eye, Vector3f center, Vector3f up) {
        return new Matrix4f().lookAt(eye, center, up);
    }

    /**
     * 为 2D 渲染（如 UI 或 2D 游戏）创建正交投影矩阵。
     * 将屏幕坐标（像素）直接映射到 NDC 空间。
     * * @param width  窗口宽度
     * @param height 窗口高度
     * @return 正交矩阵
     */
    public static Matrix4f createOrthoMatrix(float width, float height) {
        // 设置左上角为 (0,0)，右下角为 (width, height)
        // 近平面 -1.0，远平面 1.0
        return new Matrix4f().ortho(0, width, height, 0, -1.0f, 1.0f);
    }

    /**
     * 创建透视投影矩阵。
     * * @param fov         视野角度 (弧度)
     * @param aspectRatio 屏幕长宽比 (width/height)
     * @param zNear       近裁剪面 (不可为 0)
     * @param zFar        远裁剪面
     * @return 透视矩阵
     */
    public static Matrix4f createPerspectiveMatrix(float fov, float aspectRatio, float zNear, float zFar) {
        return new Matrix4f().perspective(fov, aspectRatio, zNear, zFar);
    }

    /**
     * 从 Transform 对象直接生成模型矩阵。
     * 封装了 T * R * S 的计算过程。
     */
    public static Matrix4f createModelMatrix(Transform transform) {
        return new Matrix4f().translationRotateScale(
                transform.getPosition(),
                transform.getRotation(),
                transform.getScale()
        );
    }

    /**
     * 将 4x4 矩阵转换为一维浮点数组。
     * 常用于旧版 OpenGL 或某些特定 Buffer 的写入。
     */
    public static float[] toArray(Matrix4f matrix) {
        float[] dest = new float[16];
        return matrix.get(dest);
    }
}