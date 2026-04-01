package com.zenith.render.backend.opengl.shader;

import com.zenith.common.math.Color;
import com.zenith.render.backend.opengl.texture.GLTexture;
import org.joml.*;
import com.zenith.render.backend.opengl.LightManager;

public class WaterShader extends GLShader {

    private static final String VERTEX_SRC =
            "#version 330 core\n" +
                    "#include \"water.glsl\"\n" +
                    "layout (location = 0) in vec3 aPos;\n" +
                    "layout (location = 1) in vec3 aNormal;\n" +
                    "layout (location = 2) in vec2 aTexCoord;\n" +
                    "uniform mat4 u_ViewProjection;\n" +
                    "uniform mat4 u_Model;\n" +
                    "uniform float u_Time;\n" +
                    "out vec3 vWorldPos;\n" +
                    "out vec2 vTexCoord;\n" +
                    "out mat3 vTBN;\n" +
                    "void main() {\n" +
                    "    WaveResult wave = getWaterWave(aPos.xz, u_Time);\n" +
                    "    vec3 pos = aPos;\n" +
                    "    pos.y += wave.height;\n" +
                    "    vec4 worldPos = u_Model * vec4(pos, 1.0);\n" +
                    "    vWorldPos = worldPos.xyz;\n" +
                    "    vTexCoord = aTexCoord;\n" +
                    "    vec3 T = normalize(vec3(u_Model * vec4(wave.tangent, 0.0)));\n" +
                    "    vec3 B = normalize(vec3(u_Model * vec4(wave.bitangent, 0.0)));\n" +
                    "    vec3 N = normalize(vec3(u_Model * vec4(wave.normal, 0.0)));\n" +
                    "    vTBN = mat3(T, B, N);\n" +
                    "    gl_Position = u_ViewProjection * worldPos;\n" +
                    "}";

    private static final String FRAGMENT_SRC =
            "#version 330 core\n" +
                    "\n" +
                    "// 结构体必须与 LightManager 传参完全一致\n" +
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
                    "\n" +
                    "uniform float u_LightCount;\n" + // 改为 float
                    "uniform Light u_Lights[16];\n" +
                    "uniform vec2 u_ScreenSize;\n" +
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
                    "\n" +
                    "void main() {\n" +
                    "    vec2 screenUV = gl_FragCoord.xy / u_ScreenSize;\n" +
                    "    vec3 V = normalize(u_ViewPos - vWorldPos);\n" +
                    "    vec3 tangentNormal = vec3(0.0, 0.0, 1.0);\n" +
                    "\n" +
                    "    if (u_HasNormalMap == 1) {\n" +
                    "        vec2 flow = vec2(0.02, 0.01) * u_Time;\n" +
                    "        vec3 n = texture(u_WaterNormalTex, vWorldPos.xz * u_NormalScale + flow).rgb * 2.0 - 1.0;\n" +
                    "        tangentNormal = normalize(n);\n" +
                    "        tangentNormal.xy *= u_NormalStrength;\n" +
                    "    }\n" +
                    "\n" +
                    "    vec3 N = normalize(vTBN * tangentNormal);\n" +
                    "\n" +
                    "    WaterMaterial mat;\n" +
                    "    mat.deepColor = vec3(0.02, 0.05, 0.08);\n" +
                    "    mat.shallowColor = vec3(0.08, 0.12, 0.15);\n" +
                    "    mat.roughness = 0.05;\n" +
                    "    mat.clarity = 1.0;\n" +
                    "    mat.rainIntensity = u_RainIntensity;\n" +
                    "    mat.foamColor = vec3(0.92);\n" +
                    "\n" +
                    "    vec3 color = vec3(0.0);\n" +
                    "    int count = int(u_LightCount);\n" +
                    "\n" +
                    "    for (int i = 0; i < count; i++) {\n" +
                    "        vec3 L; vec3 radiance;\n" +
                    "        // 确保你的 lighting.glsl 中 computeLight 接受 float 类型的 Light 结构体\n" +
                    "        computeLight(u_Lights[i], vWorldPos, L, radiance);\n" +
                    "        color += shadeWaterPBR(vWorldPos, V, L, N, radiance, mat, u_Time, screenUV);\n" +
                    "    }\n" +
                    "\n" +
                    "    // Tone mapping\n" +
                    "    color = (color * (2.51 * color + 0.03)) /\n" +
                    "            (color * (2.43 * color + 0.59) + 0.14);\n" +
                    "\n" +
                    "    float NoV = clamp(dot(N, V), 0.001, 1.0);\n" +
                    "    float fresnel = 0.02 + (1.0 - 0.02) * pow(1.0 - NoV, 5.0);\n" +
                    "    float alpha = mix(0.92, 1.0, fresnel);\n" +
                    "\n" +
                    "    FragColor = vec4(pow(max(color, 0.0), vec3(1.0/2.2)), alpha);\n" +
                    "}";

    public WaterShader() {
        super("WaterShader", VERTEX_SRC, FRAGMENT_SRC);
        bind();
        setUniform("u_NormalScale", 0.06f);
        setUniform("u_NormalStrength", 0.5f);
        setUniform("u_ScreenSize", new Vector2f(1920, 1080)); // 默认值
    }

    /**
     * ⭐ 关键修复：应用光照
     * 直接利用 LightManager 的逻辑，但 LightManager 内部已经做了 float 转换
     */
    public void applyLights(LightManager lm, Vector3f viewPos) {
        this.bind();
        lm.apply(this, viewPos);
    }

    // =========================
    // 接口优化
    // =========================

    public void setScreenSize(int w, int h) {
        this.bind();
        setUniform("u_ScreenSize", new Vector2f((float)w, (float)h));
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

    public void setSplashes(Vector4f[] splashes) {
        this.bind();
        for (int i = 0; i < 4; i++) {
            String name = "u_ActiveSplashes[" + i + "]";
            if (splashes != null && i < splashes.length && splashes[i] != null) {
                setUniform(name, splashes[i]);
            } else {
                setUniform(name, new Vector4f(0, 0, 0, -1.0f));
            }
        }
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