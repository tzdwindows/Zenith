package com.zenith.render.backend.opengl;

import com.zenith.common.config.RayTracingConfig;
import com.zenith.common.config.RenderConfig;
import com.zenith.render.backend.opengl.shader.ScreenShader;
import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL42.glTexStorage2D; // 用于更稳定的不可变存储

public class SceneFramebuffer {
    private int fbo;
    private int colorTex;
    private int depthTex;

    // 光追结果存储目标（如果是混合渲染，可以单独存放在这里）
    private int rayTraceTex;

    private int copyFbo;
    private int colorCopyTex;
    private int depthCopyTex;

    private int width, height;

    private int quadVao = 0;
    private int quadVbo = 0;
    private ScreenShader screenShader;

    public SceneFramebuffer(int width, int height) {
        this.width = width;
        this.height = height;
        init();
    }

    private void init() {
        int internalFormat = RenderConfig.FBO_INTERNAL_FORMAT;

        // 1. 初始化主场景 FBO
        fbo = glGenFramebuffers();
        colorTex = createTexture(internalFormat, GL_RGBA, GL_FLOAT, GL_LINEAR);
        depthTex = createTexture(GL_DEPTH_COMPONENT32F, GL_DEPTH_COMPONENT, GL_FLOAT, GL_NEAREST);

        // 创建专门用于光追写入的纹理
        rayTraceTex = createTexture(GL_RGBA16F, GL_RGBA, GL_HALF_FLOAT, GL_LINEAR);

        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, colorTex, 0);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, depthTex, 0);

        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
            throw new RuntimeException("Zenith Engine: Main FBO incomplete");
        }

        // 2. 初始化用于历史记录（Copy）的 FBO
        copyFbo = glGenFramebuffers();
        colorCopyTex = createTexture(internalFormat, GL_RGBA, GL_FLOAT, GL_LINEAR);
        depthCopyTex = createTexture(GL_DEPTH_COMPONENT32F, GL_DEPTH_COMPONENT, GL_FLOAT, GL_NEAREST);

        glBindFramebuffer(GL_FRAMEBUFFER, copyFbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, colorCopyTex, 0);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, depthCopyTex, 0);

        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
            throw new RuntimeException("Zenith Engine: Copy FBO incomplete");
        }

        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    public void resize(int newWidth, int newHeight) {
        if (newWidth <= 0 || newHeight <= 0) return;
        if (newWidth == this.width && newHeight == this.height) return;
        this.width = newWidth;
        this.height = newHeight;

        int internalFormat = RenderConfig.FBO_INTERNAL_FORMAT;
        updateTextureSize(colorTex, internalFormat, GL_RGBA, GL_FLOAT);
        updateTextureSize(depthTex, GL_DEPTH_COMPONENT32F, GL_DEPTH_COMPONENT, GL_FLOAT);
        updateTextureSize(rayTraceTex, internalFormat, GL_RGBA, GL_FLOAT);
        updateTextureSize(colorCopyTex, internalFormat, GL_RGBA, GL_FLOAT);
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
        // 分配显存
        glTexImage2D(GL_TEXTURE_2D, 0, internalFormat, width, height, 0, format, type, (ByteBuffer) null);

        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, filter);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, filter);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glBindTexture(GL_TEXTURE_2D, 0);
        return tex;
    }

    public void bind() {
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
    }

    public void unbind() {
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    public void copyToHistory() {
        glBindFramebuffer(GL_READ_FRAMEBUFFER, fbo);
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, copyFbo);
        glBlitFramebuffer(0, 0, width, height, 0, 0, width, height,
                GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT, GL_NEAREST);
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
    }

    public void blitRTtoColor() {
        if (copyFbo == 0) return;

        glBindFramebuffer(GL_READ_FRAMEBUFFER, copyFbo);
        glFramebufferTexture2D(GL_READ_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, rayTraceTex, 0);

        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, fbo);
        glFramebufferTexture2D(GL_DRAW_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, colorTex, 0);

        glBlitFramebuffer(0, 0, width, height, 0, 0, width, height, GL_COLOR_BUFFER_BIT, GL_NEAREST);

        glBindFramebuffer(GL_READ_FRAMEBUFFER, copyFbo);
        glFramebufferTexture2D(GL_READ_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, colorCopyTex, 0);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }


    /**
     * 将 FBO 内容最终呈现到屏幕
     * 自动处理光栅化与光追纹理的切换
     */
    public void renderToScreen() {
        glDisable(GL_DEPTH_TEST);
        ensureResources();

        screenShader.bind();

        // 永远把叠加好透明物体的主画布绑在 0 号位
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, colorTex);
        screenShader.setUniform("u_Texture", 0);

        glBindVertexArray(quadVao);
        glDrawArrays(GL_TRIANGLES, 0, 6);
        glBindVertexArray(0);

        screenShader.unbind();
        glEnable(GL_DEPTH_TEST);
    }


    public void ensureResources() {
        if (screenShader == null) screenShader = new ScreenShader();
        if (quadVao == 0) createQuad();
    }

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

    public void dispose() {
        glDeleteFramebuffers(fbo);
        glDeleteFramebuffers(copyFbo);
        glDeleteTextures(colorTex);
        glDeleteTextures(depthTex);
        glDeleteTextures(rayTraceTex);
        glDeleteTextures(colorCopyTex);
        glDeleteTextures(depthCopyTex);

        if (screenShader != null) screenShader.dispose();
        if (quadVao != 0) glDeleteVertexArrays(quadVao);
        if (quadVbo != 0) glDeleteBuffers(quadVbo);
    }

    // --- Getters ---
    public int getRayTraceTargetID() { return rayTraceTex; }
    public int getColorTex() { return colorTex; }
    public int getDepthTex() { return depthTex; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public ScreenShader getScreenShader() { return screenShader; }

    /**
     * 获取硬件生成的深度纹理 ID，用于混合光追中的深度测试或射线步进
     */
    public int getDepthTextureID() {
        return depthTex;
    }

    /**
     * 获取当前主场景的颜色纹理 ID（即 G-Buffer 的 Diffuse 部分）
     */
    public int getSceneTextureID() {
        return colorTex;
    }

    /**
     * 获取上一帧的历史纹理（用于时间性累积渲染，TSR/TAA）
     */
    public int getHistoryTextureID() {
        return colorCopyTex;
    }

    public int getColorCopyTex() {
        return colorCopyTex;
    }

    public int getDepthCopyTex() {
        return depthCopyTex;
    }
}