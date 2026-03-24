package com.zenith.render;

import com.zenith.common.math.Color;
import com.zenith.common.math.Vector4f;
import com.zenith.common.utils.InternalLogger;

/**
 * RenderContext 维护渲染状态的当前快照。
 * 通过减少对 GPU 的冗余状态切换来优化性能。
 */
public abstract class RenderContext {

    // 当前状态缓存
    protected Shader currentShader;
    protected Texture[] currentTextures = new Texture[32]; // 假设最多支持 32 个纹理单元
    protected Mesh currentMesh;
    protected boolean depthTestEnabled = false;
    protected boolean blendingEnabled = false;
    protected Vector4f viewport = new Vector4f();

    protected RenderContext() {
        InternalLogger.info("Initializing RenderContext state cache...");
    }

    /* -------------------------------------------------------------------------- */
    /* 核心状态管理 (带有冗余检查)                                                  */
    /* -------------------------------------------------------------------------- */

    /**
     * 绑定 Shader。如果该 Shader 已在工作，则忽略请求。
     */
    public void bindShader(Shader shader) {
        if (currentShader != shader) {
            if (shader != null) {
                shader.bind();
                currentShader = shader;
            } else {
                InternalLogger.warn("RenderContext: Attempted to bind a null shader.");
            }
        }
    }

    /**
     * 在指定插槽绑定纹理。
     */
    public void bindTexture(int slot, Texture texture) {
        if (slot < 0 || slot >= currentTextures.length) {
            InternalLogger.error("RenderContext: Texture slot " + slot + " out of bounds!");
            return;
        }

        if (currentTextures[slot] != texture) {
            if (texture != null) {
                texture.bind(slot);
                currentTextures[slot] = texture;
            } else {
                // 如果传入 null，视作解绑
                currentTextures[slot] = null;
            }
        }
    }

    /**
     * 绑定几何网格。
     */
    public void bindMesh(Mesh mesh) {
        if (currentMesh != mesh) {
            if (mesh != null) {
                // 底层实现会处理 VAO 的绑定
                currentMesh = mesh;
            }
        }
    }

    /* -------------------------------------------------------------------------- */
    /* 渲染开关控制                                                                */
    /* -------------------------------------------------------------------------- */

    public void setDepthTest(boolean enable) {
        if (this.depthTestEnabled != enable) {
            enableDepthTest(enable); // 调用具体的硬件实现
            this.depthTestEnabled = enable;
            InternalLogger.debug("RenderContext: Depth Test " + (enable ? "Enabled" : "Disabled"));
        }
    }

    public void setBlending(boolean enable) {
        if (this.blendingEnabled != enable) {
            enableBlending(enable); // 调用具体的硬件实现
            this.blendingEnabled = enable;
            InternalLogger.debug("RenderContext: Blending " + (enable ? "Enabled" : "Disabled"));
        }
    }

    /* -------------------------------------------------------------------------- */
    /* 硬件相关的抽象方法 (由 GLRenderContext 等实现)                               */
    /* -------------------------------------------------------------------------- */

    protected abstract void enableDepthTest(boolean enable);
    protected abstract void enableBlending(boolean enable);
    public abstract void setViewport(int x, int y, int width, int height);
    public abstract void clear(Color color);

    /**
     * 重置所有状态缓存（通常在 Context 丢失或切换时使用）
     */
    public void reset() {
        currentShader = null;
        currentMesh = null;
        for (int i = 0; i < currentTextures.length; i++) currentTextures[i] = null;
        InternalLogger.info("RenderContext: State cache reset.");
    }
}