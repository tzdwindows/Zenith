package com.zenith.render.backend.opengl.shader;

import com.zenith.common.math.Color;
import com.zenith.render.backend.opengl.GLLight;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.List;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL31.*;

public class AnimationShader extends GLShader {

    private static final int MAX_LIGHTS = 16;
    private final List<GLLight> lights = new ArrayList<>();
    public static final boolean FORCE_DEBUG_VISUALS = false;

    private static String getVertexSource() {
        return "#version 330 core\n" +
                "layout (location = 0) in vec3 aPos;\n" +
                "layout (location = 1) in vec2 aTexCoord;\n" +
                "layout (location = 2) in vec3 aNormal;\n" +
                "layout (location = 3) in ivec4 aBoneIDs;\n" +
                "layout (location = 4) in vec4 aWeights;\n" +
                "layout (location = 5) in vec4 aColor;\n" + // 增加颜色属性支持
                "\n" +
                "uniform mat4 u_ViewProjection;\n" +
                "uniform mat4 u_Model;\n" +
                "\n" +
                "// 使用 UBO 优化骨骼矩阵传输\n" +
                "layout (std140) uniform BoneBlock {\n" +
                "    mat4 u_JointMatrices[100];\n" +
                "};\n" +
                "\n" +
                "out vec2 vTexCoord;\n" +
                "out vec3 vNormal;\n" +
                "out vec3 vWorldPos;\n" +
                "out vec4 vColor;\n" +
                "out vec4 vDebugColor;\n" +
                "\n" +
                "void main() {\n" +
                "    mat4 skinMat = mat4(0.0);\n" +
                "    float totalWeight = aWeights.x + aWeights.y + aWeights.z + aWeights.w;\n" +
                "\n" +
                "    // 骨骼混合逻辑\n" +
                "    for(int i = 0; i < 4; i++) {\n" +
                "        int id = aBoneIDs[i];\n" +
                "        if(id >= 0 && id < 100) {\n" +
                "            skinMat += u_JointMatrices[id] * aWeights[i];\n" +
                "        }\n" +
                "    }\n" +
                "\n" +
                "    // 如果没有权重，则不进行位移（fallback）\n" +
                "    if (totalWeight < 0.01) {\n" +
                "        skinMat = mat4(1.0);\n" +
                "        vDebugColor = vec4(1.0, 0.0, 0.0, 1.0);\n" +
                "    } else {\n" +
                "        float hue = float(aBoneIDs.x) * 0.15;\n" +
                "        vDebugColor = vec4(abs(sin(hue)), abs(sin(hue + 2.0)), abs(sin(hue + 4.0)), 1.0);\n" +
                "    }\n" +
                "\n" +
                "    vec4 worldPos = u_Model * skinMat * vec4(aPos, 1.0);\n" +
                "    vWorldPos = worldPos.xyz;\n" +
                "    vTexCoord = aTexCoord;\n" +
                "    vColor = aColor;\n" +
                "    // 法线必须经过模型和骨骼变换\n" +
                "    vNormal = mat3(u_Model * skinMat) * aNormal;\n" +
                "    \n" +
                "    gl_Position = u_ViewProjection * worldPos;\n" +
                "}\n";
    }

