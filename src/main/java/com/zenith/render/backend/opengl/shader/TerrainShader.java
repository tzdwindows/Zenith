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
                    "    float h;\n" +
                    "    vec3 n;\n" +
                    "    computeTerrainHeightAndNormal(worldPos.xz, u_TerrainMat, h, n);\n" +
                    "    worldPos.y += h;\n" +
                    "    vWorldPos = worldPos.xyz;\n" +
                    "    vTexCoord = aTexCoord * u_TerrainMat.uvScale;\n" +
                    "    vNormal = n;\n" +
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
                    "// --- 贴图采样器 --- \n" +
                    "uniform sampler2D u_GrassAlbedo;\n" +
                    "uniform sampler2D u_GrassNormal;\n" +
                    "uniform sampler2D u_GrassRoughness;\n" +
                    "\n" +
                    "uniform sampler2D u_RockAlbedo;\n" +
                    "uniform sampler2D u_RockNormal;\n" +
                    "uniform sampler2D u_RockRoughness;\n" +
                    "\n" +
                    "void main() {\n" +
                    "    vec3 V = normalize(u_ViewPos - vWorldPos);\n" +
                    "    vec3 baseNormal = normalize(vNormal);\n" +
                    "    \n" +
                    "    // --- 1. 使用 terrain.glsl 中的逻辑计算基础材质权重 ---\n" +
                    "    vec3 baseAlbedo;\n" +
                    "    float baseRoughness;\n" +
                    "    float grassMask;\n" +
                    "    evaluateTerrainMaterial(vWorldPos, baseNormal, u_TerrainMat, baseAlbedo, baseRoughness, grassMask);\n" +
                    "\n" +
                    "    // 计算 slope 用于贴图混合（与 glsl 内部逻辑保持一致）\n" +
                    "    float slope = 1.0 - baseNormal.y;\n" +
                    "    float rockWeight = smoothstep(0.15, 0.35, slope);\n" +
                    "    float grassWeight = 1.0 - rockWeight;\n" +
                    "    \n" +
                    "    // --- 2. 贴图采样与切线空间处理 ---\n" +
                    "    vec3 finalAlbedo = baseAlbedo;\n" +
                    "    float finalRoughness = baseRoughness;\n" +
                    "    vec3 finalNormal = baseNormal;\n" +
                    "    \n" +
                    "    if (u_TerrainMat.hasGrassMap || u_TerrainMat.hasRockMap) {\n" +
                    "        // 计算切线空间 TBN\n" +
                    "        vec3 up = abs(baseNormal.y) < 0.999 ? vec3(0, 1, 0) : vec3(0, 0, 1);\n" +
                    "        vec3 tangent = normalize(cross(up, baseNormal));\n" +
                    "        vec3 bitangent = cross(baseNormal, tangent);\n" +
                    "        mat3 TBN = mat3(tangent, bitangent, baseNormal);\n" +
                    "        \n" +
                    "        vec3 texNormalSum = vec3(0.0, 0.0, 1.0);\n" +
                    "        \n" +
                    "        // 采样草地\n" +
                    "        if (u_TerrainMat.hasGrassMap) {\n" +
                    "            vec3 gColor = pow(texture(u_GrassAlbedo, vTexCoord).rgb, vec3(2.2));\n" +
                    "            float gRough = texture(u_GrassRoughness, vTexCoord).r;\n" +
                    "            finalAlbedo = mix(finalAlbedo, gColor, grassWeight * (1.0 - smoothstep(u_TerrainMat.snowHeight - 2.0, u_TerrainMat.snowHeight + 2.0, vWorldPos.y)));\n" +
                    "            finalRoughness = mix(finalRoughness, gRough, grassWeight);\n" +
                    "            \n" +
                    "            if (u_TerrainMat.hasNormalMap) {\n" +
                    "                texNormalSum += (texture(u_GrassNormal, vTexCoord).rgb * 2.0 - 1.0) * grassWeight;\n" +
                    "            }\n" +
                    "        }\n" +
                    "        \n" +
                    "        // 采样岩石\n" +
                    "        if (u_TerrainMat.hasRockMap) {\n" +
                    "            vec3 rColor = pow(texture(u_RockAlbedo, vTexCoord).rgb, vec3(2.2));\n" +
                    "            float rRough = texture(u_RockRoughness, vTexCoord).r;\n" +
                    "            finalAlbedo = mix(finalAlbedo, rColor, rockWeight);\n" +
                    "            finalRoughness = mix(finalRoughness, rRough, rockWeight);\n" +
                    "            \n" +
                    "            if (u_TerrainMat.hasNormalMap) {\n" +
                    "                texNormalSum += (texture(u_RockNormal, vTexCoord).rgb * 2.0 - 1.0) * rockWeight;\n" +
                    "            }\n" +
                    "        }\n" +
                    "        \n" +
                    "        if (u_TerrainMat.hasNormalMap) {\n" +
                    "            finalNormal = normalize(TBN * normalize(texNormalSum));\n" +
                    "        }\n" +
                    "    }\n" +
                    "\n" +
                    "    // --- 3. 核心光照计算 (参数现在匹配 shadeTerrain 的 9 个参数了) ---\n" +
                    "    vec3 color = shadeTerrain(\n" +
                    "        vWorldPos, V, u_SunDir, u_SunIntensity, \n" +
                    "        u_TerrainMat, finalAlbedo, finalNormal, finalRoughness, \n" +
                    "        grassMask\n" +
                    "    );\n" +
                    "\n" +
                    "    // --- 4. 后处理: 雾效与色调映射 ---\n" +
                    "    float dist = length(u_ViewPos - vWorldPos);\n" +
                    "    float fogFactor = 1.0 - exp(-dist * 0.0003);\n" +
                    "    // 简化的雾色逻辑，实际可从 evaluateAtmosphericAmbient 获取\n" +
                    "    float sunY = u_SunDir.y;\n" +
                    "\n" +
                    "// 夜晚 / 日落 / 白天\n" +
                    "vec3 nightFog  = vec3(0.01, 0.02, 0.05);\n" +
                    "vec3 sunsetFog = vec3(0.6, 0.3, 0.15);\n" +
                    "vec3 dayFog    = vec3(0.5, 0.6, 0.7);\n" +
                    "\n" +
                    "vec3 fogColor;\n" +
                    "if (sunY < 0.0) {\n" +
                    "    fogColor = mix(nightFog, sunsetFog, smoothstep(-0.2, 0.0, sunY));\n" +
                    "} else {\n" +
                    "    fogColor = mix(sunsetFog, dayFog, smoothstep(0.0, 0.3, sunY));\n" +
                    "}\n" +
                    "    color = mix(color, fogColor, clamp(fogFactor, 0.0, 1.0));\n" +
                    "\n" +
                    "    // ACES Tone Mapping\n" +
                    "    vec3 mapped = color * 0.8;\n" +
                    "    mapped = (mapped * (2.51 * mapped + 0.03)) / (mapped * (2.43 * mapped + 0.59) + 0.14);\n" +
                    "    FragColor = vec4(pow(mapped, vec3(1.0 / 2.2)), 1.0);\n" +
                    "}";

    public TerrainShader() {
        super("TerrainShader", VERTEX_SRC, FRAGMENT_SRC);
    }

    public void setMaterial(TerrainMaterialParams params) {
        this.setUniform("u_TerrainMat.hasGrassMap", params.hasGrassMap);
        this.setUniform("u_TerrainMat.hasRockMap", params.hasRockMap);
        this.setUniform("u_TerrainMat.hasNormalMap", params.hasNormalMap);
        this.setUniform("u_TerrainMat.uvScale", params.uvScale);

        this.setUniform("u_TerrainMat.grassColor", params.grassColor);
        this.setUniform("u_TerrainMat.rockColor", params.rockColor);
        this.setUniform("u_TerrainMat.snowColor", params.snowColor);

        this.setUniform("u_TerrainMat.amplitude", params.amplitude);
        this.setUniform("u_TerrainMat.frequency", params.frequency);
        this.setUniform("u_TerrainMat.snowHeight", params.snowHeight);
    }

    /**
     * 绑定草地材质组 (纹理单元 0, 1, 2)
     */
    public void bindGrassMaps(GLTexture albedo, GLTexture normal, GLTexture roughness) {
        if (albedo != null) { albedo.bind(0); this.setUniform("u_GrassAlbedo", 0); }
        if (normal != null) { normal.bind(1); this.setUniform("u_GrassNormal", 1); }
        if (roughness != null) { roughness.bind(2); this.setUniform("u_GrassRoughness", 2); }
    }

    /**
     * 绑定岩石材质组 (纹理单元 3, 4, 5)
     */
    public void bindRockMaps(GLTexture albedo, GLTexture normal, GLTexture roughness) {
        if (albedo != null) { albedo.bind(3); this.setUniform("u_RockAlbedo", 3); }
        if (normal != null) { normal.bind(4); this.setUniform("u_RockNormal", 4); }
        if (roughness != null) { roughness.bind(5); this.setUniform("u_RockRoughness", 5); }
    }

    public static class TerrainMaterialParams {

        /** 是否启用草地贴图。若为 false，则使用 grassColor 进行程序化着色 */
        public boolean hasGrassMap = false;

        /** 是否启用岩石贴图。若为 false，则使用 rockColor */
        public boolean hasRockMap = false;

        /** * 是否启用切线空间法线贴图。
         * 只有在 bindGrassMaps/bindRockMaps 传入了 Normal 贴图时才应设为 true。
         * 开启后会显著增强地形的表面凹凸质感。
         */
        public boolean hasNormalMap = false;

        /** * 贴图坐标(UV)的缩放倍率。
         * 数值越大，贴图在单位面积内重复次数越多（显得越细腻）。
         * 建议范围: 1.0 - 20.0
         */
        public float uvScale = 1.0f;

        /** 草地基础色（或贴图未开启时的填充色） */
        public Vector3f grassColor = new Vector3f(0.26f, 0.38f, 0.14f);

        /** 岩石基础色（通常用于陡峭的坡面） */
        public Vector3f rockColor = new Vector3f(0.35f, 0.3f, 0.25f);

        /** 雪地颜色（用于高海拔区域） */
        public Vector3f snowColor = new Vector3f(0.95f, 0.98f, 1.0f);

        /** * 地形振幅（高度）。
         * 决定了山峰的最大高度。
         * 默认值 85.0f 对应约 85 个世界单位的高度。
         */
        public float amplitude = 85.0f;

        /** * 噪声频率。
         * 决定了地形的起伏频率（山头的密集程度）。
         * 数值越小，地形越平缓开阔；数值越大，地形越破碎。
         * 建议范围: 0.0001 - 0.01
         */
        public float frequency = 0.0025f;

        /** * 雪线高度。
         * 地形 Y 坐标超过此值时开始向雪地材质过渡。
         * 注意：如果角色坐标 Y 高于此值，视野内将看到大面积积雪。
         */
        public float snowHeight = 50.0f;
    }
}