#ifndef ZENITH_WATER_SYSTEM_GLSL
#define ZENITH_WATER_SYSTEM_GLSL

#include "common_math.glsl"
#include "brdf.glsl"

struct PBRParams {
    vec3  diffuseColor;
    vec3  f0;
    float roughness;
    float metallic;
};

#include "surface_shading.glsl"
#include "psrdnoise3.glsl"

#define MAX_SPLASHES 4
uniform vec4 u_ActiveSplashes[MAX_SPLASHES];

uniform sampler2D u_SceneColor;
uniform sampler2D u_SceneDepth;
uniform mat4 u_InvViewProjection;
uniform mat4 u_ViewProjection;

struct WaterMaterial {
    vec3 deepColor;
    vec3 shallowColor;
    float roughness;
    float clarity;
    float rainIntensity;
    vec3 foamColor;
    float dayFactor;
};

struct WaveResult {
    float height;
    vec3 tangent;
    vec3 bitangent;
    vec3 normal;
};

float _computeGerstner(vec2 uv, float time, vec4 wave, inout vec3 T, inout vec3 B) {
    float k = 2.0 * PI / wave.w;
    float c = sqrt(9.8 / k);
    vec2 d = normalize(wave.xy);
    float f = k * (dot(d, uv) - c * time);
    float a = wave.z / k;
    T += vec3(-d.x * d.x * (a * k * sin(f)), d.x * (a * k * cos(f)), -d.x * d.y * (a * k * sin(f)));
    B += vec3(-d.x * d.y * (a * k * sin(f)), d.y * (a * k * cos(f)), -d.y * d.y * (a * k * sin(f)));
    return a * sin(f);
}

WaveResult getWaterWave(vec2 worldXZ, float time) {
    vec3 T = vec3(1.0, 0.0, 0.0);
    vec3 B = vec3(0.0, 0.0, 1.0);
    float h = 0.0;
    h += _computeGerstner(worldXZ, time * 1.15, vec4(1.0, 0.25, 0.08, 95.0), T, B);
    h += _computeGerstner(worldXZ, time * 0.92, vec4(-0.7, 0.5, 0.05, 63.0), T, B);
    h += 0.25 * sin(worldXZ.x * 0.018 + time * 1.8);
    h += 0.18 * cos(worldXZ.y * 0.022 - time * 2.1);
    vec3 N = normalize(cross(B, T));
    return WaveResult(h, normalize(T), normalize(B), N);
}

vec3 reconstructWorld(vec2 uv, float depth) {
    vec4 clip = vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 world = u_InvViewProjection * clip;
    return world.xyz / world.w;
}

vec3 sampleSky(vec3 d, float dayFactor) {
    float t = clamp(d.y * 0.5 + 0.5, 0.0, 1.0);
    // 提升基础天空亮度，防止死黑
    vec3 skyDay = mix(vec3(0.1, 0.15, 0.25), vec3(0.45, 0.65, 0.92), t);
    vec3 skyNight = mix(vec3(0.01, 0.015, 0.02), vec3(0.02, 0.03, 0.05), t);
    vec3 skyBase = mix(skyNight, skyDay, dayFactor);
    float sunBoost = pow(max(dot(normalize(d), vec3(0.0, 1.0, 0.0)), 0.0), 8.0);
    skyBase += vec3(1.0, 0.9, 0.7) * sunBoost * 0.5 * dayFactor;
    return skyBase;
}

