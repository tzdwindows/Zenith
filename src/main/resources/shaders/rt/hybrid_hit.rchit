#version 460 core
#extension GL_NV_ray_tracing : require

// 定义物理灯光
struct Light {
    vec3 position;
    vec3 color;
    float intensity;
};

// 顶点结构 (必须与 Java 端 12-float Stride 严格对齐)
struct Vertex {
    vec4 pos;    // xyz, w=1
    vec4 norm;   // xyz, w=0
    vec4 tex;    // uv, zw=0
};

// ------------------------------------------------------------
// 资源绑定
// ------------------------------------------------------------
layout(binding = 1) uniform accelerationStructureNV topLevelAS;
layout(std430, binding = 2) buffer VertexBuffer { Vertex vertices[]; };
layout(std430, binding = 4) buffer LightBuffer  { Light lights[]; };

// 有效载荷 (Payload)
// location 0: 用于主射线颜色传递
layout(location = 0) rayPayloadInNV vec3 hitValue;
// location 1: 用于阴影射线 (只读 bool 即可)
layout(location = 1) rayPayloadNV bool isShadowed;

// 硬件提供的重心坐标
hitAttributeNV vec2 bary;

// ------------------------------------------------------------
// PBR 物理数学库 (微缩版)
// ------------------------------------------------------------
const float PI = 3.14159265359;

float D_GGX(float NoH, float roughness) {
    float a = roughness * roughness;
    float a2 = a * a;
    float NoH2 = NoH * NoH;
    float d = NoH2 * (a2 - 1.0) + 1.0;
    return a2 / (PI * d * d);
}

float G_SchlickGGX(float NoV, float k) {
    return NoV / (NoV * (1.0 - k) + k);
}

vec3 F_Schlick(float cosTheta, vec3 F0) {
    return F0 + (1.0 - F0) * pow(1.0 - cosTheta, 5.0);
}

// ------------------------------------------------------------
// 主逻辑
// ------------------------------------------------------------
void main() {
    // 1. 获取三角形数据
    int primitiveID = gl_PrimitiveID;
    Vertex v0 = vertices[primitiveID * 3 + 0];
    Vertex v1 = vertices[primitiveID * 3 + 1];
    Vertex v2 = vertices[primitiveID * 3 + 2];

    // 2. 重心坐标插值 (还原法线与世界坐标)
    float w = 1.0 - bary.x - bary.y;
    vec3 worldNormal = normalize(v0.norm.xyz * w + v1.norm.xyz * bary.x + v2.norm.xyz * bary.y);
    vec3 worldPos = gl_WorldRayOriginNV + gl_WorldRayDirectionNV * gl_HitTNV;

    // 3. 设定 PBR 材质属性 (实际开发中应从 Texture/MaterialBuffer 读取)
    vec3 albedo = vec3(0.8, 0.7, 0.6); // 类似沙土的颜色
    float roughness = 0.3;
    float metallic = 0.0;
    vec3 F0 = mix(vec3(0.04), albedo, metallic);

    vec3 V = normalize(-gl_WorldRayDirectionNV);
    vec3 Lo = vec3(0.0);

    // 4. 遍历灯光 (工业级通常支持多光源)
    for(int i = 0; i < 1; i++) { // 演示仅用一个灯
        vec3 L = normalize(lights[i].position - worldPos);
        vec3 H = normalize(V + L);
        float dist = length(lights[i].position - worldPos);

        // --- 硬件级递归阴影探测 (Shadow Ray) ---
        isShadowed = true;
        uint flags = gl_RayFlagsTerminateOnFirstHitNV | gl_RayFlagsOpaqueNV | gl_RayFlagsSkipClosestHitShaderNV;

        // 发射阴影射线到 location 1
        traceNV(topLevelAS, flags, 0xFF, 0, 0, 1, worldPos, 0.01, L, dist, 1);

        if (!isShadowed) {
            // 如果未被遮挡，计算物理光照
            float NoV = max(dot(worldNormal, V), 0.001);
            float NoL = max(dot(worldNormal, L), 0.001);
            float NoH = max(dot(worldNormal, H), 0.0);
            float HoV = max(dot(H, V), 0.0);

            float D = D_GGX(NoH, roughness);
            float G = G_SchlickGGX(NoV, roughness) * G_SchlickGGX(NoL, roughness);
            vec3  F = F_Schlick(HoV, F0);

            vec3 spec = (D * G * F) / (4.0 * NoV * NoL + 0.001);
            vec3 kD = (vec3(1.0) - F) * (1.0 - metallic);

            Lo += (kD * albedo / PI + spec) * lights[i].color * lights[i].intensity * NoL;
        }
    }

    // 5. 最终着色结果写入 Payload 传回 RayGen
    hitValue = Lo + albedo * 0.05; // 0.05 是基础环境光
}