    private static String getFragmentSource() {
        return "#version 330 core\n" +
                "\n" +
                "struct PBRParams {\n" +
                "    vec3  diffuseColor;\n" +
                "    vec3  f0;\n" +
                "    float roughness;\n" +
                "    float metallic;\n" +
                "};\n" +
                "\n" +
                "struct Light {\n" +
                "    int type;\n" +
                "    vec3 position;\n" +
                "    vec3 direction;\n" +
                "    vec4 color;\n" +
                "    float intensity;\n" +
                "    float range;\n" +
                "    float innerCutOff;\n" +
                "    float outerCutOff;\n" +
                "    float ambientStrength;\n" +
                "};\n" +
                "\n" +
                "// 引入 Filament PBR 核心库\n" +
                "#include \"filament/common_math.glsl\"\n" +
                "#include \"filament/brdf.glsl\"\n" +
                "#include \"shading_indirect.glsl\"\n" +
                "#include \"surface_shading.glsl\"\n" +
                "\n" +
                "in vec2 vTexCoord;\n" +
                "in vec3 vNormal;\n" +
                "in vec3 vWorldPos;\n" +
                "in vec4 vColor;\n" +
                "in vec4 vDebugColor;\n" +
                "out vec4 FragColor;\n" +
                "\n" +
                "uniform sampler2D u_DiffuseMap;\n" +
                "uniform vec4 u_BaseColor;\n" +
                "uniform vec3 u_ViewPos;\n" +
                "uniform float u_UseTexture;\n" +
                "\n" +
                "// 自发光控制\n" +
                "uniform float u_IsEmissive;\n" +
                "uniform vec3 u_EmissiveColor;\n" +
                "uniform float u_EmissiveIntensity;\n" +
                "\n" +
                "// 多光源数据\n" +
                "uniform Light u_Lights[16];\n" +
                "uniform int u_LightCount;\n" +
                "\n" +
                "float calculateAttenuation(float dist, float range) {\n" +
                "    float distSq = dist * dist;\n" +
                "    float attenuation = 1.0 / max(distSq, 0.0001);\n" +
                "    float distOverRange = dist / max(range, 0.001);\n" +
                "    float windowing = clamp(1.0 - pow(distOverRange, 4.0), 0.0, 1.0);\n" +
                "    return attenuation * windowing * windowing;\n" +
                "}\n" +
                "\n" +
                "void main() {\n" +
                "    if (" + FORCE_DEBUG_VISUALS + ") {\n" +
                "        vec3 light = normalize(vec3(1.0, 1.0, 1.0));\n" +
                "        float d = max(dot(normalize(vNormal), light), 0.5);\n" +
                "        FragColor = vec4(vDebugColor.rgb * d, 1.0);\n" +
                "        return;\n" +
                "    }\n" +
                "\n" +
                "    vec4 texColor = (u_UseTexture > 0.5) ? texture(u_DiffuseMap, vTexCoord) : vec4(1.0);\n" +
                "    if (u_UseTexture > 0.5 && texColor.a < 0.05) discard;\n" +
                "    \n" +
                "    vec3 N = normalize(vNormal);\n" +
                "    vec3 V = normalize(u_ViewPos - vWorldPos);\n" +
                "\n" +
                "    // 1. 设置 PBR 基础材质参数\n" +
                "    // 使用 vColor.rgb 作为顶点色混合，u_BaseColor.a 兼作金属度，vColor.a 兼作粗糙度\n" +
                "    vec3 rawColor = u_BaseColor.rgb * vColor.rgb * texColor.rgb;\n" +
                "    vec3 baseColor = pow(rawColor, vec3(2.2));\n" +
                "    float mVal = clamp(u_BaseColor.a, 0.0, 1.0);\n" +
                "    float rVal = clamp(vColor.a < 0.01 ? 0.5 : vColor.a, 0.05, 1.0);\n" +
                "\n" +
                "    PBRParams pixel;\n" +
                "    pixel.diffuseColor = baseColor * (1.0 - mVal);\n" +
                "    pixel.f0 = mix(vec3(0.04), baseColor, mVal);\n" +
                "    pixel.roughness = rVal;\n" +
                "    pixel.metallic = mVal;\n" +
                "\n" +
                "    // 2. 直接光照计算\n" +
                "    vec3 Lo = evaluateIBL(pixel, N, V);\n" +
                "    vec3 ambientTotal = vec3(0.0);\n" +
                "\n" +
                "    for(int i = 0; i < u_LightCount; i++) {\n" +
                "        vec3 L;\n" +
                "        float attenuation = 1.0;\n" +
                "        \n" +
                "        if (u_Lights[i].type == 0) {\n" +
                "            L = normalize(-u_Lights[i].direction);\n" +
                "            ambientTotal += baseColor * u_Lights[i].color.rgb * u_Lights[i].ambientStrength;\n" +
                "        } else {\n" +
                "            vec3 lightVec = u_Lights[i].position - vWorldPos;\n" +
                "            float dist = length(lightVec);\n" +
                "            L = lightVec / dist;\n" +
                "            attenuation = calculateAttenuation(dist, u_Lights[i].range);\n" +
                "            \n" +
                "            if (u_Lights[i].type == 2) {\n" +
                "                float theta = dot(L, normalize(-u_Lights[i].direction));\n" +
                "                float epsilon = u_Lights[i].innerCutOff - u_Lights[i].outerCutOff;\n" +
                "                float spotEffect = clamp((theta - u_Lights[i].outerCutOff) / epsilon, 0.0, 1.0);\n" +
                "                attenuation *= spotEffect;\n" +
                "            }\n" +
                "        }\n" +
                "\n" +
                "        if (attenuation > 0.0 || u_Lights[i].type == 0) {\n" +
                "            vec3 radiance = u_Lights[i].color.rgb * u_Lights[i].intensity * attenuation;\n" +
                "            Lo += surfaceShading(pixel, L, radiance, V, N);\n" +
                "        }\n" +
                "    }\n" +
                "\n" +
                "    Lo += ambientTotal;\n" +
                "\n" +
                "    // 3. 自发光效果\n" +
                "    if (u_IsEmissive > 0.5) {\n" +
                "        vec3 emissive = u_EmissiveColor * u_EmissiveIntensity;\n" +
                "        float fresnel = pow(1.0 - max(dot(N, V), 0.0), 2.5);\n" +
                "        emissive += mix(vec3(0.0), vec3(1.5, 1.5, 1.2) * u_EmissiveIntensity, fresnel);\n" +
                "        Lo += emissive;\n" +
                "    }\n" +
                "\n" +
                "    Lo = finalizeColor(Lo);\n" +
                "    FragColor = vec4(Lo, u_BaseColor.a * texColor.a);\n" +
                "}\n";
    }

