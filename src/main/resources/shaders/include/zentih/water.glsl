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
uniform mat4 u_ViewProjection; // 修复：SSR 投影需要使用

struct WaterMaterial {
    vec3 deepColor;
    vec3 shallowColor;
    float roughness;
    float clarity;
    float rainIntensity;
    vec3 foamColor;
    float ambientWeight;
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
    vec3 skyDay = mix(vec3(0.04, 0.08, 0.16), vec3(0.45, 0.65, 0.92), t);
    vec3 skyNight = mix(vec3(0.005, 0.01, 0.02), vec3(0.01, 0.02, 0.05), t);
    vec3 skyBase = mix(skyNight, skyDay, dayFactor);
    float sunBoost = pow(max(dot(normalize(d), vec3(0.0, 1.0, 0.0)), 0.0), 6.0);
    skyBase += vec3(1.0, 0.85, 0.65) * sunBoost * 0.35 * dayFactor;
    return skyBase;
}

// 修复：添加屏幕空间反射 (SSR) 解决倒影不真实的问题
vec3 computeSSR(vec3 worldPos, vec3 R, vec3 fallbackSky) {
    vec3 stepDir = R * 2.0;
    vec3 currentPos = worldPos + stepDir * 0.5;
    vec3 hitColor = fallbackSky;

    for (int i = 0; i < 24; i++) {
        currentPos += stepDir;
        vec4 clipPos = u_ViewProjection * vec4(currentPos, 1.0);
        if (clipPos.w <= 0.0) break;

        vec3 ndcPos = clipPos.xyz / clipPos.w;
        vec2 screenUV = ndcPos.xy * 0.5 + 0.5;

        if (screenUV.x < 0.0 || screenUV.x > 1.0 || screenUV.y < 0.0 || screenUV.y > 1.0) {
            break;
        }

        float sceneDepthZ = texture(u_SceneDepth, screenUV).r;
        float ndcSceneZ = sceneDepthZ * 2.0 - 1.0;
        float zDiff = ndcPos.z - ndcSceneZ;

        // 深度命中判定：允许 0.05 的 NDC 厚度容差
        if (zDiff > 0.0001 && zDiff < 0.05) {
            hitColor = texture(u_SceneColor, screenUV).rgb;
            vec2 fade = smoothstep(0.0, 0.05, screenUV) * smoothstep(1.0, 0.95, screenUV);
            hitColor = mix(fallbackSky, hitColor, fade.x * fade.y);
            break;
        }
        stepDir *= 1.15; // 对数递增步长，平衡性能与反射距离
    }
    return hitColor;
}

vec3 shadeWaterPBR(
vec3 worldPos, vec3 V, vec3 L, vec3 N,
vec3 lightIntensity, WaterMaterial mat, float time, vec2 fragUV
) {
    float NoV = clamp(dot(N, V), 0.001, 1.0);
    float depthMapZ = texture(u_SceneDepth, fragUV).r;
    vec3 refraction = mat.deepColor;
    float rayLength = 20.0;

    if (depthMapZ > 0.0001 && depthMapZ < 0.9999) {
        vec3 scenePos = reconstructWorld(fragUV, depthMapZ);

        // 修复：真实的物理射线距离，而非简单的 Y 轴相减
        rayLength = distance(worldPos, scenePos);

        // 修复：Beer-Lambert 定律吸收模型
        // 水体对红光吸收最强，蓝光最弱，基于此计算消光系数
        vec3 absorption = vec3(0.85, 0.25, 0.05);
        vec3 transmittance = exp(-rayLength * absorption * mat.clarity * 0.1);

        // 焦散与折射扭曲随着深度加深逐渐平滑
        float distortionFactor = clamp(rayLength * 0.05, 0.0, 1.0);
        vec2 offset = N.xz * 0.03 * distortionFactor;
        vec3 refractScene = texture(u_SceneColor, clamp(fragUV + offset, 0.001, 0.999)).rgb;

        // 修复：深水区颜色沉淀，防止直接透视水底
        vec3 waterVolColor = mix(mat.shallowColor, mat.deepColor, clamp(rayLength * 0.08, 0.0, 1.0));
        refraction = mix(waterVolColor, refractScene, transmittance);
    }

    vec3 R = reflect(-V, N);
    vec3 skyReflection = sampleSky(R, mat.dayFactor);

    // 触发 SSR 反射计算
    vec3 reflection = computeSSR(worldPos, R, skyReflection);

    // 修复：强化菲涅尔效应，使掠射角的水面呈现高度镜面感
    float f0 = 0.02;
    float fresnel = f0 + (1.0 - f0) * pow(1.0 - NoV, 5.0);
    fresnel = clamp(fresnel * 1.5, 0.0, 1.0);

    vec3 envColor = mix(refraction, reflection, fresnel);

    // 泡沫逻辑适配新的射线距离
    float foamEdge = smoothstep(0.0, 2.5, 2.5 - rayLength) * smoothstep(0.7, 1.0, N.y);
    vec3 foam = mat.foamColor * foamEdge * 0.35;

    PBRParams pixel;
    pixel.roughness = max(0.02, mat.roughness);
    pixel.f0 = vec3(f0);
    pixel.diffuseColor = vec3(0.0);

    vec3 directLighting = surfaceShading(pixel, L, lightIntensity, V, N);
    if (isnan(directLighting.x) || isinf(directLighting.x)) directLighting = vec3(0.0);

    vec3 finalEnv = (envColor + foam) * mat.ambientWeight;
    return finalEnv + directLighting * 2.0;
}
#endif