package com.zenith.render.backend.opengl.shader;

import com.zenith.render.backend.opengl.GLLight;
import com.zenith.render.backend.opengl.LightManager;
import org.joml.Matrix4f;
import com.zenith.common.math.Color;
import org.joml.Vector3f;
import org.joml.Vector4f;
import java.util.ArrayList;
import java.util.List;

public class StandardShader extends GLShader {

    private static final int MAX_LIGHTS = LightManager.MAX_LIGHTS;
    private final List<GLLight> lights = new ArrayList<>();

    private static final String VERTEX_SRC =
            "#version 330 core\n" +
                    "layout (location = 0) in vec3 aPos;\n" +
                    "layout (location = 1) in vec2 aTexCoord;\n" +
                    "layout (location = 2) in vec3 aNormal;\n" +
                    "layout (location = 3) in ivec4 aBoneIds;\n" +
                    "layout (location = 4) in vec4 aWeights;\n" +
                    "layout (location = 5) in vec4 aColor;\n" +
                    "\n" +
                    "uniform mat4 u_ViewProjection;\n" +
                    "uniform mat4 u_Model;\n" +
                    "uniform mat4 u_Bones[100];\n" +
                    "uniform float u_HasBones;\n" +
                    "\n" +
                    "out vec2 vTexCoord;\n" +
                    "out vec4 vColor;\n" +
                    "out vec3 vWorldPos;\n" +
                    "out vec3 vNormal;\n" +
                    "\n" +
                    "void main() {\n" +
                    "    vTexCoord = aTexCoord;\n" +
                    "    vColor = aColor;\n" +
                    "    mat4 boneTransform = mat4(1.0);\n" +
                    "    if (u_HasBones > 0.5) {\n" +
                    "        boneTransform  = u_Bones[aBoneIds.x] * aWeights.x + u_Bones[aBoneIds.y] * aWeights.y + u_Bones[aBoneIds.z] * aWeights.z + u_Bones[aBoneIds.w] * aWeights.w;\n" +
                    "    }\n" +
                    "    vec4 worldPos = u_Model * boneTransform * vec4(aPos, 1.0);\n" +
                    "    vWorldPos = worldPos.xyz;\n" +
                    "    \n" +
                    "    mat3 normalMatrix = transpose(inverse(mat3(u_Model * boneTransform)));\n" +
                    "    vNormal = normalMatrix * aNormal;\n" +
                    "    \n" +
                    "    gl_Position = u_ViewProjection * worldPos;\n" +
                    "}";

