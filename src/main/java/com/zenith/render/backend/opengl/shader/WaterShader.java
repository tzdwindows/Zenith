package com.zenith.render.backend.opengl.shader;

import com.zenith.common.math.Color;
import org.joml.Vector3f;
import org.joml.Vector4f;

public class WaterShader extends GLShader {

    private static final String VERTEX_SRC =
            "#version 330 core\n" +
                    "#include \"water.glsl\"\n" + // 假设 water.glsl 包含 computeGerstner 函数
                    "\n" +
                    "layout (location = 0) in vec3 aPos;\n" +
                    "layout (location = 1) in vec3 aNormal;\n" +
                    "layout (location = 2) in vec2 aTexCoord;\n" +
                    "\n" +
                    "uniform mat4 u_ViewProjection;\n" +
                    "uniform mat4 u_Model;\n" +
                    "uniform float u_Time;\n" +
                    "\n" +
                    "out vec2 vTexCoord;\n" +
                    "out vec3 vWorldPos;\n" +
                    "out vec3 vNormal;\n" +
                    "out float vWaveHeight;\n" +
                    "\n" +
                    "void main() {\n" +
                    "    vTexCoord = aTexCoord * 20.0;\n" +
                    "    vec3 pos = aPos;\n" +
                    "    \n" +
                    "    vec3 tangent = vec3(1.0, 0.0, 0.0);\n" +
                    "    vec3 binormal = vec3(0.0, 0.0, 1.0);\n" +
                    "    \n" +
                    "    // 叠加 6 层波浪，方向、波长、陡度各不相同，彻底打破整齐感\n" +
                    "    // 参数格式: vec4(方向X, 方向Z, 陡度, 波长)\n" +
                    "    float h = 0.0;\n" +
                    "    h += computeGerstner(pos.xz, u_Time, vec4(1.0, 0.1, 0.15, 30.0), tangent, binormal);\n" +
                    "    h += computeGerstner(pos.xz, u_Time * 1.2, vec4(-0.7, 0.5, 0.1, 15.0), tangent, binormal);\n" +
                    "    h += computeGerstner(pos.xz, u_Time * 0.9, vec4(0.3, -0.8, 0.08, 10.0), tangent, binormal);\n" +
                    "    h += computeGerstner(pos.xz, u_Time * 1.5, vec4(-0.2, -0.7, 0.05, 7.0), tangent, binormal);\n" +
                    "    h += computeGerstner(pos.xz, u_Time * 2.1, vec4(0.8, 0.9, 0.03, 4.0), tangent, binormal);\n" +
                    "    h += computeGerstner(pos.xz, u_Time * 2.8, vec4(0.0, 1.0, 0.02, 2.5), tangent, binormal);\n" +
                    "    \n" +
                    "    pos.y += h;\n" +
                    "    vWaveHeight = h;\n" + // 传递高度用于计算白沫
                    "\n" +
                    "    vec4 worldPos = u_Model * vec4(pos, 1.0);\n" +
                    "    vWorldPos = worldPos.xyz;\n" +
                    "    // 计算物理精确的法线\n" +
                    "    vNormal = normalize(cross(binormal, tangent));\n" +
                    "    \n" +
                    "    gl_Position = u_ViewProjection * worldPos;\n" +
                    "}";

