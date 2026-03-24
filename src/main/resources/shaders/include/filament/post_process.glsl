// --- Zenith Engine / Filament 后期处理库 ---

// ACES 色调映射 (让光照强的地方不刺眼，暗部有细节)
vec3 toneMapACES(vec3 x) {
    float a = 2.51;
    float b = 0.03;
    float c = 2.43;
    float d = 0.59;
    float e = 0.14;
    return clamp((x * (a * x + b)) / (x * (c * x + d) + e), 0.0, 1.0);
}

// 伽马校正 (让颜色符合显示器的物理特性)
vec3 gammaCorrect(vec3 linearColor) {
    return pow(linearColor, vec3(1.0 / 2.2));
}

// 整合输出 (这是你 Shader 最后一步调用的函数)
vec3 finalizeColor(vec3 color) {
    color = toneMapACES(color);
    color = gammaCorrect(color);
    return color;
}