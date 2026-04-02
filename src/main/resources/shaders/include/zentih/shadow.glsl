#ifndef SHADOW_GLSL
#define SHADOW_GLSL

#include "zentih/sdf_primitives.glsl"
#include "zentih/transform.glsl"

/**
 * 场景距离场接口
 * 在你的渲染主逻辑中必须定义此函数
 */
float map(vec3 p);

/**
 * 软阴影 (Penumbra Shadows)
 * 基于 SDF 的光线步进算法
 * @param ro: 光线起点 (通常是着色点)
 * @param rd: 光线方向 (指向光源)
 * @param mint: 最小步进距离 (防止自遮挡/阴影粉刺)
 * @param maxt: 最大阴影探测距离
 * @param k: 软化因子 (值越小阴影边缘越柔和)
 */
float calculateSoftShadow(vec3 ro, vec3 rd, float mint, float maxt, float k) {
    float res = 1.0;
    float t = mint;

    // 限制循环次数以保证性能
    for(int i = 0; i < 32; i++) {
        float h = map(ro + rd * t);

        // 核心公式：根据到物体的距离/行进距离的比值来确定遮挡程度
        res = min(res, k * h / t);

        // 步进距离
        t += clamp(h, 0.01, 0.5);

        // 如果已经基本完全遮挡或超出距离，跳出
        if(res < 0.001 || t > maxt) break;
    }

    return clamp(res, 0.0, 1.0);
}

/**
 * 环境光遮蔽 (Ambient Occlusion)
 * 模拟物体转角处的阴影细节
 */
float calculateAO(vec3 p, vec3 n) {
    float occ = 0.0;
    float sca = 1.0;
    for(int i = 0; i < 5; i++) {
        float h = 0.01 + 0.12 * float(i) / 4.0;
        float d = map(p + n * h);
        occ += (h - d) * sca;
        sca *= 0.95;
    }
    return clamp(1.0 - 3.0 * occ, 0.0, 1.0);
}

#endif