package com.zenith.render.backend.opengl.buffer;

import static org.lwjgl.opengl.GL43.*;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

public class GLSSBO {
    private final int rendererID;
    private final int bindingPoint;

    public GLSSBO(int bindingPoint) {
        this.bindingPoint = bindingPoint;
        this.rendererID = glGenBuffers();
    }

    // 支持 FloatBuffer (JOML 常用)
    public void setData(FloatBuffer data) {
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, rendererID);
        glBufferData(GL_SHADER_STORAGE_BUFFER, data, GL_STATIC_DRAW);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, bindingPoint, rendererID);
    }

    public void setData(float[] data) {
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, rendererID);
        // 使用 LWJGL 提供的 float[] 重载
        glBufferData(GL_SHADER_STORAGE_BUFFER, data, GL_STATIC_DRAW);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, bindingPoint, rendererID);
    }

    public void setData(int[] data) {
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, rendererID);
        glBufferData(GL_SHADER_STORAGE_BUFFER, data, GL_STATIC_DRAW);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, bindingPoint, rendererID);
    }

    // 支持 IntBuffer (用于存储 BVH 节点索引或三角形索引)
    public void setData(IntBuffer data) {
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, rendererID);
        glBufferData(GL_SHADER_STORAGE_BUFFER, data, GL_STATIC_DRAW);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, bindingPoint, rendererID);
    }

    // 允许部分更新 (工业级优化：只更新变动的光源或相机数据)
    public void setSubData(long offset, float[] data) {
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, rendererID);
        glBufferSubData(GL_SHADER_STORAGE_BUFFER, offset, data);
    }

    public void bind() {
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, bindingPoint, rendererID);
    }

    public void dispose() {
        glDeleteBuffers(rendererID);
    }

    public int getRendererID() { return rendererID; }
}