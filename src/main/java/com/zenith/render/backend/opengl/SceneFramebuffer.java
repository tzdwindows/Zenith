package com.zenith.render.backend.opengl;

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

    // ====== 修复点 1: 增加用于备份的 FBO 和纹理 ID ======
    private int copyFbo;
    private int colorCopyTex;
    private int depthCopyTex;

    private int width, height;

    // 用于渲染到屏幕的资源
    private int quadVao = 0;
    private int quadVbo = 0;
    private int screenProgram = 0;

    public SceneFramebuffer(int width, int height) {
        this.width = width;
        this.height = height;
        init();
    }

    private void init() {
        // 1. 初始化主场景 FBO
        fbo = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);

        colorTex = createTexture(GL_RGBA16F, GL_RGBA, GL_FLOAT, GL_LINEAR);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, colorTex, 0);

        depthTex = createTexture(GL_DEPTH_COMPONENT32F, GL_DEPTH_COMPONENT, GL_FLOAT, GL_NEAREST);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, depthTex, 0);

        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
            throw new RuntimeException("Main FBO incomplete");
        }

        // 2. 修复点 2: 初始化用于 Copy 的 FBO 和纹理 (只分配一次内存)
        copyFbo = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, copyFbo);

        colorCopyTex = createTexture(GL_RGBA16F, GL_RGBA, GL_FLOAT, GL_LINEAR);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, colorCopyTex, 0);

        depthCopyTex = createTexture(GL_DEPTH_COMPONENT32F, GL_DEPTH_COMPONENT, GL_FLOAT, GL_NEAREST);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, depthCopyTex, 0);

        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
            throw new RuntimeException("Copy FBO incomplete");
        }

        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    private int createTexture(int internalFormat, int format, int type, int filter) {
        int tex = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, tex);
        glTexImage2D(GL_TEXTURE_2D, 0, internalFormat, width, height, 0, format, type, 0);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, filter);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, filter);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        return tex;
    }

    public void bind() {
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
    }

    public void unbind() {
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    // 修复点 3: 高效拷贝函数，不再创建销毁 FBO，也不再重新分配纹理内存
    public void copyToHistory() {
        // 将主 FBO 的内容“闪传”到 Copy FBO
        glBindFramebuffer(GL_READ_FRAMEBUFFER, fbo);
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, copyFbo);

        // 同时拷贝颜色和深度
        glBlitFramebuffer(0, 0, width, height,
                0, 0, width, height,
                GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT,
                GL_NEAREST);

        // 恢复绑定到主 FBO，准备后续渲染（如水面）
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
    }

    public int getColorTex() { return colorTex; }
    public int getDepthTex() { return depthTex; }

    // 给水面 Shader 使用的纹理接口
    public int getColorCopyTex() { return colorCopyTex; }
    public int getDepthCopyTex() { return depthCopyTex; }

    /**
     * 将 FBO 内容最终绘制到屏幕主缓冲
     */
    public void renderToScreen() {
        ensureScreenResources();
        glDisable(GL_DEPTH_TEST);
        glUseProgram(screenProgram);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, colorTex);
        glUniform1i(glGetUniformLocation(screenProgram, "u_Texture"), 0);

        glBindVertexArray(quadVao);
        glDrawArrays(GL_TRIANGLES, 0, 6);
        glBindVertexArray(0);
        glEnable(GL_DEPTH_TEST);
    }

    private void ensureScreenResources() {
        if (screenProgram == 0) screenProgram = createScreenProgram();
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
    }

    private int createScreenProgram() {
        String vs = "#version 330 core\nlayout(location=0)in vec2 aPos;layout(location=1)in vec2 aUV;out vec2 vUV;void main(){vUV=aUV;gl_Position=vec4(aPos,0.0,1.0);}";
        String fs = "#version 330 core\nin vec2 vUV;out vec4 c;uniform sampler2D u_Texture;void main(){c=texture(u_Texture,vUV);}";
        int p = glCreateProgram();
        int v = glCreateShader(GL_VERTEX_SHADER); glShaderSource(v, vs); glCompileShader(v);
        int f = glCreateShader(GL_FRAGMENT_SHADER); glShaderSource(f, fs); glCompileShader(f);
        glAttachShader(p, v); glAttachShader(p, f); glLinkProgram(p);
        return p;
    }

    public void dispose() {
        glDeleteFramebuffers(fbo);
        glDeleteFramebuffers(copyFbo);
        glDeleteTextures(colorTex);
        glDeleteTextures(depthTex);
        glDeleteTextures(colorCopyTex);
        glDeleteTextures(depthCopyTex);
        if (screenProgram != 0) glDeleteProgram(screenProgram);
    }
}