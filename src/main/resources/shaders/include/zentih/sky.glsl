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

#define EARTH_RADIUS 6371.0
#define CLOUD_START 1.5

const vec3 RAYLEIGH_BETA = vec3(5.5e-3, 13.0e-3, 22.4e-3) * 3.0;
const vec3 MIE_BETA = vec3(21.0e-3);

float hash(vec3 p) {
    p = fract(p * 0.3183099 + .1);
    p *= 17.0;
    return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
}

float getNoise3D(vec3 p) {
    vec3 grad;
    float n = psrdnoise(p, vec3(0.0), 0.0, grad);
    return n * 0.5 + 0.5;
}

float intersectSphere(vec3 origin, vec3 dir, float radius) {
    float b = dot(origin, dir);
    float c = dot(origin, origin) - radius * radius;
    float d = b * b - c;
    if (d < 0.0) return -1.0;
    return -b + sqrt(d);
}

float rayleighPhase(float cosTheta) {
    return 3.0 / (16.0 * PI) * (1.0 + cosTheta * cosTheta);
}

float hgPhase(float cosTheta, float g) {
    float g2 = g * g;
    return (1.0 - g2) / (4.0 * PI * pow(1.0 + g2 - 2.0 * g * cosTheta, 1.5));
}

float fbmClouds(vec3 p, float time, float speed) {
    float density = 0.0;
    float amplitude = 1.0;
    float frequency = 1.0;

    vec3 wind = vec3(1.0, 0.0, 0.5) * time * speed;

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

    float dayNightFade = smoothstep(-0.05, 0.1, sunHeight);

    float viewY = max(0.0, V.y);
    float sunY = max(0.0, L.y);

    float opticalDepthR = 8.0 / (viewY + 0.15);
    float opticalDepthM = 1.2 / (viewY + 0.15);
    float sunOpticalDepthR = 8.0 / (sunY + 0.15);
    float sunOpticalDepthM = 1.2 / (sunY + 0.15);

    vec3 sunTransmittance = exp(-(RAYLEIGH_BETA * sunOpticalDepthR + MIE_BETA * sunOpticalDepthM));
    vec3 viewTransmittance = exp(-(RAYLEIGH_BETA * opticalDepthR + MIE_BETA * opticalDepthM));

    vec3 extinction = RAYLEIGH_BETA + MIE_BETA;

    vec3 rayleighScattering = (RAYLEIGH_BETA / extinction) * rayleighPhase(cosTheta);
    float miePhaseVal = hgPhase(cosTheta, 0.8) + hgPhase(cosTheta, 0.1) * 0.2;
    vec3 mieScattering = (MIE_BETA / extinction) * miePhaseVal;

    vec3 scattering = (rayleighScattering + mieScattering) * (1.0 - viewTransmittance) * sunTransmittance * 40.0 * dayNightFade;

    // 【关键修复1：太阳消失】
    // 降低了对精度的苛刻要求，让边缘更柔和，确保在所有显卡上都能画出明亮的太阳圆盘
    float sunAngularRadius = 0.9992; // 稍微变大一点点
    float sunDisk = smoothstep(sunAngularRadius - 0.0005, sunAngularRadius + 0.0001, cosTheta);
    vec3 sunDiskColor = vec3(100.0) * sunTransmittance * sunDisk * dayNightFade;

    vec3 ambientSky = vec3(0.02, 0.03, 0.05) * clamp(-sunHeight * 2.0, 0.0, 1.0);

    // 【关键修复2：稳定的星空系统】
    // 彻底抛弃了纯粹依赖视角的噪点，改为“三维网格空间划分”。保证星星大小一致，且视角移动时绝不闪烁跳跃！
    vec3 stars = vec3(0.0);
    if (sunHeight < 0.1) {
        float starGridSize = 300.0;
        vec3 gridP = floor(V * starGridSize);      // 获取网格的固定 ID
        vec3 fractP = fract(V * starGridSize) - 0.5; // 获取网格内的坐标

        float cellHash = hash(gridP);              // 每个网格生成一个唯一的伪随机数

        // 只有 1.5% 的网格会生成星星
        if (cellHash > 0.985) {
            float dist = length(fractP); // 到网格中心的距离，保证星星是圆形的点
            float size = mix(0.1, 0.25, hash(gridP + 1.0)); // 随机大小

            float starMask = smoothstep(size, size - 0.05, dist);
            vec3 starColor = mix(vec3(0.6, 0.8, 1.0), vec3(1.0, 0.9, 0.7), hash(gridP + 2.0));

            // 闪烁频率
            float twinkle = sin(params.time * 2.0 + cellHash * 100.0) * 0.4 + 0.6;
            twinkle = mix(twinkle, 1.0, zenithAngle); // 天顶星星不闪

            float nightFade = smoothstep(0.1, -0.05, sunHeight);
            stars = starMask * starColor * twinkle * nightFade * 2.5;
        }
    }

    vec3 atmosphere = scattering + sunDiskColor + ambientSky + stars;
    vec3 finalSky = atmosphere;

    float cloudDist = intersectSphere(camPos - earthCenter, V, EARTH_RADIUS + CLOUD_START);

    if (cloudDist > 0.0 && V.y > 0.02) {
        vec3 cloudPos = (camPos + V * cloudDist) * 0.15;

        float cloudDensity = fbmClouds(cloudPos, params.time, params.cloudSpeed);
        cloudDensity = smoothstep(1.0 - params.cloudCoverage, 1.5 - params.cloudCoverage, cloudDensity);

        if (cloudDensity > 0.0) {
            float beerLaw = exp(-cloudDensity * 4.0);
            float powder = 1.0 - exp(-cloudDensity * 2.0);

            float hg = hgPhase(cosTheta, 0.6) * 1.5 + 0.3;
            vec3 directLight = sunTransmittance * hg * beerLaw * powder * 25.0 * dayNightFade;

            vec3 cloudAmbientLight = mix(vec3(0.2, 0.3, 0.4), vec3(0.8, 0.8, 0.9), zenithAngle);
            cloudAmbientLight = mix(cloudAmbientLight, vec3(0.01, 0.02, 0.04), smoothstep(0.1, -0.1, sunHeight));

            vec3 cloudColor = directLight + cloudAmbientLight;

            float distanceFade = exp(-cloudDist * 0.015);
            cloudColor = mix(atmosphere, cloudColor, distanceFade);

            float alpha = cloudDensity * distanceFade;
            finalSky = mix(atmosphere, cloudColor, alpha);
        }
    }

    return max(finalSky, vec3(0.0));
}
#endif