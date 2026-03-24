package com.zenith.render.backend.opengl.shader;

import com.zenith.common.math.Color;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * LavaShader: 修复后的 3D 动态感岩浆着色器
 */
public class LavaShader extends GLShader {

    private static final String VERTEX_SRC =
            "#version 330 core\n" +
                    "#include \"water.glsl\"\n" +
                    "\n" +
                    "layout (location = 0) in vec3 aPos;\n" +
                    "layout (location = 1) in vec3 aNormal;\n" +
                    "layout (location = 2) in vec2 aTexCoord;\n" +
                    "\n" +
                    "uniform mat4 u_ViewProjection;\n" +
                    "uniform mat4 u_Model;\n" +
                    "uniform float u_Time;\n" +
                    "\n" +
                    "out vec2 vTexCoord;\n" +
                    "out vec3 vWorldPos;\n" +
                    "out vec3 vNormal;\n" +
                    "\n" +
                    "void main() {\n" +
                    "    vTexCoord = aTexCoord * 2.0;\n" +
                    "    vec3 pos = aPos;\n" +
                    "    \n" +
                    "    // 顶点位移：让岩浆表面有起伏感\n" +
                    "    float wave = sin(pos.x * 2.0 + u_Time * 0.5) * cos(pos.z * 2.0 + u_Time * 0.5) * 0.05;\n" +
                    "    pos.y += wave;\n" +
                    "    \n" +
                    "    vec4 worldPos = u_Model * vec4(pos, 1.0);\n" +
                    "    vWorldPos = worldPos.xyz;\n" +
                    "    vNormal = mat3(u_Model) * aNormal;\n" +
                    "    gl_Position = u_ViewProjection * worldPos;\n" +
                    "}";

    private static final String FRAGMENT_SRC =
            "#version 330 core\n" +
                    "#include \"water.glsl\"\n" +
                    "\n" +
                    "// 兼容性定义：如果 common_math.glsl 没定义 saturate\n" +
                    "#ifndef saturate\n" +
                    "#define saturate(x) clamp(x, 0.0, 1.0)\n" +
                    "#endif\n" +
                    "\n" +
                    "in vec2 vTexCoord;\n" +
                    "in vec3 vWorldPos;\n" +
                    "in vec3 vNormal;\n" +
                    "out vec4 FragColor;\n" +
                    "\n" +
                    "uniform vec3 u_ViewPos;\n" +
                    "uniform float u_Time;\n" +
                    "uniform vec3 u_LavaColor;\n" +
                    "uniform float u_FlowSpeed;\n" +
                    "\n" +
                    "void main() {\n" +
                    "    vec3 V = normalize(u_ViewPos - vWorldPos);\n" +
                    "    float flowTime = u_Time * u_FlowSpeed;\n" +
                    "    \n" +
                    "    // 1. 多层噪声混合法线 (修复报错核心)\n" +
                    "    // 使用 psrdnoise 计算梯度来代替不存在的 computeNoiseNormal\n" +
                    "    vec3 g1, g2;\n" +
                    "    psrdnoise(vWorldPos * 1.2 + vec3(flowTime * 0.2, 0.0, flowTime * 0.1), vec3(0.0), 0.0, g1);\n" +
                    "    psrdnoise(vWorldPos * 2.5 - vec3(flowTime * 0.1, 0.0, -flowTime * 0.2), vec3(0.0), 0.0, g2);\n" +
                    "    \n" +
                    "    // 扰动表面法线\n" +
                    "    vec3 N = normalize(vNormal + g1 * 0.4 + g2 * 0.2);\n" +
                    "\n" +
                    "    // 2. 计算岩浆掩码 (Lava vs Crust)\n" +
                    "    vec3 grad;\n" +
                    "    // 这里的 n 是 float，修复了之前的赋值错误\n" +
                    "    float n = psrdnoise(vWorldPos * 0.6 + vec3(0.0, flowTime * 0.3, 0.0), vec3(0.0), 0.0, grad);\n" +
                    "    float lavaMask = smoothstep(-0.1, 0.4, n);\n" +
                    "    \n" +
                    "    // 3. 颜色混合\n" +
                    "    vec3 crustColor = vec3(0.02, 0.01, 0.005); // 冷却后的黑褐色岩壳\n" +
                    "    vec3 lavaBase = u_LavaColor;\n" +
                    "    \n" +
                    "    // 呼吸感脉动\n" +
                    "    float pulse = 0.9 + 0.2 * sin(u_Time * 1.5);\n" +
                    "    \n" +
                    "    // 4. 自发光计算\n" +
                    "    // lavaMask 高的部分（亮部）赋予极强的发光倍率\n" +
                    "    vec3 emissive = mix(crustColor, lavaBase, lavaMask);\n" +
                    "    emissive *= mix(1.0, 5.0, lavaMask) * pulse;\n" +
                    "    \n" +
                    "    // 5. 增强菲涅尔效应 (边缘高温感)\n" +
                    "    float NoV = saturate(dot(N, V));\n" +
                    "    float fresnel = pow(1.0 - NoV, 4.0);\n" +
                    "    emissive += lavaBase * fresnel * mix(0.5, 2.0, lavaMask);\n" +
                    "\n" +
                    "    // 6. 最终输出处理\n" +
                    "    // 如果没有 finalizeColor 函数，则使用简单的 Tonemapping 防止颜色过曝变白\n" +
                    "    // vec3 finalColor = finalizeColor(emissive);\n" +
                    "    vec3 finalColor = emissive / (emissive + vec3(1.0)); \n" +
                    "    \n" +
                    "    FragColor = vec4(finalColor, 1.0);\n" +
                    "}";

    public LavaShader() {
        super("LavaShader", VERTEX_SRC, FRAGMENT_SRC);
    }

    public void update(Matrix4f viewProj, Matrix4f model, Vector3f viewPos, float time) {
        this.bind();
        this.setUniform("u_ViewProjection", viewProj);
        this.setUniform("u_Model", model);
        this.setUniform("u_ViewPos", viewPos);
        this.setUniform("u_Time", time);
    }

    public void setParams(Vector3f baseColor, float flowSpeed) {
        this.bind();
        this.setUniform("u_LavaColor", baseColor);
        this.setUniform("u_FlowSpeed", flowSpeed);
    }
}