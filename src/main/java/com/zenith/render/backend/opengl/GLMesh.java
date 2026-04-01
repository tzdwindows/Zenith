package com.zenith.render.backend.opengl;

import com.zenith.common.utils.InternalLogger;
import com.zenith.render.Mesh;
import com.zenith.render.VertexLayout;
import com.zenith.render.VertexAttribute;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

public class GLMesh extends Mesh {
    protected final int vao;
    protected final int vbo;
    protected final int ebo; // 新增：索引缓冲区
    protected final VertexLayout layout;
    protected int currentBufferSize;
    protected int indexCount = 0; // 新增：记录索引数量

    public String name = "Unnamed Mesh";

    public GLMesh(int initialVertices, VertexLayout layout) {
        super(initialVertices);
        this.layout = layout;
        this.currentBufferSize = initialVertices * layout.getStride();

        vao = glGenVertexArrays();
        vbo = glGenBuffers();
        ebo = glGenBuffers(); // 生成 EBO

        glBindVertexArray(vao);

        // 配置 VBO
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        long sizeToAllocate = Math.max(1L, (long) currentBufferSize);
        glBufferData(GL_ARRAY_BUFFER, sizeToAllocate, GL_DYNAMIC_DRAW);

        // 配置 VertexLayout
        for (int i = 0; i < layout.getAttributes().size(); i++) {
            VertexAttribute attr = layout.getAttributes().get(i);
            glEnableVertexAttribArray(i);
            glVertexAttribPointer(i, attr.count, attr.type, attr.normalized, layout.getStride(), (long) attr.offset);
        }

        // 绑定空 EBO（占位，之后通过 updateIndices 填充）
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);

        glBindVertexArray(0);
    }

    /**
     * 上传索引数据
     */
    public void updateIndices(int[] data) {
        if (data == null || data.length == 0) return;

        this.indexCount = data.length;
        glBindVertexArray(vao);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, data, GL_DYNAMIC_DRAW);
        glBindVertexArray(0);
    }

    public void updateVertices(float[] data) {
        if (data == null || data.length == 0) {
            this.vertexCount = 0;
            return;
        }

        int requiredSize = data.length * 4;
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);

        if (requiredSize > currentBufferSize) {
            glBufferData(GL_ARRAY_BUFFER, (long) requiredSize, GL_DYNAMIC_DRAW);
            currentBufferSize = requiredSize;
        } else {
            glBufferSubData(GL_ARRAY_BUFFER, 0, data);
        }

        this.vertexCount = data.length / (layout.getStride() / 4);
        glBindVertexArray(0);
    }

    @Override
    public void render() {
        glBindVertexArray(vao);
        if (indexCount > 0) {
            // 如果有索引，使用索引渲染
            glDrawElements(GL_TRIANGLES, indexCount, GL_UNSIGNED_INT, 0);
        } else {
            glDrawArrays(GL_TRIANGLES, 0, vertexCount);
        }
        glBindVertexArray(0);
    }

    // --- 新增 Getter 供子类使用 ---
    public int getVboID() { return vbo; }
    public int getVaoID() { return vao; }

    public void bind() { glBindVertexArray(vao); }
    public void unbind() { glBindVertexArray(0); }

    @Override
    public void dispose() {
        glDeleteVertexArrays(vao);
        glDeleteBuffers(vbo);
        glDeleteBuffers(ebo);
    }
}