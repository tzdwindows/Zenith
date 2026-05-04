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
            "#version 450 core\n" +
                    "in vec2 vUV;\n" +
                    "out vec4 FragColor;\n" +
                    "uniform sampler2D u_Texture;\n" +
                    "uniform float u_Time;\n" +

                    "// 3A 级后期参数\n" +
                    "uniform float u_Exposure = 1.0;\n" +
                    "uniform float u_Contrast = 1.1;\n" +
                    "uniform float u_Saturation = 1.1;\n" +
                    "uniform float u_VignetteStrength = 0.3;\n" +
                    "uniform float u_ChromaticOffset = 0.0015;\n" +
                    "uniform float u_Sharpness = 0.4; // 锐化\n" +
                    "uniform float u_GrainAmount = 0.05; // 颗粒感\n" +

                    "// 锐化采样\n" +
                    "vec3 getSharpened(vec2 uv) {\n" +
                    "    vec2 res = 1.0 / textureSize(u_Texture, 0);\n" +
                    "    vec3 center = texture(u_Texture, uv).rgb;\n" +
                    "    vec3 left   = texture(u_Texture, uv + vec2(-res.x, 0)).rgb;\n" +
                    "    vec3 right  = texture(u_Texture, uv + vec2(res.x, 0)).rgb;\n" +
                    "    vec3 up     = texture(u_Texture, uv + vec2(0, res.y)).rgb;\n" +
                    "    vec3 down   = texture(u_Texture, uv + vec2(0, -res.y)).rgb;\n" +
                    "    return center + u_Sharpness * (center * 4.0 - left - right - up - down);\n" +
                    "}\n" +

                    "// 伪随机函数用于产生颗粒\n" +
                    "float hash(vec2 p) { return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453); }\n" +

                    "void main() {\n" +
                    "    // 1. 带色差的基础采样与锐化混合\n" +
                    "    vec3 color;\n" +
                    "    color.r = texture(u_Texture, vUV + vec2(u_ChromaticOffset, 0)).r;\n" +
                    "    color.g = getSharpened(vUV).g;\n" +
                    "    color.b = texture(u_Texture, vUV - vec2(u_ChromaticOffset, 0)).b;\n" +

                    "    // 2. 曝光补偿\n" +
                    "    color *= u_Exposure;\n" +

                    "    // 3. 对比度与饱和度调整\n" +
                    "    color = (color - 0.5) * u_Contrast + 0.5;\n" +
                    "    float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));\n" +
                    "    color = mix(vec3(luma), color, u_Saturation);\n" +

                    "    // 4. 暗角效果\n" +
                    "    float dist = distance(vUV, vec2(0.5));\n" +
                    "    color *= smoothstep(0.8, 0.5 - u_VignetteStrength * 0.5, dist);\n" +

                    "    // 5. 胶片颗粒感 (增加 3A 材质感)\n" +
                    "    float noise = hash(vUV + fract(u_Time));\n" +
                    "    color += (noise - 0.5) * u_GrainAmount;\n" +

                    "    FragColor = vec4(max(color, 0.0), 1.0);\n" +
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