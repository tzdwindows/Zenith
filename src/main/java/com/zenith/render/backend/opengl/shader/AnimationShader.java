package com.zenith.render.backend.opengl.shader;

import com.zenith.common.math.Color;
import com.zenith.render.backend.opengl.GLLight;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL31.*;

public class AnimationShader extends GLShader {

    private static final int MAX_LIGHTS = 16;
    private final List<GLLight> lights = new ArrayList<>();

    // 用于驱动水面波动
    private static float totalTime = 0.0f;

    // =========================
    // Vertex Shader (支持骨骼动画与基础属性传递)
    // =========================
    private static String getVertexSource() {
        return "#version 450 core\n" +
                "layout (location = 0) in vec3 aPos;\n" +
                "layout (location = 1) in vec2 aTexCoord;\n" +
                "layout (location = 2) in vec3 aNormal;\n" +
                "layout (location = 3) in ivec4 aBoneIDs;\n" +
                "layout (location = 4) in vec4 aWeights;\n" +
                "layout (location = 5) in vec4 aColor;\n" +

                "uniform mat4 u_ViewProjection;\n" +
                "uniform mat4 u_Model;\n" +

                "layout (std140) uniform BoneBlock {\n" +
                "    mat4 u_JointMatrices[100];\n" +
                "};\n" +

                "out vec2 vTexCoord;\n" +
                "out vec3 vNormal;\n" +
                "out vec3 vWorldPos;\n" +
                "out vec4 vColor;\n" +

                "void main() {\n" +
                "    mat4 skinMat = mat4(0.0);\n" +
                "    for(int i = 0; i < 4; i++) {\n" +
                "        int id = aBoneIDs[i];\n" +
                "        if(id >= 0 && id < 100) {\n" +
                "            skinMat += u_JointMatrices[id] * aWeights[i];\n" +
                "        }\n" +
                "    }\n" +
                "    float totalWeight = aWeights.x + aWeights.y + aWeights.z + aWeights.w;\n" +
                "    if (totalWeight < 0.01) skinMat = mat4(1.0);\n" +

                "    vec4 worldPos = u_Model * skinMat * vec4(aPos, 1.0);\n" +
                "    vWorldPos = worldPos.xyz;\n" +
                "    vTexCoord = aTexCoord;\n" +
                "    vColor = aColor;\n" +

                "    mat3 normalMatrix = transpose(inverse(mat3(u_Model * skinMat)));\n" +
                "    vNormal = normalMatrix * aNormal;\n" +

                "    gl_Position = u_ViewProjection * worldPos;\n" +
                "}\n";
    }

    // =========================
    // Fragment Shader (3A 级画质核心)
    // =========================
    private static String getFragmentSource() {
        return "#version 450 core\n" +
                "#define PI 3.14159265359\n" +

                "struct PBRParams {\n" +
                "    vec3  diffuseColor;\n" +
                "    vec3  f0;\n" +
                "    float roughness;\n" +
                "    float metallic;\n" +
                "};\n" +

                "#include \"common_math.glsl\"\n" +
                "#include \"brdf.glsl\"\n" +
                "#include \"surface_shading.glsl\"\n" +
                "#include \"lighting.glsl\"\n" +

                "in vec2 vTexCoord;\n" +
                "in vec3 vNormal;\n" +
                "in vec3 vWorldPos;\n" +
                "in vec4 vColor;\n" +
                "out vec4 FragColor;\n" +

                "uniform sampler2D u_DiffuseMap;\n" +
                "uniform vec4 u_BaseColor;\n" +
                "uniform vec3 u_ViewPos;\n" +
                "uniform float u_UseTexture;\n" +
                "uniform float u_Time;\n" +

                "uniform float u_IsEmissive;\n" +
                "uniform vec3 u_EmissiveColor;\n" +
                "uniform float u_EmissiveIntensity;\n" +

                "// ⭐ 必须通过 Java 传入，否则颜色对不上\n" +
                "uniform vec3 u_SunDir;\n" +
                "uniform vec3 u_SunColor;\n" +

                "// 改进的天空计算：加入夜间衰减\n" +
                "vec3 evaluateSky(vec3 rd) {\n" +
                "    vec3 L = normalize(u_SunDir);\n" +
                "    float cosTheta = dot(rd, L);\n" +
                "    \n" +
                "    // 根据太阳 Y 轴高度计算亮度系数 (0.0 表示地平线以下)\n" +
                "    float sunHeight = smoothstep(-0.2, 0.2, L.y);\n" +
                "    \n" +
                "    vec3 skyHorizon = vec3(0.5, 0.6, 0.75);\n" +
                "    vec3 skyZenith = vec3(0.05, 0.15, 0.4);\n" +
                "    \n" +
                "    // 晚上天空会变深蓝色/黑色\n" +
                "    vec3 skyColor = mix(skyHorizon * 0.1, skyZenith, clamp(rd.y, 0.0, 1.0)) * sunHeight;\n" +
                "    \n" +
                "    float sunDisk = smoothstep(0.9990, 0.9999, cosTheta) * sunHeight;\n" +
                "    float sunGlow = pow(max(cosTheta, 0.0), 100.0) * 0.1 * sunHeight;\n" +
                "    \n" +
                "    return skyColor + u_SunColor * (sunDisk + sunGlow);\n" +
                "}\n" +

                "vec3 getWaterNormal(vec3 p, vec3 origN) {\n" +
                "    float time = u_Time * 1.5;\n" +
                "    float wave1 = sin(p.x * 2.0 + time) * 0.03;\n" +
                "    float wave2 = cos(p.z * 1.8 - time * 0.8) * 0.02;\n" +
                "    return normalize(vec3(-wave1, 1.0, -wave2));\n" +
                "}\n" +

                "void main() {\n" +
                "    vec4 texColor = (u_UseTexture > 0.5) ? texture(u_DiffuseMap, vTexCoord) : vec4(1.0);\n" +
                "    if (u_UseTexture > 0.5 && texColor.a < 0.05) discard;\n" +

                "    vec3 V = normalize(u_ViewPos - vWorldPos);\n" +
                "    vec3 N = normalize(vNormal);\n" +

                "    // 判定是否是水(根据你的截图，模型Alpha通常是1.0)\n" +
                "    bool isWater = (u_BaseColor.a < 0.6);\n" +
                "    if(isWater) N = getWaterNormal(vWorldPos, N);\n" +

                "    vec3 rawColor = u_BaseColor.rgb * texColor.rgb;\n" +
                "    vec3 baseColor = pow(max(rawColor, 0.0), vec3(2.2));\n" +

                "    float metallic = clamp(u_BaseColor.a, 0.0, 1.0);\n" +
                "    float roughness = isWater ? 0.02 : 0.4; \n" +

                "    PBRParams pixel;\n" +
                "    pixel.diffuseColor = baseColor * (1.0 - metallic);\n" +
                "    pixel.f0 = mix(vec3(0.04), baseColor, metallic);\n" +
                "    pixel.roughness = roughness;\n" +
                "    pixel.metallic = metallic;\n" +

                "    // 1. 直接光 (这里受 Java 层的 sunLight.setIntensity 影响)\n" +
                "    vec3 Lo = evaluateLights(pixel, N, V, vWorldPos);\n" +

                "    // 2. 动态环境光 (解决发光问题的关键)\n" +
                "    vec3 R = reflect(-V, N);\n" +
                "    vec3 skyRadiance = evaluateSky(N);\n" +
                "    vec3 reflectionRadiance = evaluateSky(R);\n" +
                "    \n" +
                "    // 环境漫反射 + 环境镜面反射 (带菲涅尔)\n" +
                "    vec3 ambient = skyRadiance * 0.2 * baseColor;\n" +
                "    vec3 F = pixel.f0 + (1.0 - pixel.f0) * pow(1.0 - max(dot(N, V), 0.0), 5.0);\n" +
                "    vec3 reflection = reflectionRadiance * F * (1.0 - roughness);\n" +
                "    \n" +
                "    Lo += ambient + reflection;\n" +

                "    if (u_IsEmissive > 0.5) Lo += u_EmissiveColor * u_EmissiveIntensity;\n" +

                "    // ACES Tone Mapping\n" +
                "    Lo = (Lo * (2.51 * Lo + 0.03)) / (Lo * (2.43 * Lo + 0.59) + 0.14);\n" +
                "    FragColor = vec4(pow(max(Lo, 0.0), vec3(1.0/2.2)), texColor.a * u_BaseColor.a);\n" +
                "}\n";
    }

