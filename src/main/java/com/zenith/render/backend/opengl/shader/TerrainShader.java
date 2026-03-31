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
                    "vec3 getSafeNormal(sampler2D tex, vec2 uv) {\n" +
                    "    vec3 raw = texture(tex, uv).rgb;\n" +
                    "    if (length(raw) < 0.1) return vec3(0.0, 0.0, 1.0);\n" +
                    "    vec2 xy = raw.xy * 2.0 - 1.0;\n" +
                    "    float z = sqrt(max(1.0 - dot(xy, xy), 0.05));\n" +
                    "    return normalize(vec3(xy, z));\n" +
                    "}\n" +
                    "\n" +
                    "void main() {\n" +
                    "    vec3 V = normalize(u_ViewPos - vWorldPos);\n" +
                    "    vec3 baseNormal = normalize(vNormal);\n" +
                    "\n" +
                    "    float slope = 1.0 - baseNormal.y;\n" +
                    "    float rockWeight = smoothstep(0.15, 0.35, slope);\n" +
                    "    float grassWeight = 1.0 - rockWeight;\n" +
                    "    float heightWeight = smoothstep(u_TerrainMat.snowHeight - 2.0, u_TerrainMat.snowHeight + 2.0, vWorldPos.y);\n" +
                    "    float snowSlopeRetain = 1.0 - smoothstep(0.35, 0.55, slope);\n" +
                    "    float snowWeight = heightWeight * snowSlopeRetain;\n" +
                    "    float grassMask = grassWeight * (1.0 - snowWeight);\n" +
                    "\n" +
                    "    // --- 1. Albedo 安全提取 (修复 pow 产生 NaN 的致命问题) --- \n" +
                    "    vec3 texGrass = texture(u_GrassAlbedo, vTexCoord).rgb;\n" +
                    "    vec3 texRock  = texture(u_RockAlbedo, vTexCoord).rgb;\n" +
                    "    \n" +
                    "    // 降低判定阈值，防止把暗部贴图像素误判为没加载\n" +
                    "    vec3 gCol = (length(texGrass) > 0.01 && u_TerrainMat.hasGrassMap > 0.5) ? texGrass : u_TerrainMat.grassColor;\n" +
                    "    vec3 rCol = (length(texRock) > 0.01 && u_TerrainMat.hasRockMap > 0.5)   ? texRock  : u_TerrainMat.rockColor;\n" +
                    "    \n" +
                    "    // 【绝对防御】：强制 max(color, 0.0) 截断所有的负数值像素，防止 pow 函数爆炸产生 NaN！\n" +
                    "    vec3 linGrass = pow(max(gCol, vec3(0.0)), vec3(2.2));\n" +
                    "    vec3 linRock  = pow(max(rCol, vec3(0.0)), vec3(2.2));\n" +
                    "    vec3 linSnow  = pow(max(u_TerrainMat.snowColor, vec3(0.0)), vec3(2.2));\n" +
                    "    \n" +
                    "    vec3 finalAlbedo = mix(linGrass, linRock, rockWeight);\n" +
                    "    finalAlbedo = mix(finalAlbedo, linSnow, snowWeight);\n" +
                    "\n" +
                    "    // --- 2. 粗糙度安全提取 --- \n" +
                    "    float rGrassTex = texture(u_GrassRoughness, vTexCoord).r;\n" +
                    "    float rRockTex  = texture(u_RockRoughness, vTexCoord).r;\n" +
                    "    float rGrass = (rGrassTex > 0.01 && u_TerrainMat.hasGrassMap > 0.5) ? rGrassTex : 0.85;\n" +
                    "    float rRock  = (rRockTex > 0.01 && u_TerrainMat.hasRockMap > 0.5)   ? rRockTex  : 0.65;\n" +
                    "    float finalRoughness = mix(rGrass, rRock, rockWeight);\n" +
                    "    finalRoughness = mix(finalRoughness, 0.45, snowWeight);\n" +
                    "\n" +
                    "    // --- 3. 法线处理与阴影终结者修复 --- \n" +
                    "    vec3 tNormalGrass = vec3(0,0,1);\n" +
                    "    vec3 tNormalRock  = vec3(0,0,1);\n" +
                    "    if (u_TerrainMat.hasNormalMap > 0.5) {\n" +
                    "        tNormalGrass = getSafeNormal(u_GrassNormal, vTexCoord);\n" +
                    "        tNormalRock  = getSafeNormal(u_RockNormal, vTexCoord);\n" +
                    "    }\n" +
                    "    vec3 mixedTexNormal = normalize(mix(tNormalGrass, tNormalRock, rockWeight));\n" +
                    "    mixedTexNormal = normalize(mix(mixedTexNormal, vec3(0,0,1), snowWeight));\n" +
                    "\n" +
                    "    vec3 helperUp = abs(baseNormal.y) < 0.999 ? vec3(0.0, 1.0, 0.0) : vec3(0.0, 0.0, 1.0);\n" +
                    "    vec3 tangent = normalize(cross(helperUp, baseNormal));\n" +
                    "    vec3 bitangent = normalize(cross(baseNormal, tangent));\n" +
                    "    mat3 TBN = mat3(tangent, bitangent, baseNormal);\n" +
                    "    vec3 finalNormal = normalize(TBN * mixedTexNormal);\n" +
                    "\n" +
                    "    // 【绝对防御】：防止法线贴图将法线弯曲至背对相机！(Shadow Terminator Fix)\n" +
                    "    // 如果法线和视线的点积小于极小值，说明法线背对我们了，强制把它拉回来一点，防止 BRDF 崩溃。\n" +
                    "    float NoV_fix = dot(finalNormal, V);\n" +
                    "    if (NoV_fix < 0.001) {\n" +
                    "        finalNormal = normalize(finalNormal + V * (0.001 - NoV_fix));\n" +
                    "    }\n" +
                    "\n" +
                    "    // --- 4. 渲染核心 --- \n" +
                    "    vec3 color = shadeTerrain(vWorldPos, V, u_SunDir, u_SunIntensity, u_TerrainMat, finalAlbedo, finalNormal, finalRoughness, grassMask);\n" +
                    "\n" +
                    "    float exposure = 3.5; \n" +
                    "    vec3 mapped = color * exposure;\n" +
                    "    mapped = (mapped * (2.51 * mapped + 0.03)) / (mapped * (2.43 * mapped + 0.59) + 0.14);\n" +
                    "    \n" +
                    "    // 最后的安全收尾保护\n" +
                    "    float guard = (u_SunIntensity.x + u_ViewPos.x + u_TerrainMat.amplitude) * 0.00000001;\n" +
                    "    FragColor = vec4(pow(max(mapped, vec3(0.0)), vec3(1.0 / 2.2)) + abs(guard)*0.0, 1.0);\n" +
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