package com.zenith.render.backend.opengl.shader;

import org.joml.Vector2f;

/**
 * 强大的全屏后期处理着色器
 * 支持：RGB 色差分离、动态模糊、径向模糊、暗角等效果
 */
public class ScreenShader extends GLShader {

    private static final String VERTEX_SOURCE =
            "#version 330 core\n" +
                    "layout(location = 0) in vec2 aPos;\n" +
                    "layout(location = 1) in vec2 aUV;\n" +
                    "out vec2 vUV;\n" +
                    "void main() {\n" +
                    "    vUV = aUV;\n" +
                    "    gl_Position = vec4(aPos, 0.0, 1.0);\n" +
                    "}";

    // 优化的片段着色器，支持多重效果叠加
    private static final String FRAGMENT_SOURCE =
            "#version 330 core\n" +
                    "in vec2 vUV;\n" +
                    "out vec4 FragColor;\n" +
                    "\n" +
                    "uniform sampler2D u_Texture;\n" +
                    "\n" +
                    "// RGB 分离参数\n" +
                    "uniform float u_ChromaticOffset;\n" +
                    "\n" +
                    "// 模糊模式和采样数 (Java端没有int传参，统一使用float接收并内部转换)\n" +
                    "uniform float u_BlurMode;\n" +
                    "uniform float u_BlurSamples;\n" +
                    "\n" +
                    "// 动态模糊参数\n" +
                    "uniform vec2 u_MotionDir;\n" +
                    "\n" +
                    "// 径向模糊参数\n" +
                    "uniform float u_RadialStrength;\n" +
                    "\n" +
                    "// 暗角参数\n" +
                    "uniform float u_VignetteStrength;\n" +
                    "\n" +
                    "// 基础采样函数（自带RGB分离）\n" +
                    "vec4 sampleWithChromatic(vec2 uv) {\n" +
                    "    float r = texture(u_Texture, uv + vec2(u_ChromaticOffset, 0.0)).r;\n" +
                    "    float g = texture(u_Texture, uv).g;\n" +
                    "    float b = texture(u_Texture, uv - vec2(u_ChromaticOffset, 0.0)).b;\n" +
                    "    return vec4(r, g, b, 1.0);\n" +
                    "}\n" +
                    "\n" +
                    "void main() {\n" +
                    "    vec4 finalColor = vec4(0.0);\n" +
                    "    // 加上 0.1 防止浮点数精度带来的强制转换错误\n" +
                    "    int mode = int(u_BlurMode + 0.1);\n" +
                    "    int samples = int(u_BlurSamples + 0.1);\n" +
                    "    \n" +
                    "    if (mode == 1) { // 动态模糊 (Motion Blur)\n" +
                    "        for(int i = 0; i < 30; i++) {\n" +
                    "            if (i >= samples) break;\n" +
                    "            vec2 offset = u_MotionDir * (float(i) / float(samples - 1) - 0.5);\n" +
                    "            finalColor += sampleWithChromatic(vUV + offset);\n" +
                    "        }\n" +
                    "        finalColor /= float(samples);\n" +
                    "    }\n" +
                    "    else if (mode == 2) { // 径向模糊 (Radial Blur)\n" +
                    "        vec2 dir = 0.5 - vUV;\n" +
                    "        for(int i = 0; i < 30; i++) {\n" +
                    "            if (i >= samples) break;\n" +
                    "            float scale = 1.0 - u_RadialStrength * (float(i) / float(samples - 1));\n" +
                    "            finalColor += sampleWithChromatic(vUV + dir * (1.0 - scale));\n" +
                    "        }\n" +
                    "        finalColor /= float(samples);\n" +
                    "    }\n" +
                    "    else { // 无模糊\n" +
                    "        finalColor = sampleWithChromatic(vUV);\n" +
                    "    }\n" +
                    "\n" +
                    "    // 暗角效果处理 (Vignette)\n" +
                    "    if (u_VignetteStrength > 0.0) {\n" +
                    "        float dist = distance(vUV, vec2(0.5));\n" +
                    "        float vignette = smoothstep(0.8, 0.5 - u_VignetteStrength * 0.5, dist);\n" +
                    "        finalColor.rgb *= vignette;\n" +
                    "    }\n" +
                    "\n" +
                    "    FragColor = finalColor;\n" +
                    "}";

