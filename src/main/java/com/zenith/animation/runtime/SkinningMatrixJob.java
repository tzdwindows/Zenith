package com.zenith.animation.runtime;

import org.joml.Matrix4f;
import java.nio.FloatBuffer;

/**
 * SkinningMatrixJob 负责计算最终上传至 Shader 的蒙皮矩阵数组。
 * 它是 CPU 动画管线的最后一步。
 */
public class SkinningMatrixJob {

    // 预分配临时矩阵，避免在循环中创建对象
    private final Matrix4f tempMatrix = new Matrix4f();

    /**
     * 执行蒙皮矩阵计算
     * @param skeleton 提供静态的 Inverse Bind Poses
     * @param modelMatrices LocalToModelJob 计算出的当前骨骼全局矩阵
     * @param outputBuffer 目标缓冲区 (大小应为 numJoints * 16 * 4 字节)
     */
    public void execute(Skeleton skeleton, Matrix4f[] modelMatrices, FloatBuffer outputBuffer) {
        int numJoints = skeleton.numJoints();

        // 确保 Buffer 指针回到起始位置
        outputBuffer.rewind();

        for (int i = 0; i < numJoints; i++) {
            // 1. 获取当前骨骼在模型空间中的变换
            Matrix4f currentPose = modelMatrices[i];

            // 2. 获取该骨格的逆绑定矩阵
            Matrix4f invBindPose = skeleton.getInverseBindPose(i);

            // 3. 计算蒙皮矩阵：Skinning = CurrentModelPose * InverseBindPose
            // 注意：顺序不能错，先变换到骨骼空间，再变换到当前姿态空间
            currentPose.mul(invBindPose, tempMatrix);

            // 4. 将 16 个 float 写入 Buffer
            // JOML 的 Matrix4f.get(FloatBuffer) 会自动将矩阵存入并移动 Buffer 指针
            tempMatrix.get(outputBuffer);

            // 手动确保指针对齐到下一个矩阵的开头（每 16 个 float）
            outputBuffer.position((i + 1) * 16);
        }

        // 重置指针，方便后续 glBufferData 等调用直接从头读取
        outputBuffer.rewind();
    }
}