    public AnimationShader() {
        super("AnimationShader", getVertexSource(), getFragmentSource());
    }

    public void updateTime(float dt) {
        totalTime += dt;
    }

    public void setup(Matrix4f viewProj, Matrix4f model, Color color) {
        this.bind();
        this.setUniform("u_ViewProjection", viewProj);
        this.setUniform("u_Model", model);
        this.setUniform("u_BaseColor", new Vector4f(color.r, color.g, color.b, color.a));
        this.setUniform("u_DiffuseMap", 0);
        this.setUniform("u_Time", totalTime);

        int blockIndex = glGetUniformBlockIndex(getRendererID_Internal(), "BoneBlock");
        if (blockIndex != GL_INVALID_INDEX) {
            glUniformBlockBinding(getRendererID_Internal(), blockIndex, 0);
        }
    }

    public void applyLights(Vector3f viewPos) {
        this.bind();
        this.setUniform("u_ViewPos", viewPos != null ? viewPos : new Vector3f(0));

        int count = Math.min(lights.size(), MAX_LIGHTS);
        this.setUniform("u_LightCount", count);

        for (int i = 0; i < count; i++) {
            GLLight l = lights.get(i);
            String prefix = "u_Lights[" + i + "].";
            this.setUniform(prefix + "type", l.getType());
            this.setUniform(prefix + "position", l.getPosition() != null ? l.getPosition() : new Vector3f(0));
            this.setUniform(prefix + "direction", l.getDirection() != null ? l.getDirection() : new Vector3f(0, -1, 0));
            Color c = l.getColor();
            this.setUniform(prefix + "color", new Vector4f(c.r, c.g, c.b, c.a));
            this.setUniform(prefix + "intensity", l.getIntensity());
            this.setUniform(prefix + "range", l.getRange() > 0 ? l.getRange() : 1000.0f);
            this.setUniform(prefix + "innerCutOff", (float)Math.cos(Math.toRadians(l.getInnerCutOff())));
            this.setUniform(prefix + "outerCutOff", (float)Math.cos(Math.toRadians(l.getOuterCutOff())));
            this.setUniform(prefix + "ambientStrength", l.getAmbientStrength());
        }
    }

    // --- 保留原有 API 接口 ---

    public void setEmissive(boolean emissive, Vector3f color, float intensity) {
        this.bind();
        this.setUniform("u_IsEmissive", emissive ? 1.0f : 0.0f);
        if (emissive) {
            this.setUniform("u_EmissiveColor", color);
            this.setUniform("u_EmissiveIntensity", intensity);
        }
    }

    public void setUseTexture(boolean use) {
        this.bind();
        this.setUniform("u_UseTexture", use ? 1.0f : 0.0f);
    }

    public void addLight(GLLight light) {
        if (!lights.contains(light) && lights.size() < MAX_LIGHTS) {
            lights.add(light);
        }
    }

    public void clearLights() {
        lights.clear();
    }
}