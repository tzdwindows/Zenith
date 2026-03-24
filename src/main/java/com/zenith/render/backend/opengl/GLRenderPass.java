package com.zenith.render.backend.opengl;

import com.zenith.render.FrameBuffer;
import com.zenith.render.RenderPass;
import static org.lwjgl.opengl.GL30.*;

/**
 * OpenGL 实现的 RenderPass，支持自动管理 FrameBuffer。
 */
public class GLRenderPass extends RenderPass {
    private final FrameBuffer targetBuffer;
    private final Runnable renderLogic;
    private float[] clearColor = {0.0f, 0.0f, 0.0f, 1.0f};

    /**
     * @param name 阶段名称
     * @param target 渲染目标（传 null 则渲染到主屏幕）
     * @param logic 该阶段要执行的具体绘制代码（通常是一个 Lambda 表达式）
     */
    public GLRenderPass(String name, FrameBuffer target, Runnable logic) {
        super(name);
        this.targetBuffer = target;
        this.renderLogic = logic;
    }

    public void setClearColor(float r, float g, float b, float a) {
        this.clearColor = new float[]{r, g, b, a};
    }

    @Override
    public void execute() {
        // 1. 准备阶段：绑定目标缓冲区
        if (targetBuffer != null) {
            targetBuffer.bind();
        } else {
            // 如果 target 为 null，默认解绑所有 FBO，回到主窗口缓冲
            glBindFramebuffer(30, 0); // GL_FRAMEBUFFER = 0x8D40 (36160), 这里简化处理
        }

        // 2. 清除缓冲区
        glClearColor(clearColor[0], clearColor[1], clearColor[2], clearColor[3]);
        glClear(16384 | 256); // GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT

        // 3. 执行核心渲染逻辑
        if (renderLogic != null) {
            renderLogic.run();
        }

        // 4. 收尾阶段：如果是渲染到 FBO，通常在结束后解绑
        if (targetBuffer != null) {
            targetBuffer.unbind();
        }
    }
}