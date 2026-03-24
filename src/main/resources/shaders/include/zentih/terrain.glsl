#ifndef ZENITH_TERRAIN_SYSTEM_GLSL
#define ZENITH_TERRAIN_SYSTEM_GLSL

#include "../filament/common_math.glsl"
#include "../filament/brdf.glsl"
#include "../psrdnoise/psrdnoise3.glsl"

struct TerrainMaterial {
    bool  hasTexture;
    float uvScale;
    vec3  grassColor;
    vec3  rockColor;
    vec3  snowColor;
    float amplitude;
    float frequency;
    float snowHeight;
};

// ==========================================
// 1. 程序化地形高度生成 (改为平滑的丘陵算法)
// ==========================================
float computeTerrainHeight(vec2 worldPosXZ, TerrainMaterial mat) {
    float elevation = 0.0;
    float amp = mat.amplitude;
    float freq = mat.frequency;

    for (int i = 0; i < 4; i++) { // 草地不需要太多层高频堆叠，4层足够
        vec3 grad;
        // 【关键修复1】：加入 vec3(0, 123.4, 0) 偏移，打破纯平面的对角线晶格伪影
        float n = psrdnoise(vec3(worldPosXZ * freq, 123.4), vec3(0.0), 0.0, grad);

        // 【关键修复2】：使用平滑的正弦波式起伏 (n * 0.5 + 0.5)，彻底丢弃尖锐的 1.0-abs(n)
        elevation += amp * (n * 0.5 + 0.5);

        amp *= 0.45;
        freq *= 2.0;
    }
    // 移除之前的 pow(..., 1.5) 锐化，保持地面的柔和
    return elevation;
}

// ==========================================
// 2. 宏观法线
// ==========================================
vec3 computeTerrainNormal(vec2 worldPosXZ, TerrainMaterial mat) {
    float delta = 0.5; // 采样跨度大一点，过滤掉微小的锯齿
    float hL = computeTerrainHeight(worldPosXZ - vec2(delta, 0.0), mat);
    float hR = computeTerrainHeight(worldPosXZ + vec2(delta, 0.0), mat);
    float hD = computeTerrainHeight(worldPosXZ - vec2(0.0, delta), mat);
    float hU = computeTerrainHeight(worldPosXZ + vec2(0.0, delta), mat);
    return normalize(vec3(hL - hR, 2.0 * delta, hD - hU));
}

// ==========================================
// 3. 动态天光
// ==========================================
vec3 evaluateAtmosphericAmbient(vec3 N, vec3 lightDir) {
    float sunHeight = smoothstep(-0.2, 0.3, lightDir.y);
    vec3 zenithColor = mix(vec3(0.05, 0.1, 0.2), vec3(0.15, 0.3, 0.6), sunHeight);
    vec3 horizonColor = mix(vec3(0.5, 0.2, 0.1), vec3(0.5, 0.6, 0.7), sunHeight);
    float skyWeight = max(0.0, N.y);
    return mix(horizonColor, zenithColor, skyWeight) * mix(0.1, 1.0, sunHeight);
}

// ==========================================
// 4. 核心地形着色
// ==========================================
vec3 shadeTerrain(
vec3 worldPos, vec3 viewDir, vec3 lightDir, vec3 lightIntensity,
TerrainMaterial mat, vec3 texAlbedo, vec3 texNormal, float texRoughness
) {
    vec3 V = viewDir;
    vec3 L = lightDir;
    vec3 N = computeTerrainNormal(worldPos.xz, mat);

    vec3 albedo;
    float roughness;
    float grassMask = 1.0;

    // 生成非常柔和的大面积色彩变化，避免密密麻麻的斑点
    vec3 grad;
    float colorNoise = psrdnoise(vec3(worldPos.x * 0.01, 10.0, worldPos.z * 0.01), vec3(0.0), 0.0, grad) * 0.5 + 0.5;

    // 草地颜色：翠绿与偏黄绿色的自然过渡
    vec3 vibrantGrass = mat.grassColor;
    vec3 warmGrass = mat.grassColor * vec3(1.1, 1.1, 0.6);
    albedo = mix(vibrantGrass, warmGrass, colorNoise);

    roughness = 0.95; // 草地必须极其粗糙，不能反光！

    // 法线微扰：只做一点点，增加草地表面的微小绒毛光影，不破坏整体感
    vec3 bumpGrad;
    psrdnoise(vec3(worldPos.x * 0.2, 0.0, worldPos.z * 0.2), vec3(0.0), 0.0, bumpGrad);
    N = normalize(N - vec3(bumpGrad.x, 0.0, bumpGrad.z) * 0.1);

    // 标准 PBR
    vec3 H = normalize(V + L);
    float NoV = clamp(dot(N, V), 0.001, 1.0);
    float NoL = saturate(dot(N, L));
    float NoH = saturate(dot(N, H));
    float LoH = saturate(dot(L, H));

    vec3 Fd = albedo * Fd_Lambert();
    float D = D_GGX(roughness, NoH);
    float V_vis = V_SmithGGXCorrelated(roughness, NoV, NoL);
    vec3 Fr = (D * V_vis) * F_Schlick(vec3(0.04), LoH);

    // 【关键修复3】：草地的边缘柔光取代了原本塑料感的镜面高光
    float sheen = pow(1.0 - NoV, 3.0) * NoL * 0.5;
    Fr = albedo * sheen;

    // 合成
    vec3 directLighting = (Fd + Fr) * lightIntensity * NoL * PI;
    // 加入阳光穿透草叶的散射(SSS)
    float sss = pow(max(0.0, dot(V, -L)), 4.0) * (1.0 - NoV) * 0.3;
    directLighting += albedo * vec3(1.5, 1.5, 0.5) * sss * lightIntensity;

    vec3 ambientLighting = albedo * evaluateAtmosphericAmbient(N, lightDir);

    return directLighting + ambientLighting;
}
#endif