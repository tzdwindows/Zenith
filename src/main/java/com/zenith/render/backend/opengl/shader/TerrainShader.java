package com.zenith.render.backend.opengl.shader;

import com.zenith.render.backend.opengl.texture.GLTexture;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * TerrainShader
 * - 地形高度 + 法线
 * - 多光源照明
 * - 支持草地/岩石贴图
 */
public class TerrainShader extends GLShader {

    private static final String VERTEX_SRC = """
        #version 330 core
        layout (location = 0) in vec3 aPos;
        layout (location = 2) in vec2 aTexCoord;

        #include "terrain.glsl"

        uniform mat4 u_ViewProjection;
        uniform mat4 u_Model;
        uniform TerrainMaterial u_TerrainMat;

        out vec3 vWorldPos;
        out vec2 vTexCoord;
        out vec3 vNormal;

        void main() {
            vec4 worldPos = u_Model * vec4(aPos, 1.0);

            float h;
            vec3 n;
            computeTerrainHeightAndNormal(worldPos.xz, u_TerrainMat, h, n);

            worldPos.y += h;

            vWorldPos = worldPos.xyz;
            vTexCoord = aTexCoord * u_TerrainMat.uvScale;
            vNormal = n;

            gl_Position = u_ViewProjection * worldPos;
        }
    """;

    private static final String FRAGMENT_SRC = """
        #version 330 core

        #include "terrain.glsl"

        in vec3 vWorldPos;
        in vec2 vTexCoord;
        in vec3 vNormal;

        out vec4 FragColor;

        uniform vec3 u_ViewPos;
        uniform TerrainMaterial u_TerrainMat;

        uniform sampler2D u_GrassAlbedo;
        uniform sampler2D u_GrassNormal;
        uniform sampler2D u_GrassRoughness;

        uniform sampler2D u_RockAlbedo;
        uniform sampler2D u_RockNormal;
        uniform sampler2D u_RockRoughness;

        vec3 getSafeNormal(sampler2D tex, vec2 uv) {
            vec3 raw = texture(tex, uv).rgb;

            if (length(raw) < 0.1) {
                return vec3(0.0, 0.0, 1.0);
            }

            vec2 xy = raw.xy * 2.0 - 1.0;
            float z = sqrt(max(1.0 - dot(xy, xy), 0.05));
            return normalize(vec3(xy, z));
        }

        void main() {
            vec3 V = normalize(u_ViewPos - vWorldPos);
            vec3 baseNormal = normalize(vNormal);

            float slope = 1.0 - baseNormal.y;
            float rockWeight = smoothstep(0.15, 0.35, slope);
            float grassWeight = 1.0 - rockWeight;

            float heightWeight = smoothstep(
                u_TerrainMat.snowHeight - 2.0,
                u_TerrainMat.snowHeight + 2.0,
                vWorldPos.y
            );

            float snowSlopeRetain = 1.0 - smoothstep(0.35, 0.55, slope);
            float snowWeight = heightWeight * snowSlopeRetain;

            float grassMask = grassWeight * (1.0 - snowWeight);

            vec3 texGrass = texture(u_GrassAlbedo, vTexCoord).rgb;
            vec3 texRock  = texture(u_RockAlbedo, vTexCoord).rgb;

            vec3 gCol = (length(texGrass) > 0.01 && u_TerrainMat.hasGrassMap > 0.5)
                ? texGrass : u_TerrainMat.grassColor;

            vec3 rCol = (length(texRock) > 0.01 && u_TerrainMat.hasRockMap > 0.5)
                ? texRock : u_TerrainMat.rockColor;

            vec3 linGrass = pow(max(gCol, vec3(0.0)), vec3(2.2));
            vec3 linRock  = pow(max(rCol, vec3(0.0)), vec3(2.2));
            vec3 linSnow  = pow(max(u_TerrainMat.snowColor, vec3(0.0)), vec3(2.2));

            vec3 finalAlbedo = mix(linGrass, linRock, rockWeight);
            finalAlbedo = mix(finalAlbedo, linSnow, snowWeight);

            float rGrassTex = texture(u_GrassRoughness, vTexCoord).r;
            float rRockTex  = texture(u_RockRoughness, vTexCoord).r;

            float rGrass = (rGrassTex > 0.01 && u_TerrainMat.hasGrassMap > 0.5)
                ? rGrassTex : 0.85;

            float rRock = (rRockTex > 0.01 && u_TerrainMat.hasRockMap > 0.5)
                ? rRockTex : 0.65;

            float finalRoughness = mix(rGrass, rRock, rockWeight);
            finalRoughness = mix(finalRoughness, 0.45, snowWeight);

            vec3 tNormalGrass = vec3(0.0, 0.0, 1.0);
            vec3 tNormalRock  = vec3(0.0, 0.0, 1.0);

            if (u_TerrainMat.hasNormalMap > 0.5) {
                tNormalGrass = getSafeNormal(u_GrassNormal, vTexCoord);
                tNormalRock  = getSafeNormal(u_RockNormal, vTexCoord);
            }

            vec3 mixedTexNormal = normalize(mix(tNormalGrass, tNormalRock, rockWeight));
            mixedTexNormal = normalize(mix(mixedTexNormal, vec3(0.0, 0.0, 1.0), snowWeight));

            vec3 helperUp = abs(baseNormal.y) < 0.999 ? vec3(0.0, 1.0, 0.0) : vec3(0.0, 0.0, 1.0);
            vec3 tangent = normalize(cross(helperUp, baseNormal));
            vec3 bitangent = normalize(cross(baseNormal, tangent));
            mat3 TBN = mat3(tangent, bitangent, baseNormal);

            vec3 finalNormal = normalize(TBN * mixedTexNormal);

            vec3 color = shadeTerrainMultiLight(
                vWorldPos,
                V,
                finalNormal,
                finalAlbedo,
                finalRoughness
            );

            float exposure = 3.5;
            vec3 mapped = color * exposure;
            mapped = (mapped * (2.51 * mapped + 0.03)) /
                     (mapped * (2.43 * mapped + 0.59) + 0.14);

            FragColor = vec4(pow(max(mapped, vec3(0.0)), vec3(1.0 / 2.2)), 1.0);
        }
    """;

    public TerrainShader() {
        super("TerrainShader", VERTEX_SRC, FRAGMENT_SRC);
    }

    /**
     * 直接给 JS 调用，替代你原来不存在的 setup()
     */
    public void setup(Matrix4f viewProjection, Matrix4f model, Vector3f viewPos) {
        setUniform("u_ViewProjection", viewProjection);
        setUniform("u_Model", model);
        setUniform("u_ViewPos", viewPos);
    }

    public void setMaterial(TerrainMaterialParams params) {
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