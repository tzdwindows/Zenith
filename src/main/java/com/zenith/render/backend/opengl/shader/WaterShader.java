package com.zenith.render.backend.opengl.shader;

import com.zenith.common.math.Color;
import com.zenith.render.backend.opengl.texture.GLTexture;
import org.joml.*;
import com.zenith.render.backend.opengl.LightManager;

public class WaterShader extends GLShader {
    private static final String VERTEX_SRC =
            "#version 330 core\n" +
                    "\n" +
                    "#include \"water.glsl\"\n" +
                    "\n" +
                    "layout (location = 0) in vec3 aPos;\n" +
                    "layout (location = 1) in vec3 aNormal;\n" +
                    "layout (location = 2) in vec2 aTexCoord;\n" +
                    "\n" +
                    "// u_ViewProjection 已在 water.glsl 声明，此处移除以防止重定义冲突\n" +
                    "uniform mat4 u_Model;\n" +
                    "uniform float u_Time;\n" +
                    "\n" +
                    "out vec3 vWorldPos;\n" +
                    "out vec2 vTexCoord;\n" +
                    "out mat3 vTBN;\n" +
                    "\n" +
                    "void main() {\n" +
                    "    WaveResult wave = getWaterWave(aPos.xz, u_Time);\n" +
                    "    vec3 pos = aPos;\n" +
                    "    pos.y += wave.height;\n" +
                    "    vec4 worldPos = u_Model * vec4(pos, 1.0);\n" +
                    "    vWorldPos = worldPos.xyz;\n" +
                    "    vTexCoord = aTexCoord;\n" +
                    "    \n" +
                    "    mat3 normalMatrix = mat3(u_Model);\n" +
                    "    vec3 T = normalize(normalMatrix * wave.tangent);\n" +
                    "    vec3 B = normalize(normalMatrix * wave.bitangent);\n" +
                    "    vec3 N = normalize(normalMatrix * wave.normal);\n" +
                    "    vTBN = mat3(T, B, N);\n" +
                    "    \n" +
                    "    gl_Position = u_ViewProjection * worldPos;\n" +
                    "}";

