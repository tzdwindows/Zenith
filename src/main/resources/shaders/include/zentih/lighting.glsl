/**
*   lighting.glsl
**/
#ifndef ZENITH_LIGHTING_GLSL
#define ZENITH_LIGHTING_GLSL

// ============================================================
//  Zenith Lighting System
//  - API 保持不变
//  - 面向物理的光照输入
//  - 更稳定的 range falloff / spot falloff / energy handling
// ============================================================

struct Light {
    int type;              // 0 = directional, 1 = point, 2 = spot
    vec3 position;
    vec3 direction;        // 对于 directional/spot：表示“光照射出去的方向”
    vec4 color;            // 线性空间颜色
    float intensity;       // directional: 照度强度；point/spot: 发光强度
    float range;           // 有效范围（用于平滑截断）
    float innerCutOff;     // cos(innerAngle)
    float outerCutOff;     // cos(outerAngle)
    float ambientStrength; // 兼容旧结构，物理路径中不再使用
};

uniform Light u_Lights[16];
uniform int u_LightCount;

const float ZENITH_MIN_LIGHT_DISTANCE = 0.05;
const float ZENITH_PI = 3.14159265359;
const float ZENITH_EPS = 1e-6;

// ------------------------------------------------------------
// 工具函数
// ------------------------------------------------------------
float saturate(float x);

vec3 safeNormalize(vec3 v) {
    float lenSq = dot(v, v);
    if (lenSq <= ZENITH_EPS) return vec3(0.0, 0.0, 1.0);
    return v * inversesqrt(lenSq);
}

// ------------------------------------------------------------
// 平滑范围窗口
// 目标：
// 1) 近距离不爆炸
// 2) 到 range 边界时柔和归零
// 3) 兼容 point / spot
// ------------------------------------------------------------
float calculateRangeWindow(float dist, float range) {
    float safeRange = max(range, 0.0001);
    float x = saturate(dist / safeRange);

    // 这类高阶窗口比硬截断更自然，尤其在高曝光/ACES 下
    // 0 距离 = 1，接近 range = 0
    float w = 1.0 - x;
    w = w * w;      // 二次柔化
    w = w * w;      // 四次柔化
    return w;
}

// ------------------------------------------------------------
// 物理更合理的距离衰减：1 / r^2
// 再乘一个平滑窗口，避免硬截断
// 这更接近现代引擎对点光/聚光灯的输入方式
// ------------------------------------------------------------
float calculateAttenuation(float dist, float range) {
    float d = max(dist, ZENITH_MIN_LIGHT_DISTANCE);
    float invSq = 1.0 / max(d * d, ZENITH_EPS);
    return invSq * calculateRangeWindow(d, range);
}

// ------------------------------------------------------------
// 聚光灯角度衰减
// innerCutOff / outerCutOff 都应传 cos(angle)
// 返回值范围：[0,1]
// ------------------------------------------------------------
float calculateSpotAttenuation(Light light, vec3 L) {
    vec3 lightForward = safeNormalize(light.direction);

    // L 是 surface -> light
    // 这里需要 light -> surface，所以取反
    vec3 lightToSurface = -safeNormalize(L);

    float cosTheta = dot(lightForward, lightToSurface);

    // 防止 inner/outer 传反导致 smoothstep 异常
    float innerCutOff = max(light.innerCutOff, light.outerCutOff);
    float outerCutOff = min(light.innerCutOff, light.outerCutOff);

    float cone = smoothstep(outerCutOff, innerCutOff, cosTheta);

    // 轻微压一下边缘，让过渡更“摄影灯”一些
    return cone * cone;
}

// ------------------------------------------------------------
// 单灯辐照度/辐射亮度评估（内部函数）
// 返回 radiance 的“强度乘子”已经处理好
// ------------------------------------------------------------
vec3 evaluateLightRadiance(Light light, vec3 worldPos, out vec3 L, out float attenuation) {
    L = vec3(0.0);
    attenuation = 1.0;

    // Directional Light
    if (light.type == 0) {
        // light.direction 表示“光照射出去的方向”
        // 对表面而言，入射光方向 = -direction
        L = safeNormalize(-light.direction);
        return light.color.rgb * light.intensity;
    }

    // Point / Spot
    vec3 lightVec = light.position - worldPos;
    float distSq = max(dot(lightVec, lightVec), ZENITH_EPS);
    float dist = sqrt(distSq);

    L = lightVec / max(dist, ZENITH_MIN_LIGHT_DISTANCE);

    // 距离衰减
    attenuation = calculateAttenuation(dist, light.range);

    // Spot 额外角度衰减
    if (light.type == 2) {
        attenuation *= calculateSpotAttenuation(light, L);
    }

    // 对于 point/spot，这里输出的是“已考虑衰减后的辐射输入”
    return light.color.rgb * light.intensity * attenuation;
}

// ------------------------------------------------------------
// 主光照入口（统一调用）
// 说明：
// - 不再人为加 ambient
// - 环境光 / 间接光应来自 IBL、天空盒、GI、AO、反射探针等
// - 这里尽量把直射灯做到稳定、物理、宽动态范围友好
// ------------------------------------------------------------
vec3 evaluateLights(PBRParams pixel, vec3 N, vec3 V, vec3 worldPos) {
    vec3 Lo = vec3(0.0);

    vec3 Nn = safeNormalize(N);
    vec3 Vn = safeNormalize(V);

    int lightCount = min(u_LightCount, 16);

    for (int i = 0; i < lightCount; i++) {
        Light light = u_Lights[i];

        // 基础合法性过滤
        if (light.intensity <= 0.0) {
            continue;
        }

        vec3 L = vec3(0.0);
        float attenuation = 1.0;
        vec3 radiance = vec3(0.0);

        // 方向光
        if (light.type == 0) {
            radiance = evaluateLightRadiance(light, worldPos, L, attenuation);
        }
        // 点光 / 聚光灯
        else if (light.type == 1 || light.type == 2) {
            radiance = evaluateLightRadiance(light, worldPos, L, attenuation);

            // 有范围但已经衰减到几乎没贡献，直接跳过
            if (attenuation <= 0.0) {
                continue;
            }

            // 额外防止超远距离数值噪声
            if (dot(radiance, radiance) < ZENITH_EPS) {
                continue;
            }
        }
        else {
            // 未知类型直接忽略
            continue;
        }

        // 这里假定 surfaceShading(pixel, L, radiance, V, N)
        // 内部已经是基于 PBR 的 GGX / Fresnel / Geometry 计算
        // 如果你的 surfaceShading 还不是能量守恒模型，建议下一步一起升级
        Lo += surfaceShading(pixel, L, radiance, Vn, Nn);
    }

    return Lo;
}

#endif