    private static final String FRAGMENT_SRC =
            "#version 330 core\n" +
                    "#include \"water.glsl\"\n" +
                    "\n" +
                    "in vec2 vTexCoord;\n" +
                    "in vec3 vWorldPos;\n" +
                    "in vec3 vNormal;\n" +
                    "in float vWaveHeight;\n" +
                    "out vec4 FragColor;\n" +
                    "\n" +
                    "uniform vec3 u_ViewPos;\n" +
                    "uniform vec3 u_LightDir;\n" +
                    "uniform vec3 u_LightIntensity;\n" +
                    "uniform vec3 u_DeepColor;\n" +
                    "uniform vec3 u_ShallowColor;\n" +
                    "uniform float u_Time;\n" +
                    "uniform float u_RainIntensity;\n" +
                    "\n" +
                    "// 简单的伪随机噪声，用于模拟微小波纹\n" +
                    "float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123); }\n" +
                    "\n" +
                    "void main() {\n" +
                    "    vec3 V = normalize(u_ViewPos - vWorldPos);\n" +
                    "    vec3 L = normalize(u_LightDir);\n" +
                    "    \n" +
                    "    // --- 1. 微观法线扰动 (波光粼粼的关键) ---\n" +
                    "    // 使用 sin 函数模拟细碎的表面起伏，增强高光细节\n" +
                    "    float fineNoise = sin(vWorldPos.x * 5.0 + u_Time) * cos(vWorldPos.z * 5.0 - u_Time);\n" +
                    "    vec3 noiseNormal = normalize(vNormal + fineNoise * 0.15);\n" +
                    "    \n" +
                    "    // --- 2. 菲涅尔效应 (Fresnel) ---\n" +
                    "    // 视线越浅反射越强，垂直看水面越透明\n" +
                    "    float F0 = 0.02; \n" +
                    "    float fresnel = F0 + (1.0 - F0) * pow(1.0 - max(dot(noiseNormal, V), 0.0), 5.0);\n" +
                    "    \n" +
                    "    // --- 3. 水体颜色计算 ---\n" +
                    "    WaterMaterial mat;\n" +
                    "    mat.deepColor = u_DeepColor;\n" +
                    "    mat.shallowColor = u_ShallowColor;\n" +
                    "    mat.roughness = 0.03; // 非常光滑，产生清晰的高光点\n" +
                    "    mat.metallic = 0.0;\n" +
                    "    mat.reflectance = 1.0;\n" +
                    "    mat.ior = 1.333;\n" +
                    "    mat.clarity = 1.2;\n" +
                    "    mat.rainIntensity = u_RainIntensity;\n" +
                    "\n" +
                    "    // 基础光照计算\n" +
                    "    vec3 waterColor = shadeWater(vWorldPos, V, L, u_LightIntensity, mat, 1, u_Time);\n" +
                    "    \n" +
                    "    // --- 4. 波峰白沫 (Foam) ---\n" +
                    "    // 当波浪高度超过一定值时，混合白色\n" +
                    "    float foamThreshold = 0.8; \n" +
                    "    float foam = smoothstep(foamThreshold, foamThreshold + 0.5, vWaveHeight);\n" +
                    "    vec3 finalColor = mix(waterColor, vec3(0.9, 0.95, 1.0) * length(u_LightIntensity), foam * 0.4);\n" +
                    "    \n" +
                    "    // --- 5. 最终混合 ---\n" +
                    "    // 模拟天空反射的增强\n" +
                    "    finalColor += fresnel * u_LightIntensity * 0.2;\n" +
                    "    \n" +
                    "    // 透明度随菲涅尔变化\n" +
                    "    float alpha = mix(0.7, 1.0, fresnel);\n" +
                    "    \n" +
                    "    FragColor = vec4(finalizeColor(finalColor), alpha);\n" +
                    "}";

    public WaterShader() {
        super("WaterShader", VERTEX_SRC, FRAGMENT_SRC);
    }

    public void setRainIntensity(float intensity) {
        this.setUniform("u_RainIntensity", intensity);
    }

    public void setSplashes(Vector4f[] splashes) {
        for (int i = 0; i < 4; i++) {
            String name = "u_ActiveSplashes[" + i + "]";
            if (i < splashes.length && splashes[i] != null) {
                this.setUniform(name, splashes[i]);
            } else {
                this.setUniform(name, new Vector4f(0, 0, 0, -1.0f));
            }
        }
    }

    public void updateUniforms(Vector3f viewPos, Vector3f lightDir, Vector3f lightIntensity, Color deep, Color shallow, float time, float rain) {
        this.setUniform("u_ViewPos", viewPos);
        this.setUniform("u_LightDir", lightDir.normalize());
        this.setUniform("u_LightIntensity", lightIntensity);

        this.setUniform("u_DeepColor", new Vector3f(deep.r, deep.g, deep.b));
        this.setUniform("u_ShallowColor", new Vector3f(shallow.r, shallow.g, shallow.b));
        this.setUniform("u_Time", time);
        this.setUniform("u_RainIntensity", rain);
    }
}