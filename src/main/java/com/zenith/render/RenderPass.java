package com.zenith.render;

import com.zenith.common.utils.InternalLogger;

/**
 * RenderPass 定义了一个独立的渲染阶段。
 * 它可以关联特定的 FrameBuffer (帧缓冲)。
 */
public abstract class RenderPass {
    protected String passName;

    protected RenderPass(String name) {
        this.passName = name;
        InternalLogger.info("Created RenderPass: " + name);
    }

    public abstract void execute();
}