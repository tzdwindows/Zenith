package com.zenith.render.backend.opengl.shader;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * TerrainShader: 基于程序化噪声生成的高度场地形着色器
 * 支持：分形布朗运动(fBm)高度图、坡度感应材质混合、PBR光照
 */
public class TerrainShader extends GLShader {

    private static final String VERTEX_SRC =
            "#version 330 core\n" +
                    "layout (location = 0) in vec3 aPos;\n" +
                    "layout (location = 2) in vec2 aTexCoord;\n" +
                    "\n" +
                    "#include \"terrain.glsl\"\n" +
                    "\n" +
                    "uniform mat4 u_ViewProjection;\n" +
                    "uniform mat4 u_Model;\n" +
                    "uniform TerrainMaterial u_TerrainMat;\n" +
                    "\n" +
                    "out vec3 vWorldPos;\n" +
                    "out vec2 vTexCoord;\n" +
                    "\n" +
                    "void main() {\n" +
                    "    vec4 worldPos = u_Model * vec4(aPos, 1.0);\n" +
                    "    \n" +
                    "    // 1. 在顶点着色器中应用程序化高度位移\n" +
                    "    // 注意：aPos.y 通常为 0，高度完全由 noise 决定\n" +
                    "    worldPos.y += computeTerrainHeight(worldPos.xz, u_TerrainMat);\n" +
                    "    \n" +
                    "    vWorldPos = worldPos.xyz;\n" +
                    "    vTexCoord = aTexCoord * u_TerrainMat.uvScale;\n" +
                    "    gl_Position = u_ViewProjection * worldPos;\n" +
                    "}";

    private static final String FRAGMENT_SRC =
            "#version 330 core\n" +
                    "#include \"terrain.glsl\"\n" +
                    "\n" +
                    "in vec3 vWorldPos;\n" +
                    "in vec2 vTexCoord;\n" +
                    "out vec4 FragColor;\n" +
                    "\n" +
                    "uniform vec3 u_ViewPos;\n" +
                    "uniform vec3 u_SunDir;\n" +
                    "uniform vec3 u_SunIntensity;\n" +
                    "uniform TerrainMaterial u_TerrainMat;\n" +
                    "\n" +
                    "void main() {\n" +
                    "    vec3 V = normalize(u_ViewPos - vWorldPos);\n" +
                    "    vec3 color = shadeTerrain(\n" +
                    "        vWorldPos, V, u_SunDir, u_SunIntensity, \n" +
                    "        u_TerrainMat, vec3(0.0), vec3(0,1,0), 1.0\n" +
                    "    );\n" +
                    "\n" +
                    "    // 【电影级 ACES Tone Mapping】：防止高光泛白，保留色彩细节\n" +
                    "    vec3 mapped = color * 0.8; // 0.8 是曝光度\n" +
                    "    mapped = (mapped * (2.51 * mapped + 0.03)) / (mapped * (2.43 * mapped + 0.59) + 0.14);\n" +
                    "    \n" +
                    "    // Gamma 校正\n" +
                    "    mapped = pow(mapped, vec3(1.0 / 2.2));\n" +
                    "    \n" +
                    "    FragColor = vec4(mapped, 1.0);\n" +
                    "}";

    public TerrainShader() {
        super("TerrainShader", VERTEX_SRC, FRAGMENT_SRC);
    }

    /**
     * 快速设置地形材质参数
     */
    public void setMaterial(TerrainMaterialParams params) {
        this.setUniform("u_TerrainMat.hasTexture", params.hasTexture);
        this.setUniform("u_TerrainMat.uvScale", params.uvScale);
        this.setUniform("u_TerrainMat.grassColor", params.grassColor);
        this.setUniform("u_TerrainMat.rockColor", params.rockColor);
        this.setUniform("u_TerrainMat.snowColor", params.snowColor);
        this.setUniform("u_TerrainMat.amplitude", params.amplitude);
        this.setUniform("u_TerrainMat.frequency", params.frequency);
        this.setUniform("u_TerrainMat.snowHeight", params.snowHeight);
    }

    public static class TerrainMaterialParams {
        public boolean hasTexture = false;
        public float uvScale = 1.0f;
        public Vector3f grassColor = new Vector3f(0.15f, 0.35f, 0.1f);
        public Vector3f rockColor = new Vector3f(0.35f, 0.3f, 0.25f);
        public Vector3f snowColor = new Vector3f(0.95f, 0.98f, 1.0f);
        public float amplitude = 25.0f;
        public float frequency = 0.02f;
        public float snowHeight = 15.0f;
    }
}