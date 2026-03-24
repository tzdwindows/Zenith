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
                    "    // 移除 u_Model，天空盒只需要 ViewProjection 即可\n" +
                    "    vec4 pos = u_ViewProjection * vec4(aPos, 1.0);\n" +
                    "    \n" +
                    "    // 强制深度值为 1.0 (远平面)\n" +
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
                    "    // Tone Mapping 与 Gamma 校正\n" +
                    "    skyColor = vec3(1.0) - exp(-skyColor * 1.5);\n" +
                    "    FragColor = vec4(pow(skyColor, vec3(1.0/2.2)), 1.0);\n" +
                    "}";

    public SkyShader() {
        super("SkyShader", VERTEX_SRC, FRAGMENT_SRC);
    }
}