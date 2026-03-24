package com.zenith.render.backend.opengl;

import com.zenith.render.Camera;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class GLCamera extends Camera {

    public GLCamera() {
        super();
    }

    @Override
    public Matrix4f getViewMatrix() {
        // 绝对安全的 View Matrix 计算法：
        // 先生成相机的 Model 矩阵 (先平移后旋转)，然后直接整体求逆
        Matrix4f cameraModel = new Matrix4f()
                .translate(transform.getPosition())
                .rotate(transform.getRotation());
        return cameraModel.invert();
    }

    @Override
    public void lookAt(Vector3f target, Vector3f up) {
        Vector3f pos = transform.getPosition();
        // LookAt 得到的是 View 矩阵
        Matrix4f viewMatrix = new Matrix4f().lookAt(pos, target, up);

        // 逆矩阵就是相机的世界空间模型矩阵
        Matrix4f cameraModel = viewMatrix.invert();

        Quaternionf rotation = new Quaternionf();
        cameraModel.getUnnormalizedRotation(rotation);

        // 直接更新旋转四元数
        transform.getRotation().set(rotation);
    }
}