package com.zenith.render.backend.opengl.shader;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * SkyShader: 基于物理的大气散射与动态体积云天空
 */
public class SkyShader extends GLShader {

    private static final String VERTEX_SRC =
            "#version 330 core\n" +
                    "layout (location = 0) in vec3 aPos;\n" +
                    "layout (location = 1) in vec3 aNormal;\n" +
                    "layout (location = 2) in vec2 aTexCoord;\n" +
                    "\n" +
                    "uniform mat4 u_ViewProjection;\n" +
                    "\n" +
                    "out vec3 vViewDir;\n" +
                    "\n" +
                    "void main() {\n" +
                    "    vViewDir = aPos;\n" +
                    "    vec4 pos = u_ViewProjection * vec4(aPos, 1.0);\n" +
                    "    \n" +
                    "    // 强制深度值为 1.0 (远平面)，保证天空永远在最底层\n" +
                    "    gl_Position = pos.xyww;\n" +
                    "}";

    private static final String FRAGMENT_SRC =
            "#version 330 core\n" +
                    "#include \"sky.glsl\"\n" +
                    "\n" +
                    "in vec3 vViewDir;\n" +
                    "out vec4 FragColor;\n" +
                    "\n" +
                    "uniform vec3  u_SunDir;\n" +
                    "uniform float u_CloudCoverage;\n" +
                    "uniform float u_CloudSpeed;\n" +
                    "uniform float u_Time;\n" +
                    "\n" +
                    "void main() {\n" +
                    "    SkyParameters params;\n" +
                    "    params.sunDirection = normalize(u_SunDir);\n" +
                    "    params.cloudCoverage = u_CloudCoverage;\n" +
                    "    params.cloudSpeed = u_CloudSpeed;\n" +
                    "    params.time = u_Time;\n" +
                    "\n" +
                    "    vec3 skyColor = renderDynamicSky(normalize(vViewDir), params);\n" +
                    "\n" +
                    "    // 【统一】：使用与地形相同的 ACES Tone Mapping，保持世界色彩一致\n" +
                    "    vec3 mapped = skyColor * 0.8; // 曝光度调整\n" +
                    "    mapped = (mapped * (2.51 * mapped + 0.03)) / (mapped * (2.43 * mapped + 0.59) + 0.14);\n" +
                    "    \n" +
                    "    // 【关键新增】：Dithering (抖动) 消除天空渐变的色阶/光晕断层\n" +
                    "    vec2 screenUV = gl_FragCoord.xy;\n" +
                    "    float noise = fract(sin(dot(screenUV, vec2(12.9898, 78.233))) * 43758.5453);\n" +
                    "    mapped += (noise - 0.5) / 128.0; // 注入极其微弱的噪点打碎断层\n" +
                    "\n" +
                    "    // Gamma 校正\n" +
                    "    FragColor = vec4(pow(mapped, vec3(1.0/2.2)), 1.0);\n" +
                    "}";

    public SkyShader() {
        super("SkyShader", VERTEX_SRC, FRAGMENT_SRC);
    }
}