vec3 computeSSR(vec3 worldPos, vec3 R, vec3 fallbackSky) {
    // 稍微缩小步长，增加精确度
    vec3 stepDir = R * 0.8;
    vec3 currentPos = worldPos + stepDir;
    vec3 hitColor = fallbackSky;

    for (int i = 0; i < 30; i++) { // 增加循环次数提高质量
        vec4 clipPos = u_ViewProjection * vec4(currentPos, 1.0);
        if (clipPos.w <= 0.0) break;

        vec3 ndcPos = clipPos.xyz / clipPos.w;
        vec2 screenUV = ndcPos.xy * 0.5 + 0.5;

        // 边界检查
        if (screenUV.x < 0.0 || screenUV.x > 1.0 || screenUV.y < 0.0 || screenUV.y > 1.0) break;

        float sceneDepthZ = texture(u_SceneDepth, screenUV).r;
        float ndcSceneZ = sceneDepthZ * 2.0 - 1.0;

        // 核心修复：
        // 1. zDiff 检查是否碰撞
        // 2. 增加判断：只有当采样的场景点在“水面以上”时才反射
        float zDiff = ndcPos.z - ndcSceneZ;
        if (zDiff > 0.0001 && zDiff < 0.015) {
            // 获取碰撞点的世界位置，确保它不在水底
            vec3 hitWorldPos = reconstructWorld(screenUV, sceneDepthZ);
            if (hitWorldPos.y < worldPos.y - 0.2) {
                // 如果撞到了水下的脚或尾巴，跳过反射，防止产生黑影
                currentPos += stepDir;
                continue;
            }

            hitColor = texture(u_SceneColor, screenUV).rgb;
            // 边缘淡出，防止生硬的切边
            float fade = smoothstep(0.0, 0.2, screenUV.x) * smoothstep(1.0, 0.8, screenUV.x) *
            smoothstep(0.0, 0.2, screenUV.y) * smoothstep(1.0, 0.8, screenUV.y);
            hitColor = mix(fallbackSky, hitColor, fade);
            break;
        }

        currentPos += stepDir;
        // 指数步进优化，远处的反射更模糊，近处更精确
        stepDir *= 1.05;
    }
    return hitColor;
}

// 核心：计算环境项（折射 + 反射 + 菲涅尔）
vec3 calculateWaterEnvironment(vec3 worldPos, vec3 V, vec3 N, WaterMaterial mat, vec2 fragUV) {
    float NoV = clamp(dot(N, V), 0.001, 1.0);
    float depthMapZ = texture(u_SceneDepth, fragUV).r;

    // 1. 折射 (Beer-Lambert)
    vec3 refraction = mat.deepColor;
    float rayLength = 20.0;
    if (depthMapZ > 0.0 && depthMapZ < 1.0) {
        vec3 scenePos = reconstructWorld(fragUV, depthMapZ);
        rayLength = distance(worldPos, scenePos);
        // 修正吸收：防止红色被完全吞噬导致变黑
        vec3 absorption = vec3(0.3, 0.1, 0.05);
        vec3 transmittance = exp(-rayLength * absorption * mat.clarity);

        vec2 offset = N.xz * 0.02 * clamp(rayLength * 0.1, 0.0, 1.0);
        vec3 refractScene = texture(u_SceneColor, clamp(fragUV + offset, 0.01, 0.99)).rgb;
        vec3 waterVolColor = mix(mat.shallowColor, mat.deepColor, smoothstep(0.0, 10.0, rayLength));
        refraction = mix(waterVolColor, refractScene, transmittance);
    }

    // 2. 反射 (SSR + Sky)
    vec3 R = reflect(-V, N);
    vec3 skyColor = sampleSky(R, mat.dayFactor);
    vec3 reflection = computeSSR(worldPos, R, skyColor);

    // 3. 菲涅尔
    float f0 = 0.02;
    float fresnel = f0 + (1.0 - f0) * pow(1.0 - NoV, 5.0);

    // 4. 泡沫
    float foamEdge = smoothstep(0.0, 1.5, 1.5 - rayLength) * N.y;
    vec3 foam = mat.foamColor * foamEdge * 0.6;

    return mix(refraction, reflection, fresnel) + foam;
}

// 计算单盏灯的高光贡献
vec3 calculateWaterDirect(vec3 V, vec3 L, vec3 N, vec3 radiance, WaterMaterial mat) {
    PBRParams pixel;
    pixel.roughness = mat.roughness;
    pixel.f0 = vec3(0.02);
    pixel.diffuseColor = vec3(0.0); // 水面主要是高光，散射已在环境项处理

    return surfaceShading(pixel, L, radiance, V, N) * 2.0;
}

#endif