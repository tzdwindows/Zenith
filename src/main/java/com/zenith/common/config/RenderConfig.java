package com.zenith.common.config;

import static org.lwjgl.opengl.GL30.GL_RGBA16F;

public class RenderConfig {
    // FBO 内部格式，RGBA16F 提供足够的精度用于 HDR 和光追计算
    public static int FBO_INTERNAL_FORMAT = GL_RGBA16F;

    // 是否开启光追专用的渲染目标
    public static boolean USE_HIGH_PRECISION_RT = true;

    // 是否启用垂直同步
    public static boolean VSYNC = true;
}