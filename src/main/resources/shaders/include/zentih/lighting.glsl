#ifndef ZENITH_LIGHTING_GLSL
#define ZENITH_LIGHTING_GLSL

struct Light {
    int type;              // 0 = directional, 1 = point, 2 = spot
    vec3 position;
    vec3 direction;        // 对于 directional/spot：表示“光照射出去的方向”
    vec4 color;            // 线性空间颜色
    float intensity;       // 建议：directional 视作照度强度；point/spot 视作发光强度
    float range;           // 有效范围（用于平滑截断）
    float innerCutOff;     // 注意：这里应传 cos(innerAngle)
    float outerCutOff;     // 注意：这里应传 cos(outerAngle)
    float ambientStrength; // 兼容旧结构，物理路径中不再使用
};

uniform Light u_Lights[16];
uniform int u_LightCount;

const float ZENITH_MIN_LIGHT_DISTANCE = 0.05;

// ----------------------
// 平滑范围窗口
// ----------------------
float calculateRangeWindow(float dist, float range) {
    float safeRange = max(range, 0.0001);
    float x = dist / safeRange;
    float window = clamp(1.0 - pow(x, 4.0), 0.0, 1.0);
    return window * window;
}

// ----------------------
// 物理更合理的距离衰减：1 / r^2
// 再乘一个平滑窗口，避免硬截断
// ----------------------
float calculateAttenuation(float dist, float range) {
    float d = max(dist, ZENITH_MIN_LIGHT_DISTANCE);
    float invSq = 1.0 / (d * d);
    return invSq * calculateRangeWindow(d, range);
}

// ----------------------
// 聚光灯角度衰减
// 要求 innerCutOff / outerCutOff 为 cos(angle)
// ----------------------
float calculateSpotAttenuation(Light light, vec3 L) {
    vec3 lightForward = normalize(light.direction);
    vec3 lightToSurface = -L; // L = surface -> light，所以反过来才是 light -> surface

    float cosTheta = dot(lightForward, lightToSurface);
    float cone = smoothstep(light.outerCutOff, light.innerCutOff, cosTheta);

    // 再平方，让边缘更自然一点
    return cone * cone;
}

// ----------------------
// 主光照入口（统一调用）
// 说明：
// - 这里不再人为加 ambient
// - 环境光/间接光应来自 IBL、天空盒、GI 或 AO 管线
// ----------------------
vec3 evaluateLights(PBRParams pixel, vec3 N, vec3 V, vec3 worldPos) {
    vec3 Lo = vec3(0.0);

    int lightCount = min(u_LightCount, 16);

    for (int i = 0; i < lightCount; i++) {
        Light light = u_Lights[i];

        vec3 L = vec3(0.0);
        float attenuation = 1.0;

        // ---- Directional ----
        if (light.type == 0) {
            L = normalize(-light.direction);
        }
        // ---- Point / Spot ----
        else {
            vec3 lightVec = light.position - worldPos;
            float distSq = max(dot(lightVec, lightVec), 1e-8);
            float dist = sqrt(distSq);

            L = lightVec / max(dist, 1e-4);
            attenuation = calculateAttenuation(dist, light.range);

            // Spot
            if (light.type == 2) {
                attenuation *= calculateSpotAttenuation(light, L);
            }
        }

        if (light.type != 0 && attenuation <= 0.0) {
            continue;
        }

        vec3 radiance = light.color.rgb * light.intensity * attenuation;
        Lo += surfaceShading(pixel, L, radiance, V, N);
    }

    return Lo;
}

#endif