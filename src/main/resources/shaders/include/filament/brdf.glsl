#ifndef FILAMENT_BRDF_GLSL
#define FILAMENT_BRDF_GLSL

#ifndef PI
#define PI 3.1415926535897932384626433832795
#endif

float pow5(float x) {
    float x2 = x * x;
    return x2 * x2 * x;
}

float saturate(float x) {
    return clamp(x, 0.0, 1.0);
}

float D_GGX(float roughness, float NoH) {
    float oneMinusNoHSquared = 1.0 - NoH * NoH;
    float a = NoH * roughness;
    float k = roughness / (oneMinusNoHSquared + a * a);
    float d = k * (k * (1.0 / PI));
    return d;
}

float V_SmithGGXCorrelated(float roughness, float NoV, float NoL) {
    float a2 = roughness * roughness;
    float lambdaV = NoL * sqrt((NoV - a2 * NoV) * NoV + a2);
    float lambdaL = NoV * sqrt((NoL - a2 * NoL) * NoL + a2);
    float v = 0.5 / (lambdaV + lambdaL);
    return v;
}

vec3 F_Schlick(const vec3 f0, float f90, float VoH) {
    return f0 + (f90 - f0) * pow5(1.0 - VoH);
}

vec3 F_Schlick(const vec3 f0, float VoH) {
    float f = pow(1.0 - VoH, 5.0);
    return f + f0 * (1.0 - f);
}

float Fd_Lambert() {
    return 1.0 / PI;
}

float Fd_Burley(float roughness, float NoV, float NoL, float LoH) {
    float f90 = 0.5 + 2.0 * roughness * LoH * LoH;
    float lightScatter = F_Schlick(vec3(1.0), f90, NoL).r;
    float viewScatter  = F_Schlick(vec3(1.0), f90, NoV).r;
    return lightScatter * viewScatter * (1.0 / PI);
}

#endif