//------------------------------------------------------------------------------
// Zenith Engine - Indirect Light (IBL) Evaluation
// 基于 Filament surface_light_indirect.fs 简化
//------------------------------------------------------------------------------

#ifndef SHADING_INDIRECT_GLSL
#define SHADING_INDIRECT_GLSL

#include "filament/common_math.glsl"
#include "filament/brdf.glsl"

uniform samplerCube u_iblSpecular; // 预过滤的环境贴图 (Prefiltered Cubemap)
uniform vec3 u_iblSH[9];           // 环境光球谐函数 (用于漫反射)
uniform sampler2D u_dfgLUT;        // DFG 查找表贴图
uniform float u_maxMipLevel;       // Cubemap 的最大 Mip 级别
uniform float u_iblLuminance;      // 环境光强度校正

/**
 * 间接漫反射：使用球谐函数 (SH) 模拟环境光照射
 */
vec3 evaluateDiffuseIBL(vec3 n) {
    vec3 sh = u_iblSH[0];
    sh += u_iblSH[1] * (n.y) + u_iblSH[2] * (n.z) + u_iblSH[3] * (n.x);
    sh += u_iblSH[4] * (n.y * n.x) + u_iblSH[5] * (n.y * n.z) +
    u_iblSH[6] * (3.0 * n.z * n.z - 1.0) +
    u_iblSH[7] * (n.z * n.x) + u_iblSH[8] * (n.x * n.x - n.y * n.y);
    return max(sh, 0.0);
}

/**
 * 间接镜面反射：根据粗糙度采样 Cubemap
 */
vec3 evaluateSpecularIBL(vec3 r, float roughness) {
    float lod = u_maxMipLevel * roughness * (2.0 - roughness);
    return textureLod(u_iblSpecular, r, lod).rgb;
}

/**
 * IBL 总评：结合漫反射和镜面反射，并处理 Fresnel 能量平衡
 */
vec3 evaluateIBL(PBRParams pixel, vec3 n, vec3 v) {
    float NoV = saturate(dot(n, v));
    vec3 r = reflect(-v, n);
    vec3 indirectSpecular = evaluateSpecularIBL(r, pixel.roughness);
    vec3 indirectDiffuse = evaluateDiffuseIBL(n);
    vec2 dfg = texture(u_dfgLUT, vec2(NoV, pixel.roughness)).rg;
    vec3 E = mix(vec3(dfg.xxx), vec3(dfg.yyy), pixel.f0);
    vec3 Fr = indirectSpecular * E;
    vec3 Fd = indirectDiffuse * pixel.diffuseColor * (1.0 - E);

    return (Fd + Fr) * u_iblLuminance;
}

#endif