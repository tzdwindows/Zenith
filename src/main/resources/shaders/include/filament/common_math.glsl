#ifndef ZENITH_COMMON_MATH_GLSL
#define ZENITH_COMMON_MATH_GLSL

#define highp

#ifndef PI
#define PI 3.14159265359
#endif

#define HALF_PI 1.570796327
#define FLT_EPS 1e-5

#define PREVENT_DIV0(n, d) ((n) / (max(d, 0.00001)))

float sq(float x) {
    return x * x;
}

float max3(const vec3 v) {
    return max(v.x, max(v.y, v.z));
}

float vmax(const vec2 v) { return max(v.x, v.y); }
float vmax(const vec3 v) { return max(v.x, max(v.y, v.z)); }
float vmax(const vec4 v) { return max(max(v.x, v.y), max(v.z, v.w)); }

float min3(const vec3 v) {
    return min(v.x, min(v.y, v.z));
}

float vmin(const vec2 v) { return min(v.x, v.y); }
float vmin(const vec3 v) { return min(v.x, min(v.y, v.z)); }
float vmin(const vec4 v) { return min(min(v.x, v.y), min(v.z, v.w)); }

vec3 toneMapACES(vec3 x) {
    const float a = 2.51;
    const float b = 0.03;
    const float c = 2.43;
    const float d = 0.59;
    const float e = 0.14;
    vec3 res = (x * (a * x + b)) / (x * (c * x + d) + e);
    return clamp(res, 0.0, 1.0);
}

vec3 gammaCorrect(vec3 linearColor) {
    return pow(linearColor, vec3(1.0 / 2.2));
}

vec3 finalizeColor(vec3 color) {
    color = toneMapACES(color);
    color = gammaCorrect(color);
    return color;
}

float acosFast(float x) {
    float y = abs(x);
    float p = -0.1565827 * y + 1.570796;
    p *= sqrt(1.0 - y);
    return x >= 0.0 ? p : PI - p;
}

    highp vec4 mulMat4x4Float3(const highp mat4 m, const highp vec3 v) {
    return v.x * m[0] + (v.y * m[1] + (v.z * m[2] + m[3]));
}

float interleavedGradientNoise(highp vec2 w) {
    const vec3 m = vec3(0.06711056, 0.00583715, 52.9829189);
    return fract(m.z * fract(dot(w, m.xy)));
}

#endif