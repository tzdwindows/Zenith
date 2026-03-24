package com.zenith.render;

import com.zenith.common.utils.InternalLogger;
import com.zenith.common.math.Color;

/**
 * RenderDevice 是渲染后端的抽象基类。
 * 负责清屏、视口管理以及缓冲区交换。
 */
public abstract class RenderDevice {

    protected RenderDevice() {
        InternalLogger.info("Initializing RenderDevice...");
    }

    /** 清除颜色缓冲和深度缓冲 */
    public abstract void clear(Color color);

    /** 设置渲染视口区域 */
    public abstract void setViewport(int x, int y, int width, int height);

    /** 开启/关闭 垂直同步 */
    public abstract void setVSync(boolean enabled);

    /** 销毁渲染上下文，释放硬件资源 */
    public abstract void dispose();
}