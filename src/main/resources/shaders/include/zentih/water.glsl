#ifndef ZENITH_WATER_SYSTEM_GLSL
#define ZENITH_WATER_SYSTEM_GLSL

#include "brdf.glsl"
#include "common_math.glsl"
#include "psrdnoise3.glsl"

#define MAX_SPLASHES 4
uniform vec4 u_ActiveSplashes[MAX_SPLASHES];

struct WaterMaterial {
    vec3  deepColor;
    vec3  shallowColor;
    float roughness;
    float metallic;
    float reflectance;
    float ior;
    float clarity;       // 水的清澈度（决定光线吸收率）
    float rainIntensity;
};

// --- Hash 与 随机函数 ---
vec2 hash22(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * vec3(.1031, .1030, .0973));
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.xx + p3.yz) * p3.zy);
}
float hash12(vec2 p) {
    vec3 p3  = fract(vec3(p.xyx) * .1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

// --- 改进版 Gerstner Waves (宏观涌浪) ---
// wave = (dirX, dirY, steepness, wavelength)
float computeGerstner(vec2 uv, float time, vec4 wave, inout vec3 tangent, inout vec3 binormal) {
    float k = 2.0 * PI / wave.w;
    float c = sqrt(9.8 / k);
    vec2 d = normalize(wave.xy);
    float f = k * (dot(d, uv) - c * time);
    float a = wave.z / k;

    float sinf = sin(f);
    float cosf = cos(f);

    // 引入一点时间扰动，打破绝对规律的波浪
    float wa = a * (1.0 + 0.2 * sin(time * 0.5 + dot(uv, vec2(0.5))));

    tangent += vec3(-d.x * d.x * (wa * k * sinf), d.x * (wa * k * cosf), -d.x * d.y * (wa * k * sinf));
    binormal += vec3(-d.x * d.y * (wa * k * sinf), d.y * (wa * k * cosf), -d.y * d.y * (wa * k * sinf));

    return wa * sinf;
}

// --- 【核心1】多层交叉微观风浪 (彻底消除单向流动感) ---
// 使用 Fractional Brownian Motion (fBm) 叠加多层不同方向、速度、缩放的噪声
vec3 computeMicroRipples(vec3 worldPos, float time) {
    vec3 n1, n2, n3, n4;

    // 分别向四个完全不同的方向流动，速度和尺度成比例缩放
    vec3 p1 = worldPos * 1.2 + vec3(time * 0.15, 0.0, time * 0.1);
    vec3 p2 = worldPos * 2.5 - vec3(time * 0.2, 0.0, -time * 0.15);
    vec3 p3 = worldPos * 5.0 + vec3(-time * 0.25, 0.0, time * 0.05);
    vec3 p4 = worldPos * 10.0 - vec3(time * 0.3, 0.0, time * 0.2);

    psrdnoise(p1, vec3(0.0), 0.0, n1);
    psrdnoise(p2, vec3(0.0), 0.0, n2);
    psrdnoise(p3, vec3(0.0), 0.0, n3);
    psrdnoise(p4, vec3(0.0), 0.0, n4);

    // 权重递减叠加，产生极其丰富的高频细节
    vec3 totalGrad = n1 * 0.4 + n2 * 0.25 + n3 * 0.15 + n4 * 0.08;

    // 将梯度转换为切线空间法线
    return normalize(vec3(totalGrad.x, 1.0, totalGrad.z));
}

// --- 程序化雨滴涟漪 (保持原有优化) ---
vec3 computeRainRipples(vec2 worldPos, float time, float intensity) {
    if (intensity <= 0.001) return vec3(0.0);
    vec2 uv = worldPos * 0.8;
    vec2 grid = floor(uv);
    vec2 fracUv = fract(uv);
    vec2 normalOffset = vec2(0.0);
    float timeSpeed = time * 1.5;

    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            vec2 offset = vec2(x, y);
            vec2 dropPos = offset + hash22(grid + offset);
            vec2 diff = fracUv - dropPos;
            float dist = length(diff);
            float dropTime = fract(timeSpeed + hash12(grid + offset) * 10.0);

            if (dropTime < 0.6) {
                float wavePhase = dist * 25.0 - dropTime * 35.0;
                float attenuation = max(0.0, 1.0 - dist) * max(0.0, 1.0 - (dropTime / 0.6));
                float derivative = cos(wavePhase) * attenuation * 0.8;
                normalOffset += normalize(diff + 0.0001) * derivative * intensity;
            }
        }
    }
    return vec3(normalOffset.x, 0.0, normalOffset.y);
}

// --- 动态物体落水溅射 ---
vec3 computeDynamicSplashes(vec3 worldPos) {
    vec2 normalOffset = vec2(0.0);
    for (int i = 0; i < MAX_SPLASHES; i++) {
        vec4 splash = u_ActiveSplashes[i];
        float age = splash.w;
        if (age >= 0.0 && age < 5.0) {
            vec2 diff = worldPos.xz - splash.xz;
            float dist = length(diff);
            float wavePhase = dist * 15.0 - age * 8.0;
            float attenuation = max(0.0, 1.0 - (dist * 0.2)) * max(0.0, 1.0 - (age * 0.2));
            if (dist < age * 4.0) {
                normalOffset += normalize(diff + 0.0001) * cos(wavePhase) * attenuation * 0.4;
            }
        }
    }
    return vec3(normalOffset.x, 0.0, normalOffset.y);
}