    public ScreenShader() {
        super("ScreenShader", VERTEX_SOURCE, FRAGMENT_SOURCE);
        bind();
        resetEffects();
    }

    // ==========================================================
    // 基础 API 接口 (控制单一属性)
    // 均适配了父类 Shader 的 setUniform 重载方法
    // ==========================================================

    /**
     * 设置 RGB 色差分离强度
     * @param offset 偏移量（默认 0.0，轻微设为 0.003，重度可设为 0.01）
     */
    public void setChromaticAberration(float offset) {
        setUniform("u_ChromaticOffset", offset);
    }

    /**
     * 设置动态模糊（运动模糊）
     * @param dirX X轴模糊向量
     * @param dirY Y轴模糊向量
     * @param samples 采样数
     */
    public void setMotionBlur(float dirX, float dirY, int samples) {
        setUniform("u_BlurMode", 1.0f);
        setUniform("u_MotionDir", new Vector2f(dirX, dirY));
        setUniform("u_BlurSamples", (float) Math.min(samples, 30));
    }

    /**
     * 设置径向模糊（放射性模糊，常用于加速、冲刺）
     * @param strength 模糊强度
     * @param samples 采样数
     */
    public void setRadialBlur(float strength, int samples) {
        setUniform("u_BlurMode", 2.0f);
        setUniform("u_RadialStrength", strength);
        setUniform("u_BlurSamples", (float) Math.min(samples, 30));
    }

    /**
     * 关闭所有模糊效果
     */
    public void disableBlur() {
        setUniform("u_BlurMode", 0.0f);
    }

    /**
     * 设置屏幕边缘暗角
     * @param strength 强度 (0.0 为关闭，1.0 为极强)
     */
    public void setVignette(float strength) {
        setUniform("u_VignetteStrength", strength);
    }

    /**
     * 重置所有效果到默认状态 (无后期处理)
     */
    public void resetEffects() {
        setChromaticAberration(0.0f);
        disableBlur();
        setVignette(0.0f);
    }

    // ==========================================================
    // 高级视觉效果预设 API (组合效果)
    // ==========================================================

    /**
     * 视觉预设：【受击/严重故障效果】 (Glitch / Hit)
     */
    public void applyHitGlitchEffect(float intensity) {
        disableBlur();
        setChromaticAberration(0.015f * intensity);
        setVignette(0.8f * intensity);
    }

    /**
     * 视觉预设：【高速冲刺/爆发效果】 (Speed Dash / Boost)
     */
    public void applySpeedDashEffect(float speedIntensity) {
        setChromaticAberration(0.005f * speedIntensity);
        setRadialBlur(0.3f * speedIntensity, 15);
        setVignette(0.2f);
    }

    /**
     * 视觉预设：【快速横移/掉落效果】 (Fast Panning / Falling)
     */
    public void applyCameraPanEffect(float velocityX, float velocityY) {
        setChromaticAberration(0.002f);
        setMotionBlur(velocityX * 0.05f, velocityY * 0.05f, 12);
        setVignette(0.0f);
    }

    /**
     * 视觉预设：【眩晕/醉酒/中毒效果】 (Drunk / Dizzy)
     */
    public void applyDrunkEffect(float time, float severity) {
        float waveOffset = (float) Math.sin(time * 3.0f) * 0.008f * severity;
        setChromaticAberration(waveOffset);

        float blurX = (float) Math.cos(time * 2.0f) * 0.02f * severity;
        float blurY = (float) Math.sin(time * 1.5f) * 0.02f * severity;
        setMotionBlur(blurX, blurY, 8);

        setVignette(0.4f * severity);
    }
}