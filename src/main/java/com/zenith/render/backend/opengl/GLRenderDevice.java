package com.zenith.render.backend.opengl;

import com.zenith.common.math.Color;
import com.zenith.common.utils.InternalLogger;
import com.zenith.render.RenderDevice;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL43;
import org.lwjgl.opengl.GLUtil;
import org.lwjgl.system.Callback;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_SRGB;

/**
 * GLRenderDevice 是 OpenGL 后端的具体实现。
 * 负责硬件状态的最终执行和调试信息监控。
 */
public class GLRenderDevice extends RenderDevice {

    private Callback debugProc;

    public GLRenderDevice() {
        super();
        init();
    }

    private void init() {
        // 1. 开启深度测试（3D 引擎必备）
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LEQUAL);

        // 2. 开启 Alpha 混合（UI 和透明物体必备）
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        // 3. 开启背面剔除（优化性能，不渲染看不见的面）
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);

        // 4. 开启 Gamma 校正（确保颜色在显示器上看起来是正确的）
        glEnable(GL_FRAMEBUFFER_SRGB);

        InternalLogger.info("GLRenderDevice: Common states initialized (Depth, Blend, Cull, sRGB).");

        // 5. 开启 OpenGL 调试输出 (仅在开发模式)
        setupDebugMessages();
    }

    /**
     * 设置 OpenGL 调试回调。
     * 任何着色器错误或不规范的 GL 调用都会直接通过 InternalLogger 打印。
     */
    private void setupDebugMessages() {
        glEnable(GL43.GL_DEBUG_OUTPUT);
        glEnable(GL43.GL_DEBUG_OUTPUT_SYNCHRONOUS);
        debugProc = GLUtil.setupDebugMessageCallback();
        if (debugProc != null) {
            InternalLogger.info("GLRenderDevice: OpenGL Debug Output enabled.");
        }
    }

    @Override
    public void clear(Color color) {
        // 设置清屏颜色并清除缓冲区
        glClearColor(color.r, color.g, color.b, color.a);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);
    }

    @Override
    public void setViewport(int x, int y, int width, int height) {
        glViewport(x, y, width, height);
    }

    @Override
    public void setVSync(boolean enabled) {
        // VSync 在 GLFW 中通过 swapInterval 控制，0 为关，1 为开
        org.lwjgl.glfw.GLFW.glfwSwapInterval(enabled ? 1 : 0);
        InternalLogger.info("GLRenderDevice: VSync set to " + enabled);
    }

    /**
     * 控制线框模式 (Wireframe) 或 填充模式
     */
    public void setWireframe(boolean enabled) {
        glPolygonMode(GL_FRONT_AND_BACK, enabled ? GL_LINE : GL_FILL);
    }

    @Override
    public void dispose() {
        if (debugProc != null) {
            debugProc.free();
        }
        InternalLogger.info("GLRenderDevice: Resources disposed.");
    }
}