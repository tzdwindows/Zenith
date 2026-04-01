package com.zenith.animation.runtime;

import com.zenith.common.math.Transform;
import com.zenith.common.utils.InternalLogger;
import org.joml.Matrix4f;

/**
 * LocalToModelJob 负责将局部变换转换为模型空间全局矩阵。
 * 核心原理：GlobalMatrix = ParentGlobalMatrix * LocalMatrix
 */
public class LocalToModelJob {

    /**
     * 执行坐标变换计算
     * @param skeleton 骨架（提供父子层级关系）
     * @param inputLocals 采样得到的局部变换数组 (Transform[])
     * @param outputModels 输出的模型空间矩阵数组 (Matrix4f[])
     */
    public void execute(Skeleton skeleton, Transform[] inputLocals, Matrix4f[] outputModels) {
        int numJoints = skeleton.numJoints();
        int[] parents = skeleton.getParentIndices();

        for (int i = 0; i < numJoints; i++) {
            // 1. 获取当前骨骼的局部矩阵
            // 注意：Transform.getModelMatrix() 内部处理了 Translation * Rotation * Scale
            Matrix4f localMat = inputLocals[i].getModelMatrix();

            int parentIdx = parents[i];

            if (parentIdx == -1) {
                // 2a. 根骨骼：模型空间矩阵就是局部矩阵
                outputModels[i].set(localMat);
            } else {
                // 2b. 子骨骼：模型空间矩阵 = 父骨骼模型空间矩阵 * 局部矩阵
                // 工业级警告：必须保证 i > parentIdx，即父骨骼永远在子骨骼之前被计算
                if (parentIdx >= i) {
                    InternalLogger.error("Skeleton hierarchy error: Parent index must be smaller than child index!");
                    continue;
                }

                Matrix4f parentModelMat = outputModels[parentIdx];
                // 使用 JOML 的 mul 方法：dest = left * right
                parentModelMat.mul(localMat, outputModels[i]);
            }
        }
    }
}