#ifndef ZENITH_TERRAIN_SYSTEM_GLSL
#define ZENITH_TERRAIN_SYSTEM_GLSL

#include "../psrdnoise/psrdnoise3.glsl"

const float ZENITH_PI = 3.14159265359;

// ==========================
// PBR 参数
// ==========================
struct PBRParams {
    vec3  diffuseColor;
    vec3  f0;
    float roughness;
    float metallic;
};

// ==========================
// 地形材质
// ==========================
struct TerrainMaterial {
    float hasGrassMap;
    float hasRockMap;
    float hasNormalMap;
    float uvScale;

    vec3 grassColor;
    vec3 rockColor;
    vec3 snowColor;

    float amplitude;
    float frequency;
    float snowHeight;
};

// ==========================
// 光源
// ==========================
struct Light {
    float type;              // 0=directional, 1=point, 2=spot
    vec3 position;
    vec3 direction;
    vec4 color;
    float intensity;
    float range;
    float innerCutOff;
    float outerCutOff;
    float ambientStrength;
};

uniform Light u_Lights[16];
uniform float u_LightCount;

// ==========================
// 地形高度 + 法线
// ==========================
void computeTerrainHeightAndNormal(
vec2 worldPosXZ,
TerrainMaterial mat,
out float outHeight,
out vec3 outNormal
) {
    float elevation = 0.0;
    float amp = mat.amplitude;
    float freq = mat.frequency;
    vec2 dndx = vec2(0.0);

    for (int i = 0; i < 4; i++) {
        vec3 grad;
        vec3 p = vec3(worldPosXZ.x * freq, 123.4, worldPosXZ.y * freq);
        float n = psrdnoise(p, vec3(0.0), 0.0, grad);

        elevation += amp * (n * 0.5 + 0.5);
        dndx += vec2(grad.x, grad.z) * (amp * 0.5 * freq);

        amp *= 0.45;
        freq *= 2.0;
    }

    outHeight = elevation;
    outNormal = normalize(vec3(-dndx.x, 1.0, -dndx.y));
}

// ==========================
// 衰减 (修正为更稳定的物理衰减)
// ==========================
float calculateAttenuation(float dist, float range) {
    float att = 1.0 / max(dist * dist, 0.01);
    // 使用平滑窗口函数防止硬边缘
    float factor = dist / max(range, 0.001);
    float window = clamp(1.0 - pow(factor, 4.0), 0.0, 1.0);
    return att * (window * window);
}

// ==========================
// 轻量 PBR BRDF (修正分母溢出)
// ==========================
vec3 surfaceShading(PBRParams pixel, vec3 L, vec3 radiance, vec3 V, vec3 N) {
    vec3 H = normalize(V + L);

    float NdotL = clamp(dot(N, L), 0.001, 1.0);
    float NdotV = clamp(dot(N, V), 0.001, 1.0);
    float NdotH = clamp(dot(N, H), 0.0, 1.0);
    float VdotH = clamp(dot(V, H), 0.0, 1.0);

    float roughness = clamp(pixel.roughness, 0.05, 1.0);
    float a = roughness * roughness;
    float a2 = a * a;

    // D 项 (Trowbridge-Reitz GGX)
    float denom = (NdotH * NdotH * (a2 - 1.0) + 1.0);
    float D = a2 / (ZENITH_PI * denom * denom + 1e-6);

    // G 项 (Smith Schlick-GGX)
    float k = (roughness + 1.0);
    k = (k * k) / 8.0;
    float Gv = NdotV / (NdotV * (1.0 - k) + k);
    float Gl = NdotL / (NdotL * (1.0 - k) + k);
    float G = Gv * Gl;

    // F 项 (Schlick)
    vec3 F0 = mix(pixel.f0, pixel.diffuseColor, pixel.metallic);
    vec3 F = F0 + (1.0 - F0) * pow(clamp(1.0 - VdotH, 0.0, 1.0), 5.0);

    // 修正溢出隐患的分母
    vec3 spec = (D * G * F) / (4.0 * NdotV * NdotL + 0.001);

    vec3 kd = (1.0 - F) * (1.0 - pixel.metallic);
    vec3 diff = kd * pixel.diffuseColor / ZENITH_PI;

    return (diff + spec) * radiance * NdotL;
}

// ==========================
// 多光源照明 (修正环境光累加问题)
// ==========================
vec3 evaluateLights(PBRParams pixel, vec3 N, vec3 V, vec3 worldPos) {
    vec3 Lo = vec3(0.0);
    vec3 totalAmbient = vec3(0.0); // 修改：统一计算环境光

    int count = int(min(u_LightCount, 16.0));

    for (int i = 0; i < count; i++) {
        Light light = u_Lights[i];
        vec3 L = vec3(0.0);
        float attenuation = 1.0;

        if (light.type < 0.5) {
            // Directional Light
            L = normalize(-light.direction);
            // 修正：环境光只根据主光源（通常是第一盏平行光）计算一次，防止叠加泛白
            if (i == 0) {
                totalAmbient = pixel.diffuseColor * light.color.rgb * light.ambientStrength;
            }
        } else {
            // Point 或 Spot
            vec3 lightVec = light.position - worldPos;
            float dist = length(lightVec);
            if (dist > light.range) continue;

            L = normalize(lightVec);
            attenuation = calculateAttenuation(dist, light.range);

            if (light.type > 1.5) { // Spot
                float theta = dot(L, normalize(-light.direction));
                float epsilon = light.innerCutOff - light.outerCutOff;
                float spot = clamp((theta - light.outerCutOff) / max(epsilon, 0.0001), 0.0, 1.0);
                attenuation *= spot;
            }
        }

        if (attenuation > 0.0) {
            vec3 radiance = light.color.rgb * light.intensity * attenuation;
            Lo += surfaceShading(pixel, L, radiance, V, N);
        }
    }

    return Lo + totalAmbient;
}

// ==========================
// 地形专用入口
// ==========================
vec3 shadeTerrainMultiLight(
vec3 worldPos,
vec3 V,
vec3 N,
vec3 albedo,
float roughness
) {
    PBRParams p;
    p.diffuseColor = albedo;
    p.f0 = vec3(0.04);
    p.roughness = clamp(roughness, 0.1, 1.0); // 调高基础粗糙度防止刺眼
    p.metallic = 0.0;

    return evaluateLights(p, N, V, worldPos);
}

#endif