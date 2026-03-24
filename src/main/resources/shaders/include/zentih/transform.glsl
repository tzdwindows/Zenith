// ZenithEngine - Shader Transform Utility
// 用于在 Shader 内部进行几何空间变换

#ifndef TRANSFORM_GLSL
#define TRANSFORM_GLSL

// --- 旋转 (Rotation) ---

// 绕 X 轴旋转
vec3 rotateX(vec3 p, float theta) {
    float c = cos(theta);
    float s = sin(theta);
    return vec3(p.x, c * p.y - s * p.z, s * p.y + c * p.z);
}

// 绕 Y 轴旋转
vec3 rotateY(vec3 p, float theta) {
    float c = cos(theta);
    float s = sin(theta);
    return vec3(c * p.x + s * p.z, p.y, -s * p.x + c * p.z);
}

// 绕 Z 轴旋转
vec3 rotateZ(vec3 p, float theta) {
    float c = cos(theta);
    float s = sin(theta);
    return vec3(c * p.x - s * p.y, s * p.x + c * p.y, p.z);
}

// 绕任意轴旋转 (Rodrigues' rotation formula)
vec3 rotateAxis(vec3 p, vec3 axis, float theta) {
    axis = normalize(axis);
    float c = cos(theta);
    float s = sin(theta);
    return p * c + cross(axis, p) * s + axis * dot(axis, p) * (1.0 - c);
}

// 二维旋转 (常用于贴图坐标 UV 旋转)
vec2 rotate2D(vec2 p, float theta) {
    float c = cos(theta);
    float s = sin(theta);
    return vec2(c * p.x - s * p.y, s * p.x + c * p.y);
}

// --- 空间映射 (Spatial Mapping) ---

// 缩放
vec3 scale(vec3 p, vec3 s) {
    return p / s;
}

// 平移
vec3 translate(vec3 p, vec3 t) {
    return p - t;
}

// 重复空间 (用于创建无限重复的小行星群或星空)
// p: 当前坐标, c: 重复周期大小
vec3 repeat(vec3 p, vec3 c) {
    return mod(p + 0.5 * c, c) - 0.5 * c;
}

// --- 坐标系转换 (Coordinate Conversion) ---

// 直角坐标转极坐标 (用于环绕灯光或圆柱形效果)
// 返回: vec3(radius, phi, theta)
vec3 cartesianToSpherical(vec3 p) {
    float r = length(p);
    float theta = acos(p.z / r);
    float phi = atan(p.y, p.x);
    return vec3(r, phi, theta);
}

#endif