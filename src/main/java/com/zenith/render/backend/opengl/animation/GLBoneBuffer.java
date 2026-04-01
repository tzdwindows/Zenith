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
    private final int maxJoints = 100; // 【修改】强制设为 100，与 Shader 保持一致
    private final int size;

    public GLBoneBuffer(int numJoints) {
        // 【修改】不管模型有多少骨骼，缓冲区必须分配 100 个矩阵的空间
        this.size = maxJoints * 16 * 4;

        this.rendererID = glGenBuffers();
        glBindBuffer(GL_UNIFORM_BUFFER, rendererID);
        glBufferData(GL_UNIFORM_BUFFER, size, GL_DYNAMIC_DRAW);
        glBindBuffer(GL_UNIFORM_BUFFER, 0);

        InternalLogger.info(String.format("GLBoneBuffer allocated for %d joints (Fixed Size: %d bytes)", maxJoints, size));
    }

    public void update(FloatBuffer skinningMatrices) {
        // 即使只更新前几个矩阵，也要确保不越界
        int updateSize = Math.min(skinningMatrices.remaining(), maxJoints * 16) * 4;

        glBindBuffer(GL_UNIFORM_BUFFER, rendererID);
        // 只更新实际有数据的部分，偏移量从 0 开始
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