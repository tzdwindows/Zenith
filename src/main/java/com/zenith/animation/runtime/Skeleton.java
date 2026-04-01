package com.zenith.animation.runtime;

import org.joml.Matrix4f;
import java.util.HashMap;
import java.util.Map;

public class Skeleton {
    private final int[] parentIndices;
    private final String[] jointNames;
    private final Matrix4f[] inverseBindPoses;
    private final Map<String, Integer> nameToIndexMap;

    public Skeleton(int[] parentIndices, String[] jointNames, Matrix4f[] inverseBindPoses) {
        // 执行拷贝确保原始数据安全
        this.parentIndices = parentIndices.clone();
        this.jointNames = jointNames.clone();

        this.inverseBindPoses = new Matrix4f[inverseBindPoses.length];
        for (int i = 0; i < inverseBindPoses.length; i++) {
            this.inverseBindPoses[i] = new Matrix4f(inverseBindPoses[i]);
        }

        this.nameToIndexMap = new HashMap<>();
        for (int i = 0; i < jointNames.length; i++) {
            nameToIndexMap.put(jointNames[i], i);
        }

        validate();
    }

    /**
     * 工业级校验：确保骨架数据满足拓扑排序。
     * 这是 LocalToModelJob 能够线性 (O(N)) 运行的前提。
     */
    private void validate() {
        if (parentIndices.length != jointNames.length || parentIndices.length != inverseBindPoses.length) {
            throw new IllegalArgumentException("Skeleton: Array lengths must match!");
        }
        for (int i = 0; i < parentIndices.length; i++) {
            // 根节点必须是第一个且父索引为 -1
            if (i == 0 && parentIndices[i] != -1) {
                throw new IllegalStateException("Skeleton: Root joint must have parent index -1.");
            }
            // 拓扑排序校验：父节点必须在子节点之前出现
            if (i > 0 && parentIndices[i] >= i) {
                throw new IllegalStateException("Skeleton hierarchy error: parent index (" + parentIndices[i] +
                        ") must be smaller than child index (" + i + ") for linear processing.");
            }
        }
    }

    // --- 修复编译错误的方法 ---

    /**
     * 获取所有骨骼的父级索引数组。
     * 注意：直接返回引用以供 LocalToModelJob 高效遍历。
     */
    public int[] getParentIndices() {
        return parentIndices;
    }

    // --- 其他功能方法 ---

    public int findJointIndex(String name) {
        return nameToIndexMap.getOrDefault(name, -1);
    }

    public boolean isRoot(int index) {
        return parentIndices[index] == -1;
    }

    public int numJoints() {
        return parentIndices.length;
    }

    public int getParentIndex(int jointIndex) {
        return parentIndices[jointIndex];
    }

    public String getJointName(int jointIndex) {
        return jointNames[jointIndex];
    }

    public Matrix4f getInverseBindPose(int jointIndex) {
        return inverseBindPoses[jointIndex];
    }

    /**
     * 返回完整的逆绑定矩阵数组引用。
     * 供 SkinningMatrixJob 批量计算使用。
     */
    public Matrix4f[] getInverseBindPoses() {
        return inverseBindPoses;
    }
}