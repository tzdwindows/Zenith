package com.zenith.render.backend.opengl.buffer;

import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL43.*;

public class GLSSBO {

    private final int rendererID;
    private final int bindingPoint;

    public GLSSBO(int bindingPoint) {
        this.bindingPoint = bindingPoint;
        this.rendererID = glGenBuffers();

        if (rendererID == 0) {
            throw new IllegalStateException("Failed to create SSBO");
        }
    }

    public void setData(float[] data) {
        if (data == null || data.length == 0) return;

        glBindBuffer(GL_SHADER_STORAGE_BUFFER, rendererID);

        FloatBuffer buffer = BufferUtils.createFloatBuffer(data.length);
        buffer.put(data).flip();

        // 👉 一步上传（稳定）
        glBufferData(GL_SHADER_STORAGE_BUFFER, buffer, GL_DYNAMIC_DRAW);

        glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);
    }

    public void bind() {
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, bindingPoint, rendererID);
    }

    public void dispose() {
        glDeleteBuffers(rendererID);
    }

    public int getRendererID() {
        return rendererID;
    }
}