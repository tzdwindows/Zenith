package com.zenith.render.backend.opengl.test;

import com.zenith.asset.AssetIdentifier;
import com.zenith.asset.AssetResource;
import com.zenith.common.math.Color;
import com.zenith.render.VertexLayout;
import com.zenith.render.backend.opengl.GLWindow;
import com.zenith.render.backend.opengl.buffer.GLBufferBuilder;
import com.zenith.render.backend.opengl.shader.ImageShader;
import com.zenith.render.backend.opengl.texture.GLTexture;
import org.joml.Matrix4f;

import java.io.InputStream;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

public class Test2 {
    public static void main(String[] args) throws Exception {
        // 1. 初始化窗口
        GLWindow window = new GLWindow("Zenith Engine - BufferBuilder Test", 1280, 720);
        window.init();

        // 2. 在循环外加载资源（解决 Stream closed 问题的关键）
        ImageShader shader = new ImageShader();
        AssetIdentifier id = new AssetIdentifier("zenith", "textures/test.png");
        AssetResource res = loadFromResources(id);

        if (res == null) {
            System.err.println("错误：无法找到贴图资源！");
            return;
        }

        // 只实例化一次纹理
        GLTexture texture = new GLTexture(res);
        float texW = texture.getWidth();
        float texH = texture.getHeight();

        // 3. 准备 BufferBuilder 和 VAO/VBO
        // 假设你的 VertexLayout 已经能处理 Pos(3f) + UV(2f)
        VertexLayout layout = new VertexLayout();
        GLBufferBuilder builder = new GLBufferBuilder(1024 * 64);

        // 创建一个用于动态更新数据的 VAO
        int vao = glGenVertexArrays();
        int vbo = glGenBuffers();

        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);

        // 属性 0: Position (vec3)
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 5 * 4, 0);
        glEnableVertexAttribArray(0);
        // 属性 1: TexCoord (vec2)
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 5 * 4, 3 * 4);
        glEnableVertexAttribArray(1);

        glBindVertexArray(0);

        Matrix4f uiProj = new Matrix4f().ortho(0, 1280, 720, 0, -1, 1);

        // 4. 渲染循环
        while (!window.shouldClose()) {
            glClearColor(0.1f, 0.1f, 0.1f, 1.0f);
            glClear(GL_COLOR_BUFFER_BIT);

            // --- A. 使用 BufferBuilder 构建顶点数据 ---
            builder.begin(layout);
            builder.withTexture(texture); // 关联已加载的纹理

            float drawX = 100, drawY = 100;
            // 按照四个顶点构建 (Triangle Fan 模式)
            addVertex(builder, drawX, drawY, 0, 0);               // 左上
            addVertex(builder, drawX + texW, drawY, 1, 0);        // 右上
            addVertex(builder, drawX + texW, drawY + texH, 1, 1); // 右下
            addVertex(builder, drawX, drawY + texH, 0, 1);        // 左下

            // 结束构建，获取渲染数据包
            GLBufferBuilder.RenderedBuffer rb = builder.end();

            // --- B. 执行 OpenGL 渲染 ---
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

            shader.bind();
            shader.setup(uiProj, new Matrix4f(), Color.WHITE);

            // 自动绑定 RenderedBuffer 携带的纹理
            if (rb.texture() != null) {
                rb.texture().bind(0);
            }

            // 将 CPU 端的 ByteBuffer 数据上传到 GPU VBO
            glBindVertexArray(vao);
            glBindBuffer(GL_ARRAY_BUFFER, vbo);
            glBufferData(GL_ARRAY_BUFFER, rb.data(), GL_DYNAMIC_DRAW);

            // 执行绘制 (4个顶点)
            glDrawArrays(GL_TRIANGLE_FAN, 0, rb.vertexCount());

            glBindVertexArray(0);
            window.update();
        }

        // 5. 资源清理
        texture.dispose();
        builder.dispose();
        glDeleteVertexArrays(vao);
        glDeleteBuffers(vbo);
        window.dispose();
    }

    /**
     * 辅助方法：填充顶点数据 (Position + UV)
     */
    private static void addVertex(GLBufferBuilder builder, float x, float y, float u, float v) {
        builder.putFloat(x).putFloat(y).putFloat(0.0f) // Pos
                .putFloat(u).putFloat(v)                // UV
                .endVertex();
    }

    /**
     * 从 Classpath 加载资源
     */
    public static AssetResource loadFromResources(AssetIdentifier id) {
        String path = "/" + id.getPath();
        InputStream is = Test2.class.getResourceAsStream(path);
        if (is == null) return null;
        return new AssetResource("Resources", id, is, null,0);
    }
}