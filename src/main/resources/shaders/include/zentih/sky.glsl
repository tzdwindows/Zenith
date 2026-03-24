#ifndef ZENITH_SKY_SYSTEM_GLSL
#define ZENITH_SKY_SYSTEM_GLSL

#include "filament/common_math.glsl"
#include "psrdnoise/psrdnoise3.glsl"

struct SkyParameters {
    vec3  sunDirection;
    float cloudCoverage;
    float cloudSpeed;
    float time;
};

// ==========================================
// 物理常量定义 (单位严格转换为 千米 km，防止 Float 精度溢出崩溃)
// ==========================================
#define PI 3.14159265359
#define EARTH_RADIUS 6371.0 // 6371 千米
#define CLOUD_START 1.5     // 云层高度 1.5 千米

// 瑞利散射系数 (波长依赖：蓝光散射远大于红光) - 转换为 km^-1
const vec3 RAYLEIGH_BETA = vec3(5.5e-3, 13.0e-3, 22.4e-3) * 3.0;
// 米氏散射系数 (所有波长均匀散射，形成白色光晕) - 转换为 km^-1
const vec3 MIE_BETA = vec3(21.0e-3);

// 高质量无向哈希函数 (用于生成星星)
float hash(vec3 p) {
    p = fract(p * 0.3183099 + .1);
    p *= 17.0;
    return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
}

// 带微小畸变的 psrdnoise 映射
float getNoise3D(vec3 p) {
    vec3 grad;
    float n = psrdnoise(p, vec3(0.0), 0.0, grad);
    return n * 0.5 + 0.5;
}

// 射线与球面交点 (使用千米单位，精度完美)
float intersectSphere(vec3 origin, vec3 dir, float radius) {
    float b = dot(origin, dir);
    float c = dot(origin, origin) - radius * radius;
    float d = b * b - c;
    if (d < 0.0) return -1.0;
    return -b + sqrt(d);
}

// 瑞利散射相位函数
float rayleighPhase(float cosTheta) {
    return 3.0 / (16.0 * PI) * (1.0 + cosTheta * cosTheta);
}

// Henyey-Greenstein 相位函数
float hgPhase(float cosTheta, float g) {
    float g2 = g * g;
    return (1.0 - g2) / (4.0 * PI * pow(1.0 + g2 - 2.0 * g * cosTheta, 1.5));
}

// 高级分形布朗运动 (带有流体质感的云层)
float fbmClouds(vec3 p, float time, float speed) {
    float density = 0.0;
    float amplitude = 1.0; // 恢复了振幅，防止云层变得不可见
    float frequency = 1.0;

    vec3 wind = vec3(1.0, 0.0, 0.5) * time * speed;

    // 域扭曲 (Domain Warping)
    vec3 warp = vec3(
    getNoise3D(p * 0.5 + wind * 0.2),
    0.0,
    getNoise3D(p * 0.5 - wind * 0.2)
    ) * 1.5;

    for(int i = 0; i < 5; i++) {
        density += getNoise3D(p * frequency + wind * frequency + warp) * amplitude;
        amplitude *= 0.48;
        frequency *= 2.03;
        wind = wind * 1.1;
    }
    return pow(density, 1.2);
}

