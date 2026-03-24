package com.zenith.render.backend.opengl.shader;

import com.zenith.asset.AssetIdentifier;
import com.zenith.asset.AssetResource;
import com.zenith.render.backend.opengl.GLLight;
import org.joml.Matrix4f;
import com.zenith.common.math.Color;
import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.List;

public class StandardShader extends GLShader {

    private static final int MAX_LIGHTS = 8;
    private final List<GLLight> lights = new ArrayList<>();

    private static final String VERTEX_SRC =
            "#version 330 core\n" +
                    "layout (location = 0) in vec3 aPos;\n" +
                    "layout (location = 1) in vec3 aNormal;\n" +
                    "layout (location = 2) in vec2 aTexCoord;\n" +
                    "layout (location = 3) in vec4 aColor;\n" +
                    "layout (location = 4) in vec4 aBoneIds;\n" +
                    "layout (location = 5) in vec4 aWeights;\n" +
                    "uniform mat4 u_ViewProjection;\n" +
                    "uniform mat4 u_Model;\n" +
                    "uniform mat4 u_Bones[100];\n" +
                    "uniform float u_HasBones;\n" +
                    "out vec2 vTexCoord;\n" +
                    "out vec4 vColor;\n" +
                    "out vec3 vWorldPos;\n" +
                    "out vec3 vNormal;\n" +
                    "void main() {\n" +
                    "    vTexCoord = aTexCoord;\n" +
                    "    vColor = aColor;\n" +
                    "    mat4 boneTransform = mat4(1.0);\n" +
                    "    if (u_HasBones > 0.5) {\n" +
                    "        boneTransform  = u_Bones[int(aBoneIds.x)] * aWeights.x + u_Bones[int(aBoneIds.y)] * aWeights.y + u_Bones[int(aBoneIds.z)] * aWeights.z + u_Bones[int(aBoneIds.w)] * aWeights.w;\n" +
                    "    }\n" +
                    "    vec4 worldPos = u_Model * boneTransform * vec4(aPos, 1.0);\n" +
                    "    vWorldPos = worldPos.xyz;\n" +
                    "    vNormal = mat3(u_Model) * mat3(boneTransform) * aNormal;\n" +
                    "    gl_Position = u_ViewProjection * worldPos;\n" +
                    "}";

