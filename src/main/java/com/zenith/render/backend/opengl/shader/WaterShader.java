package com.zenith.render.backend.opengl.shader;

import com.zenith.common.math.Color;
import com.zenith.render.backend.opengl.texture.GLTexture;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

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
                    "#include \"water.glsl\"\n" +
                    "in vec3 vWorldPos;\n" +
                    "in vec2 vTexCoord;\n" +
                    "in mat3 vTBN;\n" +
                    "out vec4 FragColor;\n" +
                    "uniform vec3 u_ViewPos;\n" +
                    "uniform vec3 u_LightDir;\n" +
                    "uniform vec3 u_LightIntensity;\n" +
                    "uniform vec3 u_DeepColor;\n" +
                    "uniform vec3 u_ShallowColor;\n" +
                    "uniform float u_Time;\n" +
                    "uniform float u_RainIntensity;\n" +
                    "uniform float u_NormalScale;\n" +
                    "uniform float u_NormalStrength;\n" +
                    "uniform sampler2D u_WaterNormalTex;\n" +
                    "uniform int u_HasNormalMap;\n" +
                    "\n" +
                    "vec2 hash2(vec2 p) {\n" +
                    "    p = vec2(dot(p, vec2(127.1, 311.7)), dot(p, vec2(269.5, 183.3)));\n" +
                    "    return fract(sin(p) * 43758.5453123);\n" +
                    "}\n" +
                    "\n" +
                    "float stylizedWaterRipple(vec2 uv, float time) {\n" +
                    "    vec2 i = floor(uv);\n" +
                    "    vec2 f = fract(uv);\n" +
                    "    float minDist = 1.0;\n" +
                    "    for (int y = -1; y <= 1; y++) {\n" +
                    "        for (int x = -1; x <= 1; x++) {\n" +
                    "            vec2 neighbor = vec2(float(x), float(y));\n" +
                    "            vec2 point = hash2(i + neighbor);\n" +
                    "            point = 0.5 + 0.5 * sin(time * 1.5 + 6.2831 * point);\n" +
                    "            vec2 diff = neighbor + point - f;\n" +
                    "            minDist = min(minDist, length(diff));\n" +
                    "        }\n" +
                    "    }\n" +
                    "    return minDist;\n" +
                    "}\n" +
                    "\n" +
                    "float getRainRings(vec2 uv, float time) {\n" +
                    "    vec2 id = floor(uv);\n" +
                    "    vec2 f = fract(uv);\n" +
                    "    float r = 0.0;\n" +
                    "    for (int y = -1; y <= 1; y++) {\n" +
                    "        for (int x = -1; x <= 1; x++) {\n" +
                    "            vec2 offset = vec2(float(x), float(y));\n" +
                    "            vec2 h = hash2(id + offset);\n" +
                    "            vec2 center = offset + h;\n" +
                    "            float dist = length(f - center);\n" +
                    "            float phase = fract(time * 1.2 + h.x * 10.0);\n" +
                    "            float ring = smoothstep(0.0, 0.05, phase - dist) * smoothstep(0.0, 0.05, dist - (phase - 0.08));\n" +
                    "            r += ring * (1.0 - phase);\n" +
                    "        }\n" +
                    "    }\n" +
                    "    return r;\n" +
                    "}\n" +
                    "\n" +
                    "void main() {\n" +
                    "    vec2 screenUV = gl_FragCoord.xy / u_ScreenSize;\n" +
                    "    vec3 V = normalize(u_ViewPos - vWorldPos);\n" +
                    "    vec3 L = normalize(u_LightDir);\n" +
                    "    vec3 tangentNormal = vec3(0.0, 0.0, 1.0);\n" +
                    "\n" +
                    "    if (u_HasNormalMap == 1) {\n" +
                    "        vec2 flow1 = vec2(0.015, 0.010) * u_Time;\n" +
                    "        vec2 flow2 = vec2(-0.020, 0.015) * u_Time;\n" +
                    "        vec2 uv1 = vWorldPos.xz * u_NormalScale + flow1;\n" +
                    "        vec2 uv2 = vWorldPos.xz * (u_NormalScale * 2.0) + flow2;\n" +
                    "        vec3 n1 = texture(u_WaterNormalTex, uv1).rgb * 2.0 - 1.0;\n" +
                    "        vec3 n2 = texture(u_WaterNormalTex, uv2).rgb * 2.0 - 1.0;\n" +
                    "        tangentNormal = normalize(n1 + n2);\n" +
                    "        tangentNormal.xy *= u_NormalStrength;\n" +
                    "    } else {\n" +
                    "        vec2 e = vec2(0.06, 0.0);\n" +
                    "        float h0 = stylizedWaterRipple(vWorldPos.xz * 0.45, u_Time);\n" +
                    "        float hx = stylizedWaterRipple(vWorldPos.xz * 0.45 + e.xy, u_Time);\n" +
                    "        float hy = stylizedWaterRipple(vWorldPos.xz * 0.45 + e.yx, u_Time);\n" +
                    "        tangentNormal = normalize(vec3((h0 - hx), (h0 - hy), 0.55));\n" +
                    "        tangentNormal.xy *= u_NormalStrength * 2.0;\n" +
                    "    }\n" +
                    "\n" +
                    "    if (u_RainIntensity > 0.0) {\n" +
                    "        float rain = getRainRings(vWorldPos.xz * 4.0, u_Time);\n" +
                    "        tangentNormal.xy += vec2(rain, -rain) * u_RainIntensity * 1.2;\n" +
                    "    }\n" +
                    "\n" +
                    "    for (int i = 0; i < 4; i++) {\n" +
                    "        vec4 splash = u_ActiveSplashes[i];\n" +
                    "        if (splash.w >= 0.0) {\n" +
                    "            vec2 dir = vWorldPos.xz - splash.xy;\n" +
                    "            float dist = length(dir);\n" +
                    "            if (dist < splash.z && dist > 0.0001) {\n" +
                    "                float wave = sin((dist - u_Time * 3.0) * 15.0) * (1.0 - dist / splash.z);\n" +
                    "                tangentNormal.xy += normalize(dir) * wave * splash.w * 0.75;\n" +
                    "            }\n" +
                    "        }\n" +
                    "    }\n" +
                    "\n" +
                    "    tangentNormal = normalize(tangentNormal);\n" +
                    "    vec3 N = normalize(vTBN * tangentNormal);\n" +
                    "\n" +
                    "    WaterMaterial mat;\n" +
                    "    mat.deepColor = u_DeepColor;\n" +
                    "    mat.shallowColor = u_ShallowColor;\n" +
                    "    mat.roughness = mix(0.015, 0.09, clamp(u_RainIntensity, 0.0, 1.0));\n" +
                    "    mat.clarity = 1.0;\n" +
                    "    mat.rainIntensity = u_RainIntensity;\n" +
                    "    mat.foamColor = vec3(0.92, 0.96, 1.0);\n" +
                    "\n" +
                    "    vec3 color = shadeWaterPBR(vWorldPos, V, L, N, u_LightIntensity, mat, u_Time, screenUV);\n" +
                    "    color = (color * (2.51 * color + 0.03)) / (color * (2.43 * color + 0.59) + 0.14);\n" +
                    "\n" +
                    "    // Fresnel 控制透明度\n" +
                    "    float NoV = clamp(dot(N, V), 0.001, 1.0);\n" +
                    "    float F0 = 0.02;\n" +
                    "    float fresnel = F0 + (1.0 - F0) * pow(1.0 - NoV, 5.0);\n" +
                    "    float alpha = mix(0.92, 1.0, fresnel);\n" +
                    "\n" +
                    "    FragColor = vec4(pow(color, vec3(1.0 / 2.2)), alpha);\n" +
                    "}";

    public WaterShader() {
        super("WaterShader", VERTEX_SRC, FRAGMENT_SRC);
        bind();
        setUniform("u_NormalScale", 0.06f);
        setUniform("u_NormalStrength", 0.5f);
    }

    public void setMatrices(Matrix4f projection, Matrix4f view) {
        setUniform("u_Projection", projection);
        setUniform("u_View", view);
    }

    public void setScreenSize(int w, int h) {
        setUniform("u_ScreenSize", new Vector2f(w, h));
    }

    public void bindWaterNormal(GLTexture normalTex) {
        if (normalTex != null && normalTex.getId() != 0) {
            normalTex.bind(2);
            setUniform("u_WaterNormalTex", 2);
            setUniform("u_HasNormalMap", 1);
        } else {
            setUniform("u_HasNormalMap", 0);
        }
    }

    public void setRainIntensity(float intensity) {
        setUniform("u_RainIntensity", intensity);
    }

    public void setSplashes(Vector4f[] splashes) {
        for (int i = 0; i < 4; i++) {
            String name = "u_ActiveSplashes[" + i + "]";
            if (splashes != null && i < splashes.length && splashes[i] != null) {
                setUniform(name, splashes[i]);
            } else {
                setUniform(name, new Vector4f(0, 0, 0, -1.0f));
            }
        }
    }

    public void updateUniforms(Vector3f viewPos, Vector3f lightDir, Vector3f lightIntensity,
                               Color deep, Color shallow, float time, float rain) {
        setUniform("u_ViewPos", viewPos);
        setUniform("u_LightDir", lightDir);
        setUniform("u_LightIntensity", lightIntensity);
        setUniform("u_DeepColor", new Vector3f(deep.r, deep.g, deep.b));
        setUniform("u_ShallowColor", new Vector3f(shallow.r, shallow.g, shallow.b));
        setUniform("u_Time", time);
        setRainIntensity(rain);
    }
}