// ==========================================
// 核心：基于物理的动态天空渲染
// ==========================================
vec3 renderDynamicSky(vec3 viewDir, SkyParameters params) {
    vec3 V = normalize(viewDir);
    vec3 L = normalize(params.sunDirection);

    vec3 earthCenter = vec3(0.0, -EARTH_RADIUS, 0.0);
    vec3 camPos = vec3(0.0, 0.0, 0.0);

    float cosTheta = dot(V, L);
    float zenithAngle = max(0.0, V.y);
    float sunHeight = L.y;

    // 太阳昼夜过渡权重
    float dayNightFade = smoothstep(-0.1, 0.1, sunHeight);

    // ==========================================
    // 1. 大气透射率与光学厚度 (Optical Depth)
    // ==========================================
    float viewY = max(0.0, V.y);
    float sunY = max(0.0, L.y);

    // 这里的 +0.15 是极度关键的近似：模拟地球大气的查普曼曲率积分，防止日落时地平线彻底变黑
    float opticalDepthR = 8.0 / (viewY + 0.15);
    float opticalDepthM = 1.2 / (viewY + 0.15);
    float sunOpticalDepthR = 8.0 / (sunY + 0.15);
    float sunOpticalDepthM = 1.2 / (sunY + 0.15);

    // 透射率 (Beer-Lambert Law)
    vec3 sunTransmittance = exp(-(RAYLEIGH_BETA * sunOpticalDepthR + MIE_BETA * sunOpticalDepthM));
    vec3 viewTransmittance = exp(-(RAYLEIGH_BETA * opticalDepthR + MIE_BETA * opticalDepthM));

    // ==========================================
    // 2. 物理单次散射积分计算
    // ==========================================
    vec3 extinction = RAYLEIGH_BETA + MIE_BETA;

    // 必须除以 Extinction 归一化，否则光线能量将直接坍缩为纯黑
    vec3 rayleighScattering = (RAYLEIGH_BETA / extinction) * rayleighPhase(cosTheta);
    float miePhaseVal = hgPhase(cosTheta, 0.8) + hgPhase(cosTheta, 0.1) * 0.2;
    vec3 mieScattering = (MIE_BETA / extinction) * miePhaseVal;

    // 完整的物理单次散射方程: L = L_sun * Phase * (Scatter/Extinction) * (1 - Transmittance)
    vec3 scattering = (rayleighScattering + mieScattering) * (1.0 - viewTransmittance) * sunTransmittance * 40.0 * dayNightFade;

    // ==========================================
    // 3. 渲染物理太阳圆盘与环境光
    // ==========================================
    float sunAngularRadius = 0.99985;
    float sunDisk = smoothstep(sunAngularRadius - 0.0001, sunAngularRadius, cosTheta);
    float limbDarkening = pow(max(0.0, (cosTheta - sunAngularRadius) / (1.0 - sunAngularRadius)), 0.3);
    vec3 sunDiskColor = vec3(40.0) * sunTransmittance * limbDarkening * sunDisk * dayNightFade;

    // 天空环境底光
    vec3 ambientSky = vec3(0.02, 0.04, 0.08) * clamp(-sunHeight * 2.0, 0.0, 1.0);

    // 星空系统 (自带亮度星级、闪烁以及晨昏隐去)
    vec3 stars = vec3(0.0);
    if (sunHeight < 0.1) {
        float starVal = hash(V * 300.0);
        float starMask = smoothstep(0.997, 1.0, starVal);
        vec3 starColor = mix(vec3(0.6, 0.8, 1.0), vec3(1.0, 0.9, 0.7), hash(V * 100.0));
        float twinkle = (sin(params.time * 5.0 + starVal * 100.0) * 0.5 + 0.5);
        twinkle = mix(twinkle, 1.0, zenithAngle);
        float nightFade = smoothstep(0.1, -0.1, sunHeight);
        stars = starMask * starColor * twinkle * nightFade * 3.0;
    }

    // 组合当前的大气背景
    vec3 atmosphere = scattering + sunDiskColor + ambientSky + stars;
    vec3 finalSky = atmosphere;

    // ==========================================
    // 4. 伪体积云层 (基于千米级求交与厚度吸收)
    // ==========================================
    float cloudDist = intersectSphere(camPos - earthCenter, V, EARTH_RADIUS + CLOUD_START);

    // 过滤掉地平线以下的无效像素
    if (cloudDist > 0.0 && V.y > 0.02) {
        // 采样坐标缩放，适配之前旧代码的视觉大小
        vec3 cloudPos = (camPos + V * cloudDist) * 0.15;

        float cloudDensity = fbmClouds(cloudPos, params.time, params.cloudSpeed);
        // 通过平滑步进控制云层覆盖率
        cloudDensity = smoothstep(1.0 - params.cloudCoverage, 1.5 - params.cloudCoverage, cloudDensity);

        if (cloudDensity > 0.0) {
            // 比尔定律(内自阴影)与多次散射糖粉效应(边缘白边)
            float beerLaw = exp(-cloudDensity * 4.0);
            float powder = 1.0 - exp(-cloudDensity * 2.0);

            float hg = hgPhase(cosTheta, 0.6) * 1.5 + 0.3;
            vec3 directLight = sunTransmittance * hg * beerLaw * powder * 25.0 * dayNightFade;

            // 云层底部环境光反馈 (底盘发蓝、日落泛红发灰)
            vec3 cloudAmbientLight = mix(vec3(0.2, 0.3, 0.4), vec3(0.8, 0.8, 0.9), zenithAngle);
            cloudAmbientLight = mix(cloudAmbientLight, vec3(0.01, 0.02, 0.04), smoothstep(0.1, -0.1, sunHeight));

            vec3 cloudColor = directLight + cloudAmbientLight;

            // 真实的大气透视 (Aerial Perspective)，距离越远，云越融入地平线的大气色
            float distanceFade = exp(-cloudDist * 0.015);
            cloudColor = mix(atmosphere, cloudColor, distanceFade);

            float alpha = cloudDensity * distanceFade;
            finalSky = mix(atmosphere, cloudColor, alpha);
        }
    }

    return max(finalSky, vec3(0.0));
}

#endif