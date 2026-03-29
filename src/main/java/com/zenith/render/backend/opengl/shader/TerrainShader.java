package com.zenith.render.backend.opengl.shader;

import com.zenith.render.backend.opengl.texture.GLTexture;
import org.joml.Vector3f;

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
                    "out vec3 vNormal;\n" +
                    "\n" +
                    "void main() {\n" +
                    "    vec4 worldPos = u_Model * vec4(aPos, 1.0);\n" +
                    "    float h; vec3 n;\n" +
                    "    computeTerrainHeightAndNormal(worldPos.xz, u_TerrainMat, h, n);\n" +
                    "    worldPos.y += h;\n" +
                    "    vWorldPos = worldPos.xyz;\n" +
                    "    vTexCoord = aTexCoord * u_TerrainMat.uvScale;\n" +
                    "    vNormal = n;\n" +
                    "    // 强制使用矩阵，防止被剔除\n" +
                    "    gl_Position = u_ViewProjection * worldPos;\n" +
                    "}";

    private static final String FRAGMENT_SRC =
            "#version 330 core\n" +
                    "#include \"terrain.glsl\"\n" +
                    "\n" +
                    "in vec3 vWorldPos;\n" +
                    "in vec2 vTexCoord;\n" +
                    "in vec3 vNormal;\n" +
                    "out vec4 FragColor;\n" +
                    "\n" +
                    "uniform vec3 u_ViewPos;\n" +
                    "uniform vec3 u_SunDir;\n" +
                    "uniform vec3 u_SunIntensity;\n" +
                    "uniform TerrainMaterial u_TerrainMat;\n" +
                    "\n" +
                    "uniform sampler2D u_GrassAlbedo;\n" +
                    "uniform sampler2D u_GrassNormal;\n" +
                    "uniform sampler2D u_GrassRoughness;\n" +
                    "uniform sampler2D u_RockAlbedo;\n" +
                    "uniform sampler2D u_RockNormal;\n" +
                    "uniform sampler2D u_RockRoughness;\n" +
                    "\n" +
                    "void main() {\n" +
                    "    vec3 V = normalize(u_ViewPos - vWorldPos);\n" +
                    "    vec3 baseNormal = normalize(vNormal);\n" +
                    "    vec3 baseAlbedo; float baseRoughness; float grassMask;\n" +
                    "    evaluateTerrainMaterial(vWorldPos, baseNormal, u_TerrainMat, baseAlbedo, baseRoughness, grassMask);\n" +
                    "\n" +
                    "    float slope = 1.0 - baseNormal.y;\n" +
                    "    float rockWeight = smoothstep(0.15, 0.35, slope);\n" +
                    "    float grassWeight = 1.0 - rockWeight;\n" +
                    "    \n" +
                    "    vec3 finalAlbedo = baseAlbedo;\n" +
                    "    float finalRoughness = baseRoughness;\n" +
                    "    vec3 finalNormal = baseNormal;\n" +
                    "    vec3 texNormalSum = vec3(0.0, 0.0, 1.0);\n" +
                    "    \n" +
                    "    // 强制采样，防止采样器被剔除\n" +
                    "    vec3 gCol = texture(u_GrassAlbedo, vTexCoord).rgb;\n" +
                    "    vec3 rCol = texture(u_RockAlbedo, vTexCoord).rgb;\n" +
                    "    \n" +
                    "    if (u_TerrainMat.hasGrassMap > 0.5) {\n" +
                    "        float snowMix = 1.0 - smoothstep(u_TerrainMat.snowHeight - 2.0, u_TerrainMat.snowHeight + 2.0, vWorldPos.y);\n" +
                    "        finalAlbedo = mix(finalAlbedo, pow(gCol, vec3(2.2)), grassWeight * snowMix);\n" +
                    "        finalRoughness = mix(finalRoughness, texture(u_GrassRoughness, vTexCoord).r, grassWeight);\n" +
                    "        if (u_TerrainMat.hasNormalMap > 0.5) texNormalSum += (texture(u_GrassNormal, vTexCoord).rgb * 2.0 - 1.0) * grassWeight;\n" +
                    "    }\n" +
                    "    \n" +
                    "    if (u_TerrainMat.hasRockMap > 0.5) {\n" +
                    "        finalAlbedo = mix(finalAlbedo, pow(rCol, vec3(2.2)), rockWeight);\n" +
                    "        finalRoughness = mix(finalRoughness, texture(u_RockRoughness, vTexCoord).r, rockWeight);\n" +
                    "        if (u_TerrainMat.hasNormalMap > 0.5) texNormalSum += (texture(u_RockNormal, vTexCoord).rgb * 2.0 - 1.0) * rockWeight;\n" +
                    "    }\n" +
                    "    \n" +
                    "    if (u_TerrainMat.hasNormalMap > 0.5 && (u_TerrainMat.hasGrassMap > 0.5 || u_TerrainMat.hasRockMap > 0.5)) {\n" +
                    "        vec3 up = abs(baseNormal.y) < 0.999 ? vec3(0,1,0) : vec3(0,0,1);\n" +
                    "        mat3 TBN = mat3(normalize(cross(up, baseNormal)), cross(baseNormal, normalize(cross(up, baseNormal))), baseNormal);\n" +
                    "        finalNormal = normalize(TBN * normalize(texNormalSum));\n" +
                    "    }\n" +
                    "\n" +
                    "    vec3 color = shadeTerrain(vWorldPos, V, u_SunDir, u_SunIntensity, u_TerrainMat, finalAlbedo, finalNormal, finalRoughness, grassMask);\n" +
                    "\n" +
                    "    // --- 终极 Usage Guard ---\n" +
                    "    float guard = (u_SunIntensity.x + u_ViewPos.x + u_SunDir.x + u_TerrainMat.amplitude + u_TerrainMat.frequency + u_TerrainMat.snowHeight) * 0.0000001;\n" +
                    "    \n" +
                    "    vec3 mapped = color * 0.8;\n" +
                    "    mapped = (mapped * (2.51 * mapped + 0.03)) / (mapped * (2.43 * mapped + 0.59) + 0.14);\n" +
                    "    FragColor = vec4(pow(mapped, vec3(1.0 / 2.2)) + guard, 1.0);\n" +
                    "}";

    public TerrainShader() {
        super("TerrainShader", VERTEX_SRC, FRAGMENT_SRC);
    }

    public void setMaterial(TerrainMaterialParams params) {
        // 传递 float 值而非 boolean，解决结构体映射问题
        this.setUniform("u_TerrainMat.hasGrassMap", params.hasGrassMap ? 1.0f : 0.0f);
        this.setUniform("u_TerrainMat.hasRockMap", params.hasRockMap ? 1.0f : 0.0f);
        this.setUniform("u_TerrainMat.hasNormalMap", params.hasNormalMap ? 1.0f : 0.0f);
        this.setUniform("u_TerrainMat.uvScale", params.uvScale);
        this.setUniform("u_TerrainMat.grassColor", params.grassColor);
        this.setUniform("u_TerrainMat.rockColor", params.rockColor);
        this.setUniform("u_TerrainMat.snowColor", params.snowColor);
        this.setUniform("u_TerrainMat.amplitude", params.amplitude);
        this.setUniform("u_TerrainMat.frequency", params.frequency);
        this.setUniform("u_TerrainMat.snowHeight", params.snowHeight);
    }

    public void bindGrassMaps(GLTexture albedo, GLTexture normal, GLTexture roughness) {
        this.setUniform("u_GrassAlbedo", 0);
        this.setUniform("u_GrassNormal", 1);
        this.setUniform("u_GrassRoughness", 2);
        if (albedo != null) albedo.bind(0);
        if (normal != null) normal.bind(1);
        if (roughness != null) roughness.bind(2);
    }

    public void bindRockMaps(GLTexture albedo, GLTexture normal, GLTexture roughness) {
        this.setUniform("u_RockAlbedo", 3);
        this.setUniform("u_RockNormal", 4);
        this.setUniform("u_RockRoughness", 5);
        if (albedo != null) albedo.bind(3);
        if (normal != null) normal.bind(4);
        if (roughness != null) roughness.bind(5);
    }

    public static class TerrainMaterialParams {
        public boolean hasGrassMap = false;
        public boolean hasRockMap = false;
        public boolean hasNormalMap = false;
        public float uvScale = 1.0f;
        public Vector3f grassColor = new Vector3f(0.26f, 0.38f, 0.14f);
        public Vector3f rockColor = new Vector3f(0.35f, 0.3f, 0.25f);
        public Vector3f snowColor = new Vector3f(0.95f, 0.98f, 1.0f);
        public float amplitude = 85.0f;
        public float frequency = 0.0025f;
        public float snowHeight = 50.0f;
    }
}