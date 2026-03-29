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

// --- Gerstner 波计算保持不变 ---
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
    float z = depth * 2.0 - 1.0;
    vec4 clip = vec4(uv * 2.0 - 1.0, z, 1.0);
    vec4 view = inverse(u_Projection) * clip;
    view /= view.w;
    vec4 world = inverse(u_View) * view;
    return world.xyz;
}

vec3 sampleSky(vec3 d, vec3 lightIntensity) {
    float t = clamp(d.y * 0.5 + 0.5, 0.0, 1.0);
    vec3 skyBase = mix(vec3(0.04, 0.08, 0.16), vec3(0.45, 0.65, 0.92), t);
    float sunBoost = pow(max(dot(normalize(d), vec3(0.0, 1.0, 0.0)), 0.0), 6.0);
    skyBase += vec3(1.0, 0.85, 0.65) * sunBoost * 0.35;
    return skyBase * (lightIntensity + vec3(0.08));
}

// --- 修改后的核心着色函数 ---
vec3 shadeWaterPBR(
vec3 worldPos, vec3 V, vec3 L, vec3 N,
vec3 lightIntensity, WaterMaterial mat, float time, vec2 fragUV
) {
    // 1. 基础角度计算
    vec3 H = normalize(V + L);
    float NoV = clamp(dot(N, V), 0.001, 1.0);
    float NoL = clamp(dot(N, L), 0.0, 1.0);
    float LoH = clamp(dot(L, H), 0.0, 1.0);

    // 2. 深度与折射逻辑 (保持原样，这是水体特有的)
    float depthMapZ = texture(u_SceneDepth, fragUV).r;
    vec3 scenePos = reconstructWorld(fragUV, depthMapZ);
    float waterDepth = worldPos.y - scenePos.y;
    if (waterDepth < 0.0 || waterDepth > 80.0) waterDepth = 4.0;

    float depthFactor = smoothstep(0.0, 10.0, waterDepth * mat.clarity);
    vec3 waterBaseColor = mix(mat.shallowColor, mat.deepColor, depthFactor);

    vec2 offset = N.xz * 0.025 * clamp(waterDepth, 0.0, 1.5);
    vec3 refractColor = texture(u_SceneColor, clamp(fragUV + offset, 0.001, 0.999)).rgb;
    if (length(refractColor) < 0.03) {
        refractColor = mix(mat.shallowColor, mat.deepColor, pow(NoV, 0.6));
    }
    vec3 refraction = mix(refractColor, waterBaseColor, depthFactor * 0.75);
    refraction *= (lightIntensity + vec3(0.04));

    // 3. 映射到 Filament PBR 参数
    PBRParams pixel;
    pixel.roughness = max(0.015, mat.roughness);
    pixel.f0 = vec3(0.022); // 水的默认 F0 (IOR 1.33)
    // 关键点：对于水，我们将折射出的颜色视为“漫反射颜色”进行合成
    pixel.diffuseColor = refraction;

    // 4. 调用 surface_shading.glsl 中的逻辑计算直接光照
    // 这会自动处理 D_GGX, V_Smith, F_Schlick 以及 Fd_Burley
    vec3 directLighting = surfaceShading(pixel, L, lightIntensity, V, N);

    // 5. 反射处理 (环境/间接光)
    vec3 R = reflect(-V, N);
    vec3 skyReflection = sampleSky(R, lightIntensity);

    // 计算菲涅尔项用于环境反射混合 (复用 Filament 的 F_Schlick)
    vec3 F = F_Schlick(pixel.f0, NoV);

    // 伪 SSR 逻辑
    vec4 reflClip = u_Projection * u_View * vec4(worldPos + R * 60.0, 1.0);
    vec3 sceneReflection = skyReflection;
    if (reflClip.w > 0.001) {
        vec2 reflUV = reflClip.xy / reflClip.w * 0.5 + 0.5;
        if (reflUV.x >= 0.0 && reflUV.x <= 1.0 && reflUV.y >= 0.0 && reflUV.y <= 1.0) {
            vec3 reflScene = texture(u_SceneColor, reflUV).rgb;
            sceneReflection = mix(skyReflection, reflScene, 0.65);
        }
    }

    // 6. 最终合成
    // 我们手动处理环境反射混合，而直接光（太阳高光）已包含在 directLighting 中
    vec3 color = mix(refraction, sceneReflection, F.r * 0.92);

    // 我们只需要 directLighting 中的 Specular 部分，或者整体叠加
    // 为了保持原汁原味，直接叠加 directLighting 的结果
    color += directLighting * 1.25;

    // 7. 泡沫与吸收
    float foamEdge = smoothstep(0.0, 1.5, 1.5 - waterDepth);
    vec3 foam = mat.foamColor * foamEdge * 0.18;
    color += foam;

    color *= mix(vec3(1.0), vec3(0.78, 0.90, 0.98), clamp(depthFactor, 0.0, 1.0));

    return color;
}

#endif