    public AnimationShader() {
        super("AnimationShader", getVertexSource(), getFragmentSource());
    }

    public void setup(Matrix4f viewProj, Matrix4f model, Color color) {
        this.bind();
        this.setUniform("u_ViewProjection", viewProj);
        this.setUniform("u_Model", model);
        this.setUniform("u_BaseColor", color);
        this.setUniform("u_DiffuseMap", 0);

        // 骨骼 UBO 绑定 (与 GLBoneBuffer 对齐)
        int blockIndex = glGetUniformBlockIndex(getRendererID_Internal(), "BoneBlock");
        if (blockIndex != GL_INVALID_INDEX) {
            glUniformBlockBinding(getRendererID_Internal(), blockIndex, 0);
        }
    }

    public void applyLights(Vector3f viewPos) {
        this.bind();
        this.setUniform("u_ViewPos", viewPos);
        int count = Math.min(lights.size(), MAX_LIGHTS);
        this.setUniform("u_LightCount", count);

        for (int i = 0; i < count; i++) {
            GLLight l = lights.get(i);
            String prefix = "u_Lights[" + i + "].";
            this.setUniform(prefix + "type", l.getType());
            this.setUniform(prefix + "position", l.getPosition());
            this.setUniform(prefix + "direction", l.getDirection());
            this.setUniform(prefix + "color", l.getColor());
            this.setUniform(prefix + "intensity", l.getIntensity());
            this.setUniform(prefix + "range", l.getRange());
            this.setUniform(prefix + "innerCutOff", l.getInnerCutOff());
            this.setUniform(prefix + "outerCutOff", l.getOuterCutOff());
            this.setUniform(prefix + "ambientStrength", l.getAmbientStrength());
        }
    }

    public void setEmissive(boolean emissive, Vector3f color, float intensity) {
        this.bind();
        this.setUniform("u_IsEmissive", emissive ? 1.0f : 0.0f);
        if (emissive) {
            this.setUniform("u_EmissiveColor", color);
            this.setUniform("u_EmissiveIntensity", intensity);
        }
    }

    public void addLight(GLLight light) { if (lights.size() < MAX_LIGHTS) lights.add(light); }
    public void clearLights() { lights.clear(); }
    public void setUseTexture(boolean use) { this.bind(); this.setUniform("u_UseTexture", use ? 1.0f : 0.0f); }
}