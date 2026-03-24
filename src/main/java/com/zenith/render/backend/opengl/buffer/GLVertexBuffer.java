package com.zenith.render.backend.opengl.buffer;

import com.zenith.render.VertexLayout;
import static org.lwjgl.opengl.GL30.*;

public class GLVertexBuffer {
    private int vao;
    private int vbo;
    private int vertexCount;

    public GLVertexBuffer() {
        this.vao = glGenVertexArrays();
        this.vbo = glGenBuffers();
    }

    public void upload(GLBufferBuilder.RenderedBuffer rendered) {
        this.vertexCount = rendered.vertexCount();
        if (vertexCount == 0) return;

        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);

        glBufferData(GL_ARRAY_BUFFER, rendered.data(), GL_DYNAMIC_DRAW);

        setupAttributes(rendered.layout());

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
    }

    private void setupAttributes(VertexLayout layout) {
        int strideBytes = layout.getStride(); // 必须获取的是准确的字节数
        var attributes = layout.getAttributes();

        for (int i = 0; i < attributes.size(); i++) {
            var attr = attributes.get(i);
            glEnableVertexAttribArray(i);

            // attr.offset 必须是 VertexLayout 累加出来的准确字节偏移
            // attr.count 是组件数量 (例如 3)
            // 0x1406 是 GL_FLOAT
            glVertexAttribPointer(
                    i,
                    attr.count,
                    0x1406,
                    false, // 是否归一化，根据你的需求
                    strideBytes,
                    attr.offset
            );
        }
    }



    public void draw() {
        if (vertexCount == 0) return;
        glBindVertexArray(vao);
        glDrawArrays(GL_TRIANGLES, 0, vertexCount);
        glBindVertexArray(0);
    }

    public void dispose() {
        glDeleteVertexArrays(vao);
        glDeleteBuffers(vbo);
    }
}