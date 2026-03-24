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
    private final int vao;
    private final int vbo;
    private final VertexLayout layout;
    private int currentBufferSize;

    /**
     * @param initialVertices 初始预留的顶点数量
     * @param layout 顶点布局描述
     */
    public String name = "Unnamed Mesh";

    public GLMesh(int initialVertices, VertexLayout layout) {
        super(initialVertices);
        this.layout = layout;
        // 计算初始需要的字节总数
        this.currentBufferSize = initialVertices * layout.getStride();

        vao = glGenVertexArrays();
        vbo = glGenBuffers();

        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);

        // 初始化缓冲区。即使 initialVertices 为 0，也分配 1 字节防止某些驱动报错
        long sizeToAllocate = Math.max(1L, (long) currentBufferSize);
        glBufferData(GL_ARRAY_BUFFER, sizeToAllocate, GL_DYNAMIC_DRAW);

        // 根据 VertexLayout 自动配置属性指针
        for (int i = 0; i < layout.getAttributes().size(); i++) {
            VertexAttribute attr = layout.getAttributes().get(i);
            glEnableVertexAttribArray(i);
            glVertexAttribPointer(
                    i,
                    attr.count,
                    attr.type,
                    attr.normalized,
                    layout.getStride(),
                    (long) attr.offset
            );
        }

        glBindVertexArray(0);
        InternalLogger.debug("GLMesh: Created with stride " + layout.getStride() + ", Initial size: " + currentBufferSize);
    }

    /**
     * 将新的顶点数据写入 GPU 缓冲区
     * 如果数据量超过当前缓冲区大小，会自动触发重新分配（扩容）
     */
    public void updateVertices(float[] data) {
        if (data == null || data.length == 0) {
            this.vertexCount = 0;
            return;
        }

        int requiredSize = data.length * 4; // float 是 4 字节
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);

        // --- 核心修复：动态扩容逻辑 ---
        if (requiredSize > currentBufferSize) {
            // 重新分配空间 (Re-allocate)
            glBufferData(GL_ARRAY_BUFFER, (long) requiredSize, GL_DYNAMIC_DRAW);
            currentBufferSize = requiredSize;
            InternalLogger.info("GLMesh [" + vao + "]: Buffer resized to " + currentBufferSize + " bytes.");
        } else {
            // 空间足够，只更新子集数据 (性能更高)
            glBufferSubData(GL_ARRAY_BUFFER, 0, data);
        }

        // 更新当前可渲染的顶点数量
        this.vertexCount = requiredSize / layout.getStride();

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
    }

    @Override
    public void render() {
        if (vertexCount <= 0) return;

        glBindVertexArray(vao);
        // 如果你的 Mesh 包含索引缓冲区(EBO)，这里应该改用 glDrawElements
        glDrawArrays(GL_TRIANGLES, 0, vertexCount);
        glBindVertexArray(0);
    }

    public void bind() {
        glBindVertexArray(vao);
    }

    public void unbind() {
        glBindVertexArray(0);
    }

    @Override
    public void dispose() {
        glDeleteVertexArrays(vao);
        glDeleteBuffers(vbo);
        InternalLogger.info("GLMesh: Resources disposed (VAO: " + vao + ").");
    }
}