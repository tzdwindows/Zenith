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

struct WaterMaterial {
    vec3 deepColor;
    vec3 shallowColor;
    float roughness;
    float clarity;
    float rainIntensity;
    vec3 foamColor;
    float ambientWeight;
    float dayFactor; // ⭐ 新增：用于判断日夜，压暗水面
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

// ⭐ 修复天空倒影：白天亮蓝，晚上极暗的午夜蓝
vec3 sampleSky(vec3 d, float dayFactor) {
    float t = clamp(d.y * 0.5 + 0.5, 0.0, 1.0);
    vec3 skyDay = mix(vec3(0.04, 0.08, 0.16), vec3(0.45, 0.65, 0.92), t);
    vec3 skyNight = mix(vec3(0.005, 0.01, 0.02), vec3(0.01, 0.02, 0.05), t); // 黑夜颜色

    // 根据日夜因子混合天空颜色
    vec3 skyBase = mix(skyNight, skyDay, dayFactor);

    // 只有白天才会反射耀眼的太阳高光
    float sunBoost = pow(max(dot(normalize(d), vec3(0.0, 1.0, 0.0)), 0.0), 6.0);
    skyBase += vec3(1.0, 0.85, 0.65) * sunBoost * 0.35 * dayFactor;

    return skyBase;
}

vec3 shadeWaterPBR(
vec3 worldPos, vec3 V, vec3 L, vec3 N,
vec3 lightIntensity, WaterMaterial mat, float time, vec2 fragUV
) {
    float NoV = clamp(dot(N, V), 0.001, 1.0);

    float depthMapZ = texture(u_SceneDepth, fragUV).r;
    float waterDepth = 20.0;
    vec3 refraction = mat.deepColor;

    if (depthMapZ > 0.0001 && depthMapZ < 0.9999) {
        vec3 scenePos = reconstructWorld(fragUV, depthMapZ);
        waterDepth = max(worldPos.y - scenePos.y, 0.0);
        if (isnan(waterDepth) || isinf(waterDepth)) waterDepth = 20.0;

        vec3 extinction = vec3(0.45, 0.15, 0.05);
        vec3 transmittance = exp(-waterDepth * extinction * mat.clarity);

        vec2 offset = N.xz * 0.02 * clamp(waterDepth * 0.05, 0.0, 1.0);
        vec3 refractScene = texture(u_SceneColor, clamp(fragUV + offset, 0.001, 0.999)).rgb;
        refraction = mix(mat.deepColor, refractScene, transmittance);
    }

    if (isnan(refraction.x)) refraction = mat.deepColor;

    vec3 R = reflect(-V, N);
    vec3 skyReflection = sampleSky(R, mat.dayFactor); // 传入日夜因子

    float f0 = 0.02;
    float fresnel = f0 + (1.0 - f0) * pow(1.0 - NoV, 5.0);
    vec3 envColor = mix(refraction, skyReflection, fresnel);

    float foamEdge = smoothstep(0.0, 1.5, 1.5 - waterDepth) * smoothstep(0.6, 1.0, N.y);
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