    private static final String FRAGMENT_TEMPLATE =
            "#version 330 core\n" +
                    "\n" +
                    "struct PBRParams {\n" +
                    "    vec3  diffuseColor;\n" +
                    "    vec3  f0;\n" +
                    "    float roughness;\n" +
                    "    float metallic;\n" +
                    "};\n" +
                    "\n" +
                    "#include \"filament/common_math.glsl\"\n" +
                    "#include \"filament/brdf.glsl\"\n" +
                    "#include \"shading_indirect.glsl\"\n" +
                    "#include \"surface_shading.glsl\"\n" +
                    "\n" +
                    "in vec2 vTexCoord;\n" +
                    "in vec4 vColor;\n" +
                    "in vec3 vWorldPos;\n" +
                    "in vec3 vNormal;\n" +
                    "out vec4 FragColor;\n" +
                    "\n" +
                    "uniform sampler2D u_Texture;\n" +
                    "uniform vec4 u_TextColor;\n" +
                    "uniform vec3 u_ViewPos;\n" +
                    "uniform float u_UseTexture;\n" +
                    "uniform float u_IsEmissive;\n" +
                    "\n" +
                    "uniform vec3 u_LightPos[8];\n" +
                    "uniform vec4 u_LightColor[8];\n" +
                    "uniform float u_LightIntensity[8];\n" +
                    "uniform float u_LightCount;\n" +
                    "\n" +
                    "void main() {\n" +
                    "    // 1. 基础颜色获取\n" +
                    "    vec4 texColor = (u_UseTexture > 0.5) ? texture(u_Texture, vTexCoord) : vec4(1.0);\n" +
                    "    if (u_UseTexture > 0.5 && texColor.a < 0.05) discard;\n" +
                    "    \n" +
                    "    vec3 rawColor = u_TextColor.rgb * vColor.rgb * texColor.rgb;\n" +
                    "    \n" +
                    "    // --- 修改后的增强自发光逻辑 --- \n" +
                    "    if (u_IsEmissive > 0.5) {\n" +
                    "        vec3 N = normalize(vNormal);\n" +
                    "        vec3 V = normalize(u_ViewPos - vWorldPos);\n" +
                    "        \n" +
                    "        // 计算边缘发光强度\n" +
                    "        float fresnel = pow(1.0 - max(dot(N, V), 0.0), 2.5);\n" +
                    "        \n" +
                    "        // 颜色混合：中心亮白，边缘为传入的颜色\n" +
                    "        // 这里手动跳过 finalizeColor 以免亮度被截断\n" +
                    "        vec3 emissive = mix(rawColor * 2.0, vec3(1.5, 1.5, 1.2), fresnel);\n" +
                    "        \n" +
                    "        // 简单的伽马映射保底\n" +
                    "        FragColor = vec4(pow(emissive, vec3(1.0/2.2)), u_TextColor.a);\n" +
                    "        return;\n" +
                    "    }\n" +
                    "\n" +
                    "    // 2. 标准 PBR 逻辑\n" +
                    "    vec3 baseColor = pow(rawColor, vec3(2.2));\n" +
                    "    float mVal = clamp(u_TextColor.a, 0.0, 1.0);\n" +
                    "    float rVal = clamp(vColor.a < 0.01 ? 0.5 : vColor.a, 0.05, 1.0);\n" +
                    "\n" +
                    "    vec3 N = normalize(vNormal);\n" +
                    "    vec3 V = normalize(u_ViewPos - vWorldPos);\n" +
                    "\n" +
                    "    PBRParams pixel;\n" +
                    "    pixel.diffuseColor = baseColor * (1.0 - mVal);\n" +
                    "    pixel.f0 = mix(vec3(0.04), baseColor, mVal);\n" +
                    "    pixel.roughness = rVal;\n" +
                    "    pixel.metallic = mVal;\n" +
                    "\n" +
                    "    vec3 Lo = evaluateIBL(pixel, N, V);\n" +
                    "    for(int i = 0; i < int(u_LightCount); i++) {\n" +
                    "        vec3 L = normalize(u_LightPos[i] - vWorldPos);\n" +
                    "        vec3 lightCol = u_LightColor[i].rgb * u_LightIntensity[i];\n" +
                    "        Lo += surfaceShading(pixel, L, lightCol, V, N);\n" +
                    "    }\n" +
                    "\n" +
                    "    Lo = finalizeColor(Lo);\n" +
                    "    FragColor = vec4(Lo, u_TextColor.a * texColor.a);\n" +
                    "}";

    public StandardShader() {
        super("StandardShader",
                VERTEX_SRC,
                FRAGMENT_TEMPLATE);
    }

    public void setBones(Matrix4f[] bones) {
        this.bind();
        if (bones != null && bones.length > 0) {
            this.setUniform("u_HasBones", 1.0f);
            for (int i = 0; i < Math.min(bones.length, 100); i++) {
                if(bones[i] != null) this.setUniform("u_Bones[" + i + "]", bones[i]);
            }
        } else {
            this.setUniform("u_HasBones", 0.0f);
        }
    }

    public void setEmissive(boolean emissive) {
        this.bind();
        this.setUniform("u_IsEmissive", emissive ? 1.0f : 0.0f);
    }

    public void setup(Matrix4f viewProj, Matrix4f model, Color color) {
        this.bind();
        this.setUniform("u_ViewProjection", viewProj);
        this.setUniform("u_Model", model);
        this.setUniform("u_TextColor", color);
        this.setUniform("u_Texture", 0);
    }

    public void applyLights(Vector3f viewPos) {
        this.bind();
        this.setUniform("u_ViewPos", viewPos);
        int count = Math.min(lights.size(), MAX_LIGHTS);
        this.setUniform("u_LightCount", (float) count);
        for (int i = 0; i < count; i++) {
            GLLight l = lights.get(i);
            this.setUniform("u_LightPos[" + i + "]", l.getPosition());
            this.setUniform("u_LightColor[" + i + "]", l.getColor());
            this.setUniform("u_LightIntensity[" + i + "]", l.getIntensity());
        }
    }

    public void addLight(GLLight light) { if (lights.size() < MAX_LIGHTS) lights.add(light); }
    public void clearLights() { lights.clear(); }
    public void setUseTexture(boolean use) { this.bind(); this.setUniform("u_UseTexture", use ? 1.0f : 0.0f); }
}