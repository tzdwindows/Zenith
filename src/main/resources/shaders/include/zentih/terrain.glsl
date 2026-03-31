#ifndef ZENITH_TERRAIN_SYSTEM_GLSL
#define ZENITH_TERRAIN_SYSTEM_GLSL

#include "../filament/common_math.glsl"
#include "../filament/brdf.glsl"

// 1. 必须在包含 surface_shading.glsl 之前定义 PBRParams
struct PBRParams {
    vec3  diffuseColor;
    vec3  f0;
    float roughness;
    float metallic;
};

#include "../filament/surface_shading.glsl"
#include "../psrdnoise/psrdnoise3.glsl"

struct TerrainMaterial {
    float hasGrassMap;
    float hasRockMap;
    float hasNormalMap;
    float uvScale;

    vec3  grassColor;
    vec3  rockColor;
    vec3  snowColor;

    float amplitude;
    float frequency;
    float snowHeight;
};

// ==========================================
// 1. 程序化地形高度 + 法线 (保持不变)
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
// 2. 大气环境光 (保持不变)
// ==========================================
vec3 evaluateAtmosphericAmbient(vec3 N, vec3 lightDir) {
    float sunY = lightDir.y;
    vec3 nightSky   = vec3(0.01, 0.02, 0.05);
    vec3 sunsetSky  = vec3(0.60, 0.30, 0.15);
    vec3 daySky     = vec3(0.15, 0.30, 0.60);
    vec3 skyColor = (sunY < 0.0) ? mix(nightSky, sunsetSky, smoothstep(-0.2, 0.0, sunY))
    : mix(sunsetSky, daySky, smoothstep(0.0, 0.3, sunY));
    float skyWeight = mix(0.3, 1.0, max(0.0, N.y));
    float timeBrightness = mix(0.05, 1.0, smoothstep(-0.2, 0.2, sunY));
    float nightAtten = smoothstep(-0.05, -0.3, sunY);
    vec3 moonAmbient = vec3(0.02, 0.04, 0.08) * smoothstep(0.0, -0.2, sunY);
    return ((skyColor * skyWeight * timeBrightness) * (1.0 - nightAtten)) + moonAmbient;
}

// ==========================================
// 3. 地形材质混合 (保持不变)
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
// 4. 修改后的核心 PBR 着色
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

    float NoV = clamp(dot(N, V), 0.001, 1.0);
    // 【修复1】将下限从 0.0 改为 0.001，防止 Filament BRDF 内部除以 0 导致 NaN (死黑)
    float NoL = clamp(dot(N, L), 0.001, 1.0);

    // 1. 初始化 PBR 参数
    PBRParams pixel;
    pixel.diffuseColor = finalAlbedo * 0.9;
    pixel.f0           = vec3(0.04);
    pixel.roughness    = clamp(finalRoughness, 0.05, 1.0);
    pixel.metallic     = 0.0;

    // 2. 直接光着色
    vec3 directLighting = surfaceShading(pixel, L, lightIntensity, V, N);

    // 3. 草地透射 (SSS)
    float sheen = pow(1.0 - NoV, 3.0) * NoL * 0.2;
    vec3 grassFuzz = finalAlbedo * sheen * grassMask * lightIntensity;
    float sssFactor = pow(max(0.0, dot(V, -L)), 6.0) * (1.0 - NoV) * 0.1;
    vec3 grassSSS = finalAlbedo * vec3(1.1, 1.1, 0.8) * sssFactor * lightIntensity * grassMask;

    // 4. 环境光部分
    float ambientAO = mix(0.4, 1.0, max(0.0, N.y) * 0.5 + 0.5); // 防止底部过暗
    vec3 ambientLighting = finalAlbedo * evaluateAtmosphericAmbient(N, lightDir) * ambientAO;

    // 5. 组合最终颜色 (HDR 线性空间)
    vec3 finalColor = directLighting + grassFuzz + grassSSS + ambientLighting;

    // 【修复2】删除此处的 Reinhard 和 Gamma，交给 Java 里的 Fragment Shader 统一处理！
    // finalColor = finalColor / (finalColor + vec3(1.0));
    // finalColor = pow(finalColor, vec3(1.0 / 2.2));

    return finalColor;
}


#endif