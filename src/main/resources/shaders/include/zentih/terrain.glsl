#ifndef ZENITH_TERRAIN_SYSTEM_GLSL
#define ZENITH_TERRAIN_SYSTEM_GLSL

#include "../filament/common_math.glsl"
#include "../filament/brdf.glsl"
#include "../psrdnoise/psrdnoise3.glsl"

struct TerrainMaterial {
    bool  hasGrassMap;
    bool  hasRockMap;
    bool  hasNormalMap;
    float uvScale;

    vec3  grassColor;
    vec3  rockColor;
    vec3  snowColor;

    float amplitude;
    float frequency;
    float snowHeight;
};

// ==========================================
// 1. 程序化地形高度 + 法线
// ==========================================
void computeTerrainHeightAndNormal(vec2 worldPosXZ, TerrainMaterial mat, out float outHeight, out vec3 outNormal) {
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

float computeTerrainHeight(vec2 worldPosXZ, TerrainMaterial mat) {
    float h; vec3 n;
    computeTerrainHeightAndNormal(worldPosXZ, mat, h, n);
    return h;
}

// ==========================================
// 2. 大气环境光（已修复夜晚亮度问题）
// ==========================================
vec3 evaluateAtmosphericAmbient(vec3 N, vec3 lightDir) {
    float sunY = lightDir.y;

    vec3 nightSky   = vec3(0.01, 0.02, 0.05);
    vec3 sunsetSky  = vec3(0.60, 0.30, 0.15);
    vec3 daySky     = vec3(0.15, 0.30, 0.60);

    vec3 skyColor;

    if (sunY < 0.0) {
        skyColor = mix(nightSky, sunsetSky, smoothstep(-0.2, 0.0, sunY));
    } else {
        skyColor = mix(sunsetSky, daySky, smoothstep(0.0, 0.3, sunY));
    }

    float skyWeight = mix(0.3, 1.0, max(0.0, N.y));

    // ✅ 修复：夜晚更暗
    float timeBrightness = mix(0.05, 1.0, smoothstep(-0.2, 0.2, sunY));

    // ✅ 夜晚快速衰减
    float nightAtten = smoothstep(-0.05, -0.3, sunY);

    vec3 moonAmbient = vec3(0.02, 0.04, 0.08) * smoothstep(0.0, -0.2, sunY);

    return ((skyColor * skyWeight * timeBrightness) * (1.0 - nightAtten)) + moonAmbient;
}

// ==========================================
// 3. 地形材质混合
// ==========================================
void evaluateTerrainMaterial(
vec3 worldPos,
vec3 normal,
TerrainMaterial mat,
out vec3 outAlbedo,
out float outRoughness,
out float outGrassMask
) {
    float slope = 1.0 - normal.y;

    float rockWeight = smoothstep(0.15, 0.35, slope);

    float heightWeight = smoothstep(mat.snowHeight - 2.0, mat.snowHeight + 2.0, worldPos.y);
    float snowSlopeRetain = 1.0 - smoothstep(0.35, 0.55, slope);
    float snowWeight = heightWeight * snowSlopeRetain;

    vec3 albedo = mix(mat.grassColor, mat.rockColor, rockWeight);
    albedo = mix(albedo, mat.snowColor, snowWeight);

    float roughness = mix(0.85, 0.65, rockWeight);
    roughness = mix(roughness, 0.45, snowWeight);

    outGrassMask = (1.0 - rockWeight) * (1.0 - snowWeight);

    outAlbedo = albedo;
    outRoughness = roughness;
}

// ==========================================
// 4. 核心 PBR 着色
// ==========================================
vec3 shadeTerrain(
vec3 worldPos,
vec3 viewDir,
vec3 lightDir,
vec3 lightIntensity,
TerrainMaterial mat,
vec3 finalAlbedo,
vec3 finalNormal,
float finalRoughness,
float grassMask
) {
    vec3 N = normalize(finalNormal);
    vec3 V = viewDir;
    vec3 L = lightDir;
    vec3 H = normalize(V + L);

    float NoV = clamp(dot(N, V), 0.001, 1.0);
    float NoL = clamp(dot(N, L), 0.0, 1.0);
    float NoH = clamp(dot(N, H), 0.0, 1.0);
    float LoH = clamp(dot(L, H), 0.0, 1.0);

    vec3 Fd = finalAlbedo * Fd_Lambert();
    float D = D_GGX(finalRoughness, NoH);
    float V_vis = V_SmithGGXCorrelated(finalRoughness, NoV, NoL);
    vec3 Fr = (D * V_vis) * F_Schlick(vec3(0.04), LoH);

    // 草地边缘柔光
    float sheen = pow(1.0 - NoV, 3.0) * NoL * 0.5;
    vec3 grassFuzz = finalAlbedo * sheen * grassMask;

    vec3 directLighting = (Fd + Fr + grassFuzz) * lightIntensity * NoL * 3.14159265;

    // 次表面散射（草地）
    float sss = pow(max(0.0, dot(V, -L)), 6.0) * (1.0 - NoV) * 0.15;
    directLighting += finalAlbedo * vec3(1.2, 1.2, 0.5) * sss * lightIntensity * grassMask;

    vec3 ambientLighting = finalAlbedo * evaluateAtmosphericAmbient(N, lightDir);

    return directLighting + ambientLighting;
}

#endif