package com.zenith.render.backend.opengl;

import com.zenith.render.FrameBuffer;
import com.zenith.render.Texture;
import com.zenith.render.backend.opengl.texture.GLTexture;

import static org.lwjgl.opengl.GL30.*;

public class GLFrameBuffer extends FrameBuffer {
    private int fboId;
    private int rboId; // 深度和模板缓冲区
    private GLTexture colorAttachment;

    public GLFrameBuffer(int width, int height) {
        this.width = width;
        this.height = height;

        // 1. 创建 Framebuffer 句柄
        fboId = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, fboId);

        // 2. 创建并配置颜色附着纹理
        colorAttachment = new GLTexture(width, height);
        // 将纹理挂载到 FBO 的颜色槽位 0
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, colorAttachment.getId(), 0);

        // 3. 创建渲染缓冲对象 (RBO) 用于深度和模板测试
        rboId = glGenRenderbuffers();
        glBindRenderbuffer(GL_RENDERBUFFER, rboId);
        glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH24_STENCIL8, width, height);
        // 将 RBO 挂载到 FBO
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT, GL_RENDERBUFFER, rboId);

        // 4. 检查完整性
        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
            throw new RuntimeException("Framebuffer 未能正确初始化！");
        }

        glBindFramebuffer(GL_FRAMEBUFFER, 0); // 解绑，回到主屏幕缓冲
    }

    @Override
    public void bind() {
        glBindFramebuffer(GL_FRAMEBUFFER, fboId);
        glViewport(0, 0, width, height); // 必须匹配 FBO 大小
    }

    @Override
    public void unbind() {
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    @Override
    public Texture getColorAttachment() {
        return colorAttachment;
    }

    @Override
    public void dispose() {
        glDeleteFramebuffers(fboId);
        glDeleteRenderbuffers(rboId);
        colorAttachment.dispose();
    }
}