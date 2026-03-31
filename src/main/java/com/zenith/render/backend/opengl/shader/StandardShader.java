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

    // 增加最大光源数量以适应复杂场景
    private static final int MAX_LIGHTS = 16;
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
                    "// 现代游戏引擎标准的光源结构体\n" +
                    "struct Light {\n" +
                    "    int type;           // 0: 平行光(太阳), 1: 点光源(火把), 2: 聚光灯(手电筒)\n" +
                    "    vec3 position;      // 光源位置\n" +
                    "    vec3 direction;     // 光照方向 (用于平行光和聚光灯)\n" +
                    "    vec4 color;         // 光照颜色\n" +
                    "    float intensity;    // 光照强度\n" +
                    "    float range;        // 最大衰减半径\n" +
                    "    float innerCutOff;  // 聚光灯内圆锥角 (余弦值)\n" +
                    "    float outerCutOff;  // 聚光灯外圆锥角 (余弦值)\n" +
                    "    float ambientStrength;// 环境光贡献度\n" +
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
                    "\n" +
                    "// 自发光参数\n" +
                    "uniform float u_IsEmissive;\n" +
                    "uniform vec3 u_EmissiveColor;\n" +
                    "uniform float u_EmissiveIntensity;\n" +
                    "\n" +
                    "uniform Light u_Lights[16];\n" +
                    "uniform int u_LightCount;\n" +
                    "\n" +
                    "// Unreal Engine 风格的物理平方反比衰减\n" +
                    "float calculateAttenuation(float dist, float range) {\n" +
                    "    float distSq = dist * dist;\n" +
                    "    float attenuation = 1.0 / max(distSq, 0.0001);\n" +
                    "    // 窗口函数，确保在 range 边缘平滑降为 0\n" +
                    "    float distOverRange = dist / max(range, 0.001);\n" +
                    "    float windowing = clamp(1.0 - pow(distOverRange, 4.0), 0.0, 1.0);\n" +
                    "    return attenuation * windowing * windowing;\n" +
                    "}\n" +
                    "\n" +
                    "void main() {\n" +
                    "    vec4 texColor = (u_UseTexture > 0.5) ? texture(u_Texture, vTexCoord) : vec4(1.0);\n" +
                    "    if (u_UseTexture > 0.5 && texColor.a < 0.05) discard;\n" +
                    "    \n" +
                    "    vec3 rawColor = u_TextColor.rgb * vColor.rgb * texColor.rgb;\n" +
                    "    vec3 N = normalize(vNormal);\n" +
                    "    vec3 V = normalize(u_ViewPos - vWorldPos);\n" +
                    "\n" +
                    "    // 1. 标准 PBR 参数设置\n" +
                    "    vec3 baseColor = pow(rawColor, vec3(2.2));\n" +
                    "    float mVal = clamp(u_TextColor.a, 0.0, 1.0);\n" +
                    "    float rVal = clamp(vColor.a < 0.01 ? 0.5 : vColor.a, 0.05, 1.0);\n" +
                    "\n" +
                    "    PBRParams pixel;\n" +
                    "    pixel.diffuseColor = baseColor * (1.0 - mVal);\n" +
                    "    pixel.f0 = mix(vec3(0.04), baseColor, mVal);\n" +
                    "    pixel.roughness = rVal;\n" +
                    "    pixel.metallic = mVal;\n" +
                    "\n" +
                    "    // 2. 光照累加计算\n" +
                    "    vec3 Lo = evaluateIBL(pixel, N, V);\n" +
                    "    vec3 ambientTotal = vec3(0.0);\n" +
                    "\n" +
                    "    for(int i = 0; i < u_LightCount; i++) {\n" +
                    "        vec3 L;\n" +
                    "        float attenuation = 1.0;\n" +
                    "        \n" +
                    "        if (u_Lights[i].type == 0) {\n" +
                    "            // 平行光 (Sun/Moon)\n" +
                    "            L = normalize(-u_Lights[i].direction);\n" +
                    "            ambientTotal += baseColor * u_Lights[i].color.rgb * u_Lights[i].ambientStrength;\n" +
                    "        } else {\n" +
                    "            // 点光源 / 聚光灯\n" +
                    "            vec3 lightVec = u_Lights[i].position - vWorldPos;\n" +
                    "            float dist = length(lightVec);\n" +
                    "            L = lightVec / dist;\n" +
                    "            \n" +
                    "            // 物理距离衰减\n" +
                    "            attenuation = calculateAttenuation(dist, u_Lights[i].range);\n" +
                    "            \n" +
                    "            if (u_Lights[i].type == 2) {\n" +
                    "                // 聚光灯圆锥边缘柔和衰减\n" +
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
                    "    \n" +
                    "    // 加入全局环境光\n" +
                    "    Lo += ambientTotal;\n" +
                    "\n" +
                    "    // 3. 自发光 (Emissive) 处理\n" +
                    "    if (u_IsEmissive > 0.5) {\n" +
                    "        // 基础自发光颜色\n" +
                    "        vec3 emissive = u_EmissiveColor * u_EmissiveIntensity;\n" +
                    "        // 保留你原本特色的菲涅尔边缘泛光效果\n" +
                    "        float fresnel = pow(1.0 - max(dot(N, V), 0.0), 2.5);\n" +
                    "        emissive += mix(vec3(0.0), vec3(1.5, 1.5, 1.2) * u_EmissiveIntensity, fresnel);\n" +
                    "        \n" +
                    "        // 叠加到总光亮度中\n" +
                    "        Lo += emissive;\n" +
                    "    }\n" +
                    "\n" +
                    "    Lo = finalizeColor(Lo);\n" +
                    "    FragColor = vec4(Lo, u_TextColor.a * texColor.a);\n" +
                    "}";

    public StandardShader() {
        super("StandardShader", VERTEX_SRC, FRAGMENT_TEMPLATE);
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

    /**
     * 高级自发光控制
     * @param emissive 是否开启自发光
     * @param color 自发光颜色 (RGB)
     * @param intensity 发光强度 (例如 1.0 到 50.0+)
     */
    public void setEmissive(boolean emissive, Vector3f color, float intensity) {
        this.bind();
        this.setUniform("u_IsEmissive", emissive ? 1.0f : 0.0f);
        if (emissive) {
            this.setUniform("u_EmissiveColor", color);
            this.setUniform("u_EmissiveIntensity", intensity);
        }
    }

    // 兼容旧版的调用
    public void setEmissive(boolean emissive) {
        setEmissive(emissive, new Vector3f(1.0f, 1.0f, 1.0f), 2.0f);
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
        this.setUniform("u_LightCount", count);

        for (int i = 0; i < count; i++) {
            GLLight l = lights.get(i);
            String prefix = "u_Lights[" + i + "].";

            // 绑定光源的所有属性
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

    public void addLight(GLLight light) { if (lights.size() < MAX_LIGHTS) lights.add(light); }
    public void clearLights() { lights.clear(); }
    public void setUseTexture(boolean use) { this.bind(); this.setUniform("u_UseTexture", use ? 1.0f : 0.0f); }
}