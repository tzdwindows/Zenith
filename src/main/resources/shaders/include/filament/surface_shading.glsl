#ifndef SURFACE_SHADING_GLSL
#define SURFACE_SHADING_GLSL

#include "common_math.glsl"
#include "brdf.glsl"

vec3 isotropicLobe(PBRParams pixel, vec3 n, vec3 v, vec3 l, vec3 h) {
    float NoH = saturate(dot(n, h));
    float NoV = saturate(dot(n, v));
    float NoL = saturate(dot(n, l));
    float LoH = saturate(dot(l, h));

    float D = D_GGX(pixel.roughness, NoH);
    float V = V_SmithGGXCorrelated(pixel.roughness, NoV, NoL);
    vec3  F = F_Schlick(pixel.f0, LoH);

    return (D * V) * F;
}

vec3 diffuseLobe(PBRParams pixel, float NoV, float NoL, float LoH) {
    return pixel.diffuseColor * Fd_Burley(pixel.roughness, NoV, NoL, LoH);
}

vec3 surfaceShading(PBRParams pixel, vec3 L, vec3 lightColor, vec3 V, vec3 N) {
    vec3 H = normalize(V + L);
    float NoL = saturate(dot(N, L));
    float NoV = saturate(dot(N, V));
    float LoH = saturate(dot(L, H));

    vec3 Fr = isotropicLobe(pixel, N, V, L, H);
    vec3 Fd = diffuseLobe(pixel, NoV, NoL, LoH);

    return (Fd + Fr) * lightColor * NoL;
}

#endif