    private static final String FRAGMENT_SRC =
            "#version 330 core\n" +
                    "\n" +
                    "#include \"water.glsl\"\n" +
                    "#include \"lighting.glsl\"\n" +
                    "\n" +
                    "in vec3 vWorldPos;\n" +
                    "in vec2 vTexCoord;\n" +
                    "in mat3 vTBN;\n" +
                    "out vec4 FragColor;\n" +
                    "\n" +
                    "uniform vec3 u_ViewPos;\n" +
                    "uniform float u_Time;\n" +
                    "uniform float u_RainIntensity;\n" +
                    "uniform float u_NormalScale;\n" +
                    "uniform float u_NormalStrength;\n" +
                    "uniform sampler2D u_WaterNormalTex;\n" +
                    "uniform int u_HasNormalMap;\n" +
                    "uniform vec3 u_DeepColor;\n" +
                    "uniform vec3 u_ShallowColor;\n" +
                    "uniform vec2 u_ScreenSize;\n" +
                    "\n" +
                    "void main() {\n" +
                    "    vec2 screenUV = gl_FragCoord.xy / u_ScreenSize;\n" +
                    "    vec3 V = normalize(u_ViewPos - vWorldPos);\n" +
                    "    vec3 tangentNormal = vec3(0.0, 0.0, 1.0);\n" +
                    "\n" +
                    "    if (u_HasNormalMap == 1) {\n" +
                    "        vec2 flow1 = vec2(0.015, 0.01) * u_Time;\n" +
                    "        vec2 flow2 = vec2(-0.01, 0.015) * u_Time;\n" +
                    "        vec3 n1 = texture(u_WaterNormalTex, vWorldPos.xz * u_NormalScale + flow1).rgb * 2.0 - 1.0;\n" +
                    "        vec3 n2 = texture(u_WaterNormalTex, vWorldPos.xz * u_NormalScale * 1.5 + flow2).rgb * 2.0 - 1.0;\n" +
                    "        tangentNormal = normalize(vec3(n1.xy + n2.xy, n1.z * n2.z));\n" +
                    "        tangentNormal.xy *= u_NormalStrength;\n" +
                    "        tangentNormal = normalize(tangentNormal);\n" +
                    "    }\n" +
                    "    vec3 N = normalize(vTBN * tangentNormal);\n" +
                    "    int lightCount = max(1, min(int(u_LightCount), 16));\n" +
                    "\n" +
                    "    float dayFactor = 0.02;\n" +
                    "    if (u_LightCount > 0) {\n" +
                    "        dayFactor = clamp(u_Lights[0].intensity * 0.5, 0.02, 1.0);\n" +
                    "    }\n" +
                    "\n" +
                    "    WaterMaterial mat;\n" +
                    "    mat.deepColor = u_DeepColor * dayFactor;\n" +
                    "    mat.shallowColor = u_ShallowColor * dayFactor;\n" +
                    "    mat.foamColor = vec3(0.85) * dayFactor;\n" +
                    "    mat.roughness = 0.05;\n" +
                    "    mat.clarity = 1.0;\n" +
                    "    mat.rainIntensity = u_RainIntensity;\n" +
                    "    mat.ambientWeight = 1.0 / float(lightCount);\n" +
                    "    mat.dayFactor = dayFactor;\n" +
                    "\n" +
                    "    vec3 finalColor = vec3(0.0);\n" +
                    "    for (int i = 0; i < lightCount; i++) {\n" +
                    "        Light light = u_Lights[i];\n" +
                    "        vec3 L; \n" +
                    "        float attenuation = 1.0;\n" +
                    "        if (light.type == 0) {\n" +
                    "            L = normalize(-light.direction);\n" +
                    "        } else {\n" +
                    "            vec3 lightVec = light.position - vWorldPos;\n" +
                    "            float dist = length(lightVec);\n" +
                    "            L = normalize(lightVec);\n" +
                    "            attenuation = calculateAttenuation(dist, light.range);\n" +
                    "            if (light.type == 2) attenuation *= calculateSpotAttenuation(light, L);\n" +
                    "        }\n" +
                    "        vec3 radiance = light.color.rgb * light.intensity * attenuation;\n" +
                    "        finalColor += shadeWaterPBR(vWorldPos, V, L, N, radiance, mat, u_Time, screenUV);\n" +
                    "    }\n" +
                    "\n" +
                    "    if(isnan(finalColor.x) || isnan(finalColor.y) || isnan(finalColor.z)) finalColor = u_DeepColor * dayFactor;\n" +
                    "    finalColor = (finalColor * (2.51 * finalColor + 0.03)) / (finalColor * (2.43 * finalColor + 0.59) + 0.14);\n" +
                    "    float NoV_flat = clamp(dot(vec3(0.0, 1.0, 0.0), V), 0.0, 1.0);\n" +
                    "    float alpha = mix(0.9, 1.0, pow(1.0 - NoV_flat, 4.0));\n" +
                    "    FragColor = vec4(pow(max(finalColor, 0.0), vec3(1.0/2.2)), alpha);\n" +
                    "}";

    public WaterShader() {
        super("WaterShader", VERTEX_SRC, FRAGMENT_SRC);
        bind();
        setUniform("u_NormalScale", 0.03f);
        setUniform("u_NormalStrength", 0.5f);
        setUniform("u_ScreenSize", new Vector2f(1920, 1080));
    }

    public void setScreenSize(int w, int h) {
        this.bind();
        setUniform("u_ScreenSize", new Vector2f((float)w, (float)h));
    }

    public void applyLights(LightManager lm, Vector3f viewPos) {
        this.bind();
        lm.apply(this, viewPos);
    }

    public void bindWaterNormal(GLTexture normalTex) {
        this.bind();
        if (normalTex != null && normalTex.getId() != 0) {
            normalTex.bind(2);
            setUniform("u_WaterNormalTex", 2);
            setUniform("u_HasNormalMap", 1);
        } else {
            setUniform("u_HasNormalMap", 0);
        }
    }

    public void setRainIntensity(float intensity) {
        this.bind();
        setUniform("u_RainIntensity", intensity);
    }

    public void updateUniforms(Vector3f viewPos, Color deep, Color shallow, float time, float rain) {
        this.bind();
        setUniform("u_ViewPos", viewPos);
        setUniform("u_DeepColor", new Vector3f(deep.r, deep.g, deep.b));
        setUniform("u_ShallowColor", new Vector3f(shallow.r, shallow.g, shallow.b));
        setUniform("u_Time", time);
        setRainIntensity(rain);
    }
}