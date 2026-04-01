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
    public static final boolean FORCE_DEBUG_VISUALS = false;

    // =========================
    // Vertex Shader (保持不变)
    // =========================
    private static String getVertexSource() {
        return "#version 330 core\n" +
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
                "out vec4 vDebugColor;\n" +

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
    // Fragment Shader (同步修改 float 类型)
    // =========================
    private static String getFragmentSource() {
        return "#version 330 core\n" +

                "struct PBRParams {\n" +
                "    vec3  diffuseColor;\n" +
                "    vec3  f0;\n" +
                "    float roughness;\n" +
                "    float metallic;\n" +
                "};\n" +

                // 注意：这里确保 u_LightCount 是 float，Light.type 是 float
                // 这样才能和 Java 端的传递以及你的 lighting.glsl 修改版本匹配
                "struct Light {\n" +
                "    float type;\n" +
                "    vec3 position;\n" +
                "    vec3 direction;\n" +
                "    vec4 color;\n" +
                "    float intensity;\n" +
                "    float range;\n" +
                "    float innerCutOff;\n" +
                "    float outerCutOff;\n" +
                "    float ambientStrength;\n" +
                "};\n" +
                "uniform Light u_Lights[16];\n" +
                "uniform float u_LightCount;\n" + // 改为 float

                "#include \"common_math.glsl\"\n" +
                "#include \"brdf.glsl\"\n" +
                "#include \"shading_indirect.glsl\"\n" +
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

                "uniform float u_IsEmissive;\n" +
                "uniform vec3 u_EmissiveColor;\n" +
                "uniform float u_EmissiveIntensity;\n" +

                "void main() {\n" +
                "    vec4 texColor = (u_UseTexture > 0.5) ? texture(u_DiffuseMap, vTexCoord) : vec4(1.0);\n" +
                "    if (u_UseTexture > 0.5 && texColor.a < 0.05) discard;\n" +

                "    vec3 N = normalize(vNormal);\n" +
                "    vec3 V = normalize(u_ViewPos - vWorldPos);\n" +

                "    vec3 rawColor = u_BaseColor.rgb * texColor.rgb;\n" +
                "    vec3 baseColor = pow(rawColor, vec3(2.2));\n" +

                "    float metallic = clamp(u_BaseColor.a, 0.0, 1.0);\n" +
                "    float roughness = 0.5;\n" +

                "    PBRParams pixel;\n" +
                "    pixel.diffuseColor = baseColor * (1.0 - metallic);\n" +
                "    pixel.f0 = mix(vec3(0.04), baseColor, metallic);\n" +
                "    pixel.roughness = roughness;\n" +
                "    pixel.metallic = metallic;\n" +

                "    vec3 Lo = evaluateIBL(pixel, N, V);\n" +
                "    Lo += evaluateLights(pixel, N, V, vWorldPos);\n" +

                "    if (u_IsEmissive > 0.5) {\n" +
                "        Lo += u_EmissiveColor * u_EmissiveIntensity;\n" +
                "    }\n" +

                "    Lo = finalizeColor(Lo);\n" +
                "    FragColor = vec4(Lo, texColor.a * u_BaseColor.a);\n" +
                "}\n";
    }

    public AnimationShader() {
        super("AnimationShader", getVertexSource(), getFragmentSource());
    }

    public void setup(Matrix4f viewProj, Matrix4f model, Color color) {
        this.bind();
        this.setUniform("u_ViewProjection", viewProj);
        this.setUniform("u_Model", model);
        this.setUniform("u_BaseColor", new Vector4f(color.r, color.g, color.b, color.a));

        this.setUniform("u_DiffuseMap", 0);
        this.setUniform("u_iblSpecular", 1);
        this.setUniform("u_dfgLUT", 2);

        int blockIndex = glGetUniformBlockIndex(getRendererID_Internal(), "BoneBlock");
        if (blockIndex != GL_INVALID_INDEX) {
            glUniformBlockBinding(getRendererID_Internal(), blockIndex, 0);
        }
    }

    /**
     * 核心修复：应用光照数据
     */
    public void applyLights(Vector3f viewPos) {
        this.bind();
        this.setUniform("u_ViewPos", viewPos != null ? viewPos : new Vector3f(0));

        // 修复 1：将数量作为 float 传递，防止 GLSL int 接收失败
        float count = (float) Math.min(lights.size(), MAX_LIGHTS);
        this.setUniform("u_LightCount", count);

        for (int i = 0; i < (int)count; i++) {
            GLLight l = lights.get(i);
            String prefix = "u_Lights[" + i + "].";

            // 修复 2：将 type 转为 float
            this.setUniform(prefix + "type", (float)l.getType());

            // 修复 3：空值保护
            this.setUniform(prefix + "position", l.getPosition() != null ? l.getPosition() : new Vector3f(0));
            this.setUniform(prefix + "direction", l.getDirection() != null ? l.getDirection() : new Vector3f(0, -1, 0));

            // 修复 4：将 Color 对象显式转为 Vector4f
            Color c = l.getColor();
            this.setUniform(prefix + "color", new Vector4f(c.r, c.g, c.b, c.a));

            this.setUniform(prefix + "intensity", l.getIntensity());
            this.setUniform(prefix + "range", l.getRange() > 0 ? l.getRange() : 1000.0f);
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

    public void addLight(GLLight light) {
        if (lights.size() < MAX_LIGHTS) lights.add(light);
    }

    public void clearLights() {
        lights.clear();
    }

    public void setUseTexture(boolean use) {
        this.bind();
        this.setUniform("u_UseTexture", use ? 1.0f : 0.0f);
    }
}