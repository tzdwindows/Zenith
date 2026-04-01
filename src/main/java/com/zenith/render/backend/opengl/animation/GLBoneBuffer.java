package com.zenith.render.backend.opengl.animation;

import com.zenith.common.utils.InternalLogger;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL31.GL_UNIFORM_BUFFER;

/**
 * GLBoneBuffer 负责管理 GPU 端的骨骼矩阵缓冲区 (UBO)。
 * 它可以高效地更新数百个骨骼矩阵，并供多个 Shader 同时读取。
 */
public class GLBoneBuffer {

    private final int rendererID;
    private final int maxJoints;
    private final int size;

    /**
     * @param maxJoints 该缓冲区支持的最大骨骼数量（通常与 Shader 中的 100 对应）
     */
    public GLBoneBuffer(int maxJoints) {
        this.maxJoints = maxJoints;
        // 每个矩阵 16 个 float，每个 float 4 字节
        this.size = maxJoints * 16 * 4;

        // 1. 创建 UBO
        this.rendererID = glGenBuffers();
        glBindBuffer(GL_UNIFORM_BUFFER, rendererID);

        // 2. 初始化缓冲区大小，使用 GL_DYNAMIC_DRAW 因为每一帧都会更新
        glBufferData(GL_UNIFORM_BUFFER, size, GL_DYNAMIC_DRAW);

        glBindBuffer(GL_UNIFORM_BUFFER, 0);
        InternalLogger.info(String.format("GLBoneBuffer created for %d joints (Size: %d bytes)", maxJoints, size));
    }

    /**
     * 将 CPU 计算好的蒙皮矩阵上传到 GPU。
     * 建议传入由 SkinningMatrixJob 直接填充的 FloatBuffer。
     */
    public void update(FloatBuffer skinningMatrices) {
        // 确保传入的 Buffer 大小合法
        if (skinningMatrices.remaining() > maxJoints * 16) {
            InternalLogger.error("Skinning matrices buffer exceeds GLBoneBuffer capacity!");
            return;
        }

        glBindBuffer(GL_UNIFORM_BUFFER, rendererID);
        // 使用 glBufferSubData 仅更新数据而不重新分配显存，这是性能最优解
        glBufferSubData(GL_UNIFORM_BUFFER, 0, skinningMatrices);
        glBindBuffer(GL_UNIFORM_BUFFER, 0);
    }

    /**
     * 将此缓冲区绑定到指定的 Binding Point。
     * 在 Shader 中需要使用 layout(std140, binding = X) 来匹配。
     * @param bindingSlot 绑定的槽位索引（例如 1）
     */
    public void bind(int bindingSlot) {
        glBindBufferBase(GL_UNIFORM_BUFFER, bindingSlot, rendererID);
    }

    /**
     * 释放 GPU 资源
     */
    public void dispose() {
        glDeleteBuffers(rendererID);
        InternalLogger.info("GLBoneBuffer disposed.");
    }

    public int getRendererID() {
        return rendererID;
    }
}