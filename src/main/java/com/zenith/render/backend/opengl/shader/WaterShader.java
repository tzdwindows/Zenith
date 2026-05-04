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
            "#version 450 core\n" +
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
                    "// ⭐ 新增：太阳参数同步\n" +
                    "uniform vec3 u_SunDir;\n" +
                    "uniform vec3 u_SunColor;\n" +
                    "\n" +
                    "// 物理天空模型 (与 AnimationShader 保持一致)\n" +
                    "vec3 evaluateSkyReflection(vec3 rd) {\n" +
                    "    vec3 L = normalize(u_SunDir);\n" +
                    "    float sunHeight = smoothstep(-0.2, 0.2, L.y);\n" +
                    "    vec3 skyHorizon = vec3(0.5, 0.6, 0.75);\n" +
                    "    vec3 skyZenith = vec3(0.05, 0.15, 0.4);\n" +
                    "    vec3 skyColor = mix(skyHorizon * 0.1, skyZenith, clamp(rd.y, 0.0, 1.0)) * sunHeight;\n" +
                    "    float cosTheta = dot(rd, L);\n" +
                    "    float sunDisk = smoothstep(0.9990, 0.9999, cosTheta) * sunHeight;\n" +
                    "    return skyColor + u_SunColor * sunDisk;\n" +
                    "}\n" +
                    "\n" +
                    "void main() {\n" +
                    "    vec2 screenUV = gl_FragCoord.xy / u_ScreenSize;\n" +
                    "    vec3 V = normalize(u_ViewPos - vWorldPos);\n" +
                    "    \n" +
                    "    // 计算太阳高度影响因子\n" +
                    "    float sunFactor = smoothstep(-0.1, 0.2, normalize(u_SunDir).y);\n" +
                    "\n" +
                    "    // 计算法线\n" +
                    "    vec3 tangentNormal = vec3(0.0, 0.0, 1.0);\n" +
                    "    if (u_HasNormalMap == 1) {\n" +
                    "        vec2 flow = vec2(u_Time * 0.02);\n" +
                    "        vec3 n1 = texture(u_WaterNormalTex, vWorldPos.xz * u_NormalScale + flow).rgb * 2.0 - 1.0;\n" +
                    "        vec3 n2 = texture(u_WaterNormalTex, vWorldPos.xz * u_NormalScale * 1.5 - flow * 0.8).rgb * 2.0 - 1.0;\n" +
                    "        tangentNormal = normalize(vec3(n1.xy + n2.xy, n1.z * n2.z));\n" +
                    "        tangentNormal.xy *= u_NormalStrength;\n" +
                    "        tangentNormal = normalize(tangentNormal);\n" +
                    "    }\n" +
                    "    vec3 N = normalize(vTBN * tangentNormal);\n" +
                    "\n" +
                    "    // 环境项计算\n" +
                    "    WaterMaterial mat;\n" +
                    "    // ⭐ 核心修复：水面颜色随太阳变暗\n" +
                    "    mat.deepColor = u_DeepColor * (0.1 + 0.9 * sunFactor);\n" +
                    "    mat.shallowColor = u_ShallowColor * (0.1 + 0.9 * sunFactor);\n" +
                    "    mat.foamColor = vec3(0.95) * sunFactor;\n" +
                    "    mat.roughness = 0.05;\n" +
                    "    mat.clarity = 0.3;\n" +
                    "    mat.rainIntensity = u_RainIntensity;\n" +
                    "    mat.dayFactor = sunFactor;\n" +
                    "\n" +
                    "    // 获取基础环境色 (折射等)\n" +
                    "    vec3 finalColor = calculateWaterEnvironment(vWorldPos, V, N, mat, screenUV);\n" +
                    "    \n" +
                    "    // ⭐ 核心修复：加入真实的物理天空反射\n" +
                    "    vec3 R = reflect(-V, N);\n" +
                    "    vec3 skyRef = evaluateSkyReflection(R);\n" +
                    "    float fresnel = 0.02 + 0.98 * pow(1.0 - max(dot(N, V), 0.0), 5.0);\n" +
                    "    finalColor += skyRef * fresnel * 0.5;\n" +
                    "\n" +
                    "    // 累加直接光照 (太阳高光)\n" +
                    "    int lightCount = max(0, min(int(u_LightCount), 16));\n" +
                    "    for (int i = 0; i < lightCount; i++) {\n" +
                    "        Light light = u_Lights[i];\n" +
                    "        vec3 L;\n" +
                    "        float atten = 1.0;\n" +
                    "        if (light.type == 0) {\n" +
                    "            L = normalize(-light.direction);\n" +
                    "        } else {\n" +
                    "            vec3 lVec = light.position - vWorldPos;\n" +
                    "            float d = length(lVec);\n" +
                    "            L = normalize(lVec);\n" +
                    "            atten = calculateAttenuation(d, light.range);\n" +
                    "        }\n" +
                    "        vec3 radiance = light.color.rgb * light.intensity * atten;\n" +
                    "        finalColor += calculateWaterDirect(V, L, N, radiance, mat);\n" +
                    "    }\n" +
                    "\n" +
                    "    // Tone Mapping (ACES)\n" +
                    "    finalColor = max(finalColor, 0.0);\n" +
                    "    finalColor = (finalColor * (2.51 * finalColor + 0.03)) / (finalColor * (2.43 * finalColor + 0.59) + 0.14);\n" +
                    "    \n" +
                    "    float NoV_flat = clamp(dot(vec3(0.0, 1.0, 0.0), V), 0.0, 1.0);\n" +
                    "    float alpha = mix(0.8, 1.0, pow(1.0 - NoV_flat, 3.0));\n" +
                    "    \n" +
                    "    FragColor = vec4(pow(finalColor, vec3(1.0/2.2)), alpha);\n" +
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

    // ⭐ 新增：用于每帧同步太阳状态
    public void setSun(Vector3f dir, Vector3f color) {
        this.bind();
        setUniform("u_SunDir", dir);
        setUniform("u_SunColor", color);
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