    private static final String FRAGMENT_TEMPLATE =
            "#version 330 core\n" +
                    "\n" +
                    "struct Light {\n" +
                    "    float type;\n" + // 改为 float
                    "    vec3 position;\n" +
                    "    vec3 direction;\n" +
                    "    vec4 color;\n" +
                    "    float intensity;\n" +
                    "    float range;\n" + // 补齐字段
                    "    float innerCutOff;\n" +
                    "    float outerCutOff;\n" +
                    "    float ambientStrength;\n" +
                    "};\n" +
                    "\n" +
                    "uniform float u_LightCount;\n" + // 改为 float
                    "uniform Light u_Lights[16];\n" +
                    "\n" +
                    "in vec2 vTexCoord;\n" +
                    "in vec4 vColor;\n" +
                    "in vec3 vWorldPos;\n" +
                    "in vec3 vNormal;\n" +
                    "\n" +
                    "out vec4 FragColor;\n" +
                    "\n" +
                    "uniform sampler2D u_Texture;\n" +
                    "uniform vec4 u_TextColor;\n" +
                    "uniform vec3 u_ViewPos;\n" +
                    "uniform float u_UseTexture;\n" +
                    "\n" +
                    "uniform float u_IsEmissive;\n" +
                    "uniform vec3 u_EmissiveColor;\n" +
                    "uniform float u_EmissiveIntensity;\n" +
                    "\n" +
                    "void main() {\n" +
                    "    vec3 N = normalize(vNormal);\n" +
                    "    vec3 V = normalize(u_ViewPos - vWorldPos);\n" +
                    "    vec3 albedo = u_TextColor.rgb;\n" +
                    "    if (u_UseTexture > 0.5) {\n" +
                    "        albedo *= texture(u_Texture, vTexCoord).rgb;\n" +
                    "    }\n" +
                    "\n" +
                    "    vec3 result = vec3(0.0);\n" +
                    "    int count = int(u_LightCount);\n" +
                    "\n" +
                    "    for (int i = 0; i < count; i++) {\n" +
                    "        Light light = u_Lights[i];\n" +
                    "        vec3 L;\n" +
                    "        float attenuation = 1.0;\n" +
                    "\n" +
                    "        if (light.type < 0.5) {\n" + // Directional
                    "            L = normalize(-light.direction);\n" +
                    "            result += albedo * light.color.rgb * light.ambientStrength;\n" +
                    "        } else {\n" +
                    "            vec3 toLight = light.position - vWorldPos;\n" +
                    "            float dist = length(toLight);\n" +
                    "            L = normalize(toLight);\n" +
                    "            // 物理衰减\n" +
                    "            attenuation = 1.0 / max(dist * dist, 0.0001);\n" +
                    "            float window = clamp(1.0 - pow(dist/light.range, 4.0), 0.0, 1.0);\n" +
                    "            attenuation *= window * window;\n" +
                    "        }\n" +
                    "\n" +
                    "        float NdotL = max(dot(N, L), 0.0);\n" +
                    "        vec3 diffuse = albedo * NdotL;\n" +
                    "\n" +
                    "        vec3 H = normalize(V + L);\n" +
                    "        float spec = pow(max(dot(N, H), 0.0), 32.0);\n" +
                    "        vec3 specular = vec3(0.1) * spec;\n" + // 降低高光防止过曝
                    "\n" +
                    "        vec3 radiance = light.color.rgb * light.intensity * attenuation;\n" +
                    "        result += (diffuse + specular) * radiance;\n" +
                    "    }\n" +
                    "\n" +
                    "    if (u_IsEmissive > 0.5) {\n" +
                    "        result += u_EmissiveColor * u_EmissiveIntensity;\n" +
                    "    }\n" +
                    "\n" +
                    "    // ACESToneMapping 简易版\n" +
                    "    vec3 mapped = (result * (2.51 * result + 0.03)) / (result * (2.43 * result + 0.59) + 0.14);\n" +
                    "    FragColor = vec4(pow(max(mapped, 0.0), vec3(1.0/2.2)), 1.0);\n" +
                    "}";

    public StandardShader() {
        super("StandardShader", VERTEX_SRC, FRAGMENT_TEMPLATE);
    }

    public void setup(Matrix4f viewProj, Matrix4f model, Color color) {
        this.bind();
        this.setUniform("u_ViewProjection", viewProj);
        this.setUniform("u_Model", model);
        // 显式转换 Color 到 Vector4f
        this.setUniform("u_TextColor", new Vector4f(color.r, color.g, color.b, color.a));
        this.setUniform("u_Texture", 0);
    }

    public void applyLights(Vector3f viewPos) {
        this.bind();
        this.setUniform("u_ViewPos", viewPos != null ? viewPos : new Vector3f(0));

        float count = (float) Math.min(lights.size(), MAX_LIGHTS);
        this.setUniform("u_LightCount", count);

        for (int i = 0; i < (int)count; i++) {
            GLLight l = lights.get(i);
            String prefix = "u_Lights[" + i + "].";
            this.setUniform(prefix + "type", (float)l.getType());
            this.setUniform(prefix + "position", l.getPosition() != null ? l.getPosition() : new Vector3f(0));
            this.setUniform(prefix + "direction", l.getDirection() != null ? l.getDirection() : new Vector3f(0, -1, 0));

            Color c = l.getColor();
            this.setUniform(prefix + "color", new Vector4f(c.r, c.g, c.b, c.a));

            this.setUniform(prefix + "intensity", l.getIntensity());
            this.setUniform(prefix + "range", l.getRange() > 0 ? l.getRange() : 1000.0f);
            this.setUniform(prefix + "innerCutOff", l.getInnerCutOff());
            this.setUniform(prefix + "outerCutOff", l.getOuterCutOff());
            this.setUniform(prefix + "ambientStrength", l.getAmbientStrength());
        }
    }

    // 其他方法保持不变
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