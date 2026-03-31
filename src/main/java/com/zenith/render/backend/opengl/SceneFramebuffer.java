package com.zenith.render.backend.opengl;

import com.zenith.render.backend.opengl.shader.ScreenShader;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

public class SceneFramebuffer {
    private int fbo;
    private int colorTex;
    private int depthTex;

    private int copyFbo;
    private int colorCopyTex;
    private int depthCopyTex;

    private int width, height;

    // 使用继承自 GLShader 的类
    private int quadVao = 0;
    private int quadVbo = 0;
    private ScreenShader screenShader;

    public SceneFramebuffer(int width, int height) {
        this.width = width;
        this.height = height;
        init();
    }

    private void init() {
        // 1. 初始化主场景 FBO
        fbo = glGenFramebuffers();
        colorTex = createTexture(GL_RGBA16F, GL_RGBA, GL_FLOAT, GL_LINEAR);
        depthTex = createTexture(GL_DEPTH_COMPONENT32F, GL_DEPTH_COMPONENT, GL_FLOAT, GL_NEAREST);

        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, colorTex, 0);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, depthTex, 0);

        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
            throw new RuntimeException("Main FBO incomplete");
        }

        // 2. 初始化用于 Copy 的 FBO
        copyFbo = glGenFramebuffers();
        colorCopyTex = createTexture(GL_RGBA16F, GL_RGBA, GL_FLOAT, GL_LINEAR);
        depthCopyTex = createTexture(GL_DEPTH_COMPONENT32F, GL_DEPTH_COMPONENT, GL_FLOAT, GL_NEAREST);

        glBindFramebuffer(GL_FRAMEBUFFER, copyFbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, colorCopyTex, 0);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, depthCopyTex, 0);

        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
            throw new RuntimeException("Copy FBO incomplete");
        }

        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    public void resize(int newWidth, int newHeight) {
        if (newWidth <= 0 || newHeight <= 0) return;
        if (newWidth == this.width && newHeight == this.height) return;
        this.width = newWidth;
        this.height = newHeight;
        updateTextureSize(colorTex, GL_RGBA16F, GL_RGBA, GL_FLOAT);
        updateTextureSize(depthTex, GL_DEPTH_COMPONENT32F, GL_DEPTH_COMPONENT, GL_FLOAT);
        updateTextureSize(colorCopyTex, GL_RGBA16F, GL_RGBA, GL_FLOAT);
        updateTextureSize(depthCopyTex, GL_DEPTH_COMPONENT32F, GL_DEPTH_COMPONENT, GL_FLOAT);

        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
            throw new RuntimeException("Main FBO incomplete after resize");
        }
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    private void updateTextureSize(int texID, int internalFormat, int format, int type) {
        glBindTexture(GL_TEXTURE_2D, texID);
        glTexImage2D(GL_TEXTURE_2D, 0, internalFormat, width, height, 0, format, type, 0);
        glBindTexture(GL_TEXTURE_2D, 0);
    }

    private int createTexture(int internalFormat, int format, int type, int filter) {
        int tex = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, tex);
        glTexImage2D(GL_TEXTURE_2D, 0, internalFormat, width, height, 0, format, type, 0);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, filter);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, filter);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glBindTexture(GL_TEXTURE_2D, 0);
        return tex;
    }

    /**
     * 绑定当前场景
     */
    public void bind() {
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
    }

    /**
     * 解绑当前场景
     */
    public void unbind() {
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    /**
     * 将当前场景的纹理复制到历史纹理
     */
    public void copyToHistory() {
        glBindFramebuffer(GL_READ_FRAMEBUFFER, fbo);
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, copyFbo);
        glBlitFramebuffer(0, 0, width, height, 0, 0, width, height,
                GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT, GL_NEAREST);
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
    }

    /**
     * 渲染场景到屏幕
     */
    public void renderToScreen() {
        ensureResources();

        glDisable(GL_DEPTH_TEST);

        // 使用 GLShader 封装的绑定方法
        screenShader.bind();

        // 激活并绑定纹理
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, colorTex);
        glUniform1i(glGetUniformLocation(screenShader.getRendererID_Internal(), "u_Texture"), 0);

        glBindVertexArray(quadVao);
        glDrawArrays(GL_TRIANGLES, 0, 6);
        glBindVertexArray(0);

        screenShader.unbind();
        glEnable(GL_DEPTH_TEST);
    }

    /**
     * 确保资源已创建
     */
    private void ensureResources() {
        if (screenShader == null) screenShader = new ScreenShader();
        if (quadVao == 0) createQuad();
    }

    /**
     * 创建 Quad
     */
    private void createQuad() {
        float[] quadVertices = {
                -1f,  1f,  0f, 1f,   -1f, -1f,  0f, 0f,    1f, -1f,  1f, 0f,
                -1f,  1f,  0f, 1f,    1f, -1f,  1f, 0f,    1f,  1f,  1f, 1f
        };
        quadVao = glGenVertexArrays();
        quadVbo = glGenBuffers();
        glBindVertexArray(quadVao);
        glBindBuffer(GL_ARRAY_BUFFER, quadVbo);
        glBufferData(GL_ARRAY_BUFFER, quadVertices, GL_STATIC_DRAW);

        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 4 * Float.BYTES, 0L);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 4 * Float.BYTES, 2L * Float.BYTES);
        glBindVertexArray(0);
    }

    /**
     * 销毁资源
     */
    public void dispose() {
        glDeleteFramebuffers(fbo);
        glDeleteFramebuffers(copyFbo);
        glDeleteTextures(colorTex);
        glDeleteTextures(depthTex);
        glDeleteTextures(colorCopyTex);
        glDeleteTextures(depthCopyTex);

        if (screenShader != null) screenShader.dispose();
        if (quadVao != 0) glDeleteVertexArrays(quadVao);
        if (quadVbo != 0) glDeleteBuffers(quadVbo);
    }

    /**
     * 获取颜色纹理
     * @return 颜色纹理
     */
    public int getColorTex() { return colorTex; }

    /**
     * 获取深度纹理
     * @return 深度纹理
     */
    public int getDepthTex() { return depthTex; }

    /**
     * 获取颜色纹理（复制）
     * @return 颜色纹理
     */
    public int getColorCopyTex() {
        return colorCopyTex;
    }

    /**
     * 获取深度纹理 （复制）
     * @return 深度纹理
     */
    public int getDepthCopyTex() {
        return depthCopyTex;
    }

    /**
     * 获取屏幕渲染器
     * @return 屏幕渲染器
     */
    public ScreenShader getScreenShader() {
        return screenShader;
    }

    /**
     * 设置屏幕渲染器
     * @param screenShader 屏幕渲染器
     */
    public void setScreenShader(ScreenShader screenShader) {
        this.screenShader = screenShader;
    }
}