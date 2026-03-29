#ifndef ZENITH_WATER_SYSTEM_GLSL
#define ZENITH_WATER_SYSTEM_GLSL

#include "brdf.glsl"
#include "common_math.glsl"
#include "psrdnoise3.glsl"

#define MAX_SPLASHES 4
uniform vec4 u_ActiveSplashes[MAX_SPLASHES];

uniform sampler2D u_SceneColor;
uniform sampler2D u_SceneDepth;
uniform mat4 u_Projection;
uniform mat4 u_View;
uniform vec2 u_ScreenSize;

struct WaterMaterial {
    vec3 deepColor;
    vec3 shallowColor;
    float roughness;
    float clarity;
    float rainIntensity;
    vec3 foamColor;
};

struct WaveResult {
    float height;
    vec3 tangent;
    vec3 bitangent;
    vec3 normal;
};

// 宏观物理波浪
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
    h += _computeGerstner(worldXZ, time * 0.8, vec4(1.0, 0.2, 0.04, 80.0), T, B);
    h += _computeGerstner(worldXZ, time * 0.9, vec4(-0.7, 0.5, 0.02, 50.0), T, B);
    return WaveResult(h, normalize(T), normalize(B), normalize(cross(B, T)));
}

vec3 reconstructWorld(vec2 uv, float depth) {
    float z = depth * 2.0 - 1.0;
    vec4 clip = vec4(uv * 2.0 - 1.0, z, 1.0);
    vec4 view = inverse(u_Projection) * clip;
    view /= view.w;
    vec4 world = inverse(u_View) * view;
    return world.xyz;
}

// 修正：天空采样需要根据当前光照强度进行缩放
vec3 sampleSky(vec3 d, vec3 lightIntensity) {
    float t = clamp(d.y * 0.5 + 0.5, 0.0, 1.0);
    vec3 skyBase = mix(vec3(0.05, 0.1, 0.2), vec3(0.4, 0.6, 0.9), t);
    // 乘上光照强度（并加一个微弱的底色防止全黑），使晚上反射变暗
    return skyBase * (lightIntensity + 0.05);
}

// 原神风格 (Stylized PBR) 水面着色
vec3 shadeWaterPBR(
vec3 worldPos, vec3 V, vec3 L, vec3 N,
vec3 lightIntensity, WaterMaterial mat, float time, vec2 fragUV
) {
    float NoV = clamp(dot(N, V), 0.001, 1.0);

    // 风格化 Fresnel
    float F0 = 0.02;
    float F = F0 + (1.0 - F0) * pow(1.0 - NoV, 4.0);

    float depthMapZ = texture(u_SceneDepth, fragUV).r;
    vec3 scenePos = reconstructWorld(fragUV, depthMapZ);
    float waterDepth = worldPos.y - scenePos.y;

    if (waterDepth < 0.0 || waterDepth > 50.0) {
        waterDepth = 3.0;
    }

    // 1. 反射：应用受光照调制的采样
    vec3 R = reflect(-V, N);
    vec3 reflection = sampleSky(R, lightIntensity);

    // 2. 折射与基础色
    float depthFactor = smoothstep(0.0, 5.0, waterDepth * mat.clarity);
    vec3 waterBaseColor = mix(mat.shallowColor, mat.deepColor, depthFactor);

    vec2 offset = N.xz * 0.03 * clamp(waterDepth, 0.0, 1.0);
    vec3 refractColor = texture(u_SceneColor, clamp(fragUV + offset, 0.001, 0.999)).rgb;

    // 无场景 FBO 时的降级
    if (length(refractColor) < 0.05) {
        refractColor = mix(mat.shallowColor, mat.deepColor, pow(NoV, 0.6));
    }

    // 修正：折射和水体本身颜色也必须乘上光照强度
    // 这里乘上 (lightIntensity + 0.1) 是为了模拟微弱的环境光，不至于晚上完全像个黑洞
    vec3 refraction = mix(refractColor, waterBaseColor, depthFactor * 0.85);
    refraction *= (lightIntensity + 0.02);

    // 3. 高光
    vec3 H = normalize(V + L);
    float NoL = clamp(dot(N, L), 0.0, 1.0);
    float NoH = clamp(dot(N, H), 0.0, 1.0);

    float D = D_GGX(0.02 + mat.roughness * 0.1, NoH);
    float Vv = V_SmithGGXCorrelated(0.02 + mat.roughness * 0.1, NoV, NoL);
    vec3 spec = D * Vv * F * lightIntensity * NoL * PI;

    // 最终合成
    // reflection 已乘过 lightIntensity, refraction 已乘过, spec 已包含 lightIntensity
    return reflection * F + refraction * (1.0 - F) + spec;
}
#endif