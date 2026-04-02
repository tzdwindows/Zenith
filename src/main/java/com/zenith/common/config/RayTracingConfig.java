package com.zenith.common.config;

public class RayTracingConfig {
    public static boolean ENABLE_RAY_TRACING = false;

    // 光追模式：0 - 仅光追, 1 - 混合渲染 (光栅化 + RT 反射/阴影)
    public static int RT_MODE = 1;

    // 是否每帧更新加速结构 (针对动态物体)
    public static boolean DYNAMIC_AS_UPDATE = true;

    // 光追采样次数
    public static int RAY_SAMPLES = 1;
}