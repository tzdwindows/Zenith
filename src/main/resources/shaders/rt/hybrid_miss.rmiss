#version 460 core
#extension GL_NV_ray_tracing : require

// location 0: 主射线没撞到，返回天空色
layout(location = 0) rayPayloadInNV vec3 hitValue;
// location 1: 阴影射线没撞到，说明没被遮挡
layout(location = 1) rayPayloadInNV bool isShadowed;

void main() {
    // 根据当前执行的 miss index 判断
    // 如果是阴影射线 miss 了：
    isShadowed = false;

    // 如果是主射线 miss 了：
    vec3 rd = gl_WorldRayDirectionNV;
    hitValue = mix(vec3(0.1, 0.2, 0.4), vec3(0.6, 0.8, 1.0), clamp(rd.y, 0.0, 1.0));
}