// --- 【核心2】HDR级物理天空环境模拟 (如果你没有环境Cubemap) ---
vec3 simulatePhysicalSky(vec3 viewDir, vec3 lightDir) {
    float zenith = max(0.0, viewDir.y);
    // 天空渐变：从地平线的雾霾蓝白过度到天顶的深邃蓝
    vec3 skyColor = mix(vec3(0.4, 0.6, 0.85), vec3(0.05, 0.15, 0.4), pow(zenith, 0.7));

    // 地平线大气散射白化
    float horizonDist = 1.0 - max(0.0, abs(viewDir.y));
    skyColor = mix(skyColor, vec3(0.85, 0.9, 0.95), pow(horizonDist, 8.0));

    // 太阳光晕 (大气散射)
    float sunGlow = max(0.0, dot(viewDir, lightDir));
    skyColor += vec3(1.0, 0.8, 0.5) * pow(sunGlow, 8.0) * 0.5;

    // 真实的太阳圆盘倒影
    float sunDisk = pow(sunGlow, 2048.0);
    skyColor += vec3(5.0, 4.0, 3.0) * sunDisk; // HDR亮度超限，需配合后处理泛光(Bloom)

    return skyColor;
}

// --- 主着色函数 ---
vec3 shadeWater(vec3 worldPos, vec3 viewDir, vec3 lightDir, vec3 lightIntensity, WaterMaterial mat, int mode, float time) {
    vec3 tangent = vec3(1.0, 0.0, 0.0);
    vec3 binormal = vec3(0.0, 0.0, 1.0);
    float waveHeight = 0.0;

    // 1. 宏观物理波浪：采用四个方向互相冲突的波浪，彻底打破单一流动感
    waveHeight += computeGerstner(worldPos.xz, time, vec4(0.8, 0.6, 0.3, 18.0), tangent, binormal);
    waveHeight += computeGerstner(worldPos.xz, time * 1.1, vec4(-0.7, 0.7, 0.2, 11.0), tangent, binormal);
    waveHeight += computeGerstner(worldPos.xz, time * 0.9, vec4(0.4, -0.9, 0.15, 6.0), tangent, binormal);
    waveHeight += computeGerstner(worldPos.xz, time * 1.3, vec4(-0.5, -0.5, 0.08, 3.0), tangent, binormal);

    vec3 macroNormal = normalize(cross(binormal, tangent));

    // 2. 混合各层法线细节
    vec3 microNormal = computeMicroRipples(worldPos, time);
    vec3 rainNormal = computeRainRipples(worldPos.xz, time, mat.rainIntensity);
    vec3 splashNormal = computeDynamicSplashes(worldPos);

    // 法线混合技术：利用微观细节扰动宏观法线
    vec3 N = normalize(macroNormal + microNormal * 1.5 + vec3(rainNormal.x, 0.0, rainNormal.z) * 2.0 + vec3(splashNormal.x, 0.0, splashNormal.z));

    // --- 光照向量准备 ---
    vec3 V = viewDir;
    vec3 L = lightDir;
    vec3 H = normalize(V + L);
    float NoV = clamp(dot(N, V), 0.0001, 1.0);
    float NoL = clamp(dot(N, L), 0.0, 1.0);
    float NoH = clamp(dot(N, H), 0.0, 1.0);
    float VoH = clamp(dot(V, H), 0.0, 1.0);

    // --- 【核心3】光学：基于菲涅尔与水体吸收的反射折射 ---
    // F0对于水通常是 0.02 (IOR ~1.33)
    vec3 f0 = vec3(0.02);
    // 菲涅尔效应：掠角反射天空，直视折射水底
    vec3 F = F_Schlick(f0, max(dot(H, V), 0.0));

    // 反射：环境天空与太阳
    vec3 reflectionDir = reflect(-V, N);
    vec3 skyReflection = simulatePhysicalSky(reflectionDir, L);
    vec3 specularEnv = skyReflection * F;

    // 太阳强高光 (GGX)：破碎的高光带来波光粼粼的质感
    float D = D_GGX(mat.roughness, NoH);
    float V_vis = V_SmithGGXCorrelated(mat.roughness, NoV, NoL);
    vec3 sunSpecular = (D * V_vis) * F * lightIntensity * NoL * PI;

    // 额外物理闪烁点 (Glitter/Sparkle)，模拟阳光打在极小波峰上的点点星光
    float sparkleThreshold = 0.98;
    float sparkle = smoothstep(sparkleThreshold, 1.0, NoH) * mat.reflectance;
    sunSpecular += vec3(5.0) * sparkle * lightIntensity; // 同样需配合 Bloom

    // --- 【核心4】光学：基于深度的水体吸收与次表面散射 ---
    // 模拟 Beer-Lambert 法则的色彩吸收 (水越深越蓝/绿)
    float opticalDepth = exp(-mat.clarity * NoV * 2.0); // 视线越垂直，看透的深度越深
    vec3 waterBodyColor = mix(mat.deepColor, mat.shallowColor, opticalDepth);
    vec3 refractionColor = waterBodyColor * (vec3(1.0) - F);

    // 次表面散射 (SSS)：当阳光照过波浪背脊时产生的高级透光感
    float scatterView = max(0.0, dot(V, -L)); // 逆光观察
    // 仅在波峰且视线迎着光线时透光
    float sssWeight = pow(scatterView, 4.0) * smoothstep(0.0, 1.0, waveHeight * 1.5) * (1.0 - NoV);
    vec3 sssColor = mat.shallowColor * sssWeight * lightIntensity * vec3(2.0, 3.0, 2.5); // 稍微偏绿/蓝的透射光

    // --- 最终色彩合成 ---
    return specularEnv + sunSpecular + refractionColor + sssColor;
}
#endif