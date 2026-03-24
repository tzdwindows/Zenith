// ZenithEngine - SDF Primitives Utility
// 包含基础几何体公式与空间布尔运算

#ifndef SDF_PRIMITIVES_GLSL
#define SDF_PRIMITIVES_GLSL

// --- 基础几何体 (SDF Primitives) ---

// 球体 (Sphere)
float sdSphere(vec3 p, float s) {
    return length(p) - s;
}

// 盒子 (Box)
float sdBox(vec3 p, vec3 b) {
    vec3 q = abs(p) - b;
    return length(max(q, 0.0)) + min(max(q.x, max(q.y, q.z)), 0.0);
}

// 圆环 (Torus)
float sdTorus(vec3 p, vec2 t) {
    vec2 q = vec2(length(p.xz) - t.x, p.y);
    return length(q) - t.y;
}

// 圆柱体 (Cylinder)
float sdCylinder(vec3 p, float h, float r) {
    vec2 d = abs(vec2(length(p.xz), p.y)) - vec2(r, h);
    return min(max(d.x, d.y), 0.0) + length(max(d.x, 0.0));
}

// 胶囊体 (Capsule)
float sdCapsule(vec3 p, vec3 a, vec3 b, float r) {
    vec3 pa = p - a, ba = b - a;
    float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0);
    return length(pa - ba * h) - r;
}

// 平面 (Plane) - n为法线, h为偏移
float sdPlane(vec3 p, vec3 n, float h) {
    return dot(p, normalize(n)) + h;
}

// --- 布尔运算 (Boolean Operations) ---

// 并集 (Union) - 两个物体合并
float opUnion(float d1, float d2) {
    return min(d1, d2);
}

// 交集 (Intersection) - 取重叠部分
float opIntersection(float d1, float d2) {
    return max(d1, d2);
}

// 差集 (Subtraction) - 从 d1 中减去 d2
float opSubtraction(float d1, float d2) {
    return max(d1, -d2);
}

// --- 高级混合 (Smooth Operations) ---
// 用于实现类似流体、有机物互相融合的效果

// 平滑并集 (Smooth Union) - k 是融合半径，值越大融合越丝滑
float opSmoothUnion(float d1, float d2, float k) {
    float h = clamp(0.5 + 0.5 * (d2 - d1) / k, 0.0, 1.0);
    return mix(d2, d1, h) - k * h * (1.0 - h);
}

// 平滑差集 (Smooth Subtraction)
float opSmoothSubtraction(float d1, float d2, float k) {
    float h = clamp(0.5 - 0.5 * (d2 + d1) / k, 0.0, 1.0);
    return mix(d2, -d1, h) + k * h * (1.0 - h);
}

#endif