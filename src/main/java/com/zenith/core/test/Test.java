package com.zenith.core.test;

import com.zenith.asset.AssetResource;
import com.zenith.core.ZenithEngine;
import com.zenith.render.VertexLayout;
import com.zenith.render.backend.opengl.GLWindow;
import com.zenith.render.backend.opengl.GLMaterial;
import com.zenith.render.backend.opengl.GLMesh;
import com.zenith.render.backend.opengl.shader.*;
import com.zenith.common.math.Color;
import com.zenith.render.backend.opengl.texture.GLTexture;
import org.joml.Vector3f;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.io.IOException;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13C.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13C.GL_TEXTURE1;
import static org.lwjgl.opengl.GL13C.glActiveTexture;
import static org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30C.glBindFramebuffer;

public class Test extends ZenithEngine {
    private WaterEntity waterEntity;
    private GLMesh skyBoxMesh;
    private GLMesh terrainMesh;

    private SkyShader skyShader;
    private WaterShader waterShader;
    private TerrainShader terrainShader;

    private float time = 0.0f;
    private boolean sceneInitialized = false;

    private final Matrix4f viewMatrix = new Matrix4f();
    private final Matrix4f projMatrix = new Matrix4f();

    private final Vector3f sunDir = new Vector3f();
    private final Vector3f sunIntensityVec = new Vector3f();
    private final Vector3f camPosCached = new Vector3f();
    private final TerrainShader.TerrainMaterialParams terrainParams = new TerrainShader.TerrainMaterialParams();
    private final Matrix4f tempMat = new Matrix4f();

    GLTexture grassAlbedo;
    GLTexture grassNormal;
    GLTexture grassRoughness;
    GLTexture rockAlbedo;
    GLTexture rockNormal;
    GLTexture rockRoughness;

    GLTexture waterAlbedo;
    GLTexture waterNormal;

    public Test() {
        super(new GLWindow("Zenith World System - Terrain & Water", 1280, 720));
    }

    private void initScene() {
        // 相机位置
        this.getCamera().getTransform().getPosition().set(0, 80, 100);

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        VertexLayout layout = createStandardLayout();

        // 天空盒
        skyShader = GLShaderRegistry.get(GLShaderRegistry.SKY);
        skyBoxMesh = new GLMesh(generateSkyBoxData().length / 20, layout);
        skyBoxMesh.updateVertices(generateSkyBoxData());

        // 地形
        terrainShader = new TerrainShader();

        terrainParams.hasGrassMap = true;
        terrainParams.hasRockMap = true;
        terrainParams.hasNormalMap = true;
        terrainParams.uvScale = 8.0f;
        terrainParams.amplitude = 85.0f;
        terrainParams.frequency = 0.0025f;
        terrainParams.snowHeight = 50.0f;

        terrainShader.setMaterial(terrainParams);

        float[] terrainData = generatePlaneData(3000.0f, 3000.0f, 250, 250);
        terrainMesh = new GLMesh(terrainData.length / 20, layout);
        terrainMesh.updateVertices(terrainData);

        // 水体
        waterShader = new WaterShader();
        GLMaterial waterMaterial = new GLMaterial(waterShader);

        float[] waterData = generatePlaneData(3000.0f, 3000.0f, 300, 300);
        GLMesh waterMesh = new GLMesh(waterData.length / 20, layout);
        waterMesh.updateVertices(waterData);
        this.waterEntity = new WaterEntity(waterMesh, waterMaterial);

        try {
            grassAlbedo = new GLTexture(AssetResource.loadFromResources("textures/grass/Grass004_4K-PNG_Color.png"));
            grassNormal = new GLTexture(AssetResource.loadFromResources("textures/grass/Grass004_4K-PNG_NormalGL.png"));
            grassRoughness = new GLTexture(AssetResource.loadFromResources("textures/grass/Grass004_4K-PNG_Roughness.png"));

            rockAlbedo = new GLTexture(AssetResource.loadFromResources("textures/rock/Rock051_1K-PNG_Color.png"));
            rockNormal = new GLTexture(AssetResource.loadFromResources("textures/rock/Rock051_1K-PNG_NormalGL.png"));
            rockRoughness = new GLTexture(AssetResource.loadFromResources("textures/rock/Rock051_1K-PNG_Roughness.png"));

            waterAlbedo = new GLTexture(AssetResource.loadFromResources("textures/water/Water_0341.jpg"));
            waterNormal = new GLTexture(AssetResource.loadFromResources("textures/water/Water_0341normal.jpg"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        this.sceneInitialized = true;
    }

    @Override
    protected void update(float deltaTime) {
        if (!sceneInitialized) initScene();

        // 让水流动更明显
        time += deltaTime * 1.2f;

        // 太阳轨迹
        float tilt = 0.41f;
        sunDir.set(
                (float) (Math.cos(time) * Math.sin(tilt)),
                (float) (Math.sin(time)),
                (float) (Math.cos(time) * Math.cos(tilt))
        ).normalize();

        float sunY = Math.max(0.0f, sunDir.y);

        Vector3f sunsetColor = new Vector3f(1.0f, 0.45f, 0.15f);
        Vector3f noonColor = new Vector3f(1.0f, 0.98f, 0.95f);

        float blend = (float) Math.pow(sunY, 0.4);
        sunIntensityVec.set(sunsetColor).lerp(noonColor, blend);

        float intensity = Math.max(0.15f, sunY * 3.5f);
        sunIntensityVec.mul(intensity);
    }

    @Override
    protected void renderScene() {
        glClearColor(0.02f, 0.04f, 0.08f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        viewMatrix.set(getCamera().getViewMatrix());
        projMatrix.set(getCamera().getProjection().getMatrix());
        camPosCached.set(getCamera().getTransform().getPosition());

        // 天空：先关闭深度写入，避免被地形/自身深度挡掉
        glDepthMask(false);
        glDepthFunc(GL_LEQUAL);

        skyShader.bind();
        Matrix4f viewNoTrans = new Matrix4f(viewMatrix);
        viewNoTrans.m30(0);
        viewNoTrans.m31(0);
        viewNoTrans.m32(0);
        skyShader.setUniform("u_ViewProjection", new Matrix4f(projMatrix).mul(viewNoTrans));
        skyShader.setUniform("u_SunDir", sunDir);
        skyBoxMesh.render();

        // 恢复默认深度状态
        glDepthMask(true);
        glDepthFunc(GL_LESS);

        // 地形
        glEnable(GL_DEPTH_TEST);
        terrainShader.bind();
        terrainShader.setMaterial(terrainParams);
        terrainShader.setUniform("u_ViewProjection", new Matrix4f(projMatrix).mul(viewMatrix));
        terrainShader.setUniform("u_Model", new Matrix4f().translate(0, -20, 0));
        terrainShader.setUniform("u_ViewPos", camPosCached);
        terrainShader.setUniform("u_SunDir", sunDir);
        terrainShader.setUniform("u_SunIntensity", sunIntensityVec);

        terrainShader.bindGrassMaps(grassAlbedo, grassNormal, grassRoughness);
        terrainShader.bindRockMaps(rockAlbedo, rockNormal, rockRoughness);

        terrainMesh.render();
    }

    @Override
    protected void renderAfterOpaqueScene() {
        if (waterEntity == null || sceneFBO == null) return;

        waterShader.bind();
        waterShader.setMatrices(projMatrix, viewMatrix);
        waterShader.setUniform("u_ViewProjection", new Matrix4f(projMatrix).mul(viewMatrix));
        waterShader.setUniform("u_Model", new Matrix4f().translate(0, 50, 0));
        waterShader.setScreenSize(window.getWidth(), window.getHeight());
        waterShader.setSplashes(null);

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, sceneFBO.getColorCopyTex());
        waterShader.setUniform("u_SceneColor", 8);

        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, sceneFBO.getDepthCopyTex());
        waterShader.setUniform("u_SceneDepth", 9);

        waterShader.bindWaterNormal(waterNormal);

        waterShader.updateUniforms(
                camPosCached,
                sunDir,
                sunIntensityVec,
                new Color(0.01f, 0.05f, 0.12f),
                new Color(0.1f, 0.5f, 0.65f),
                time,
                0.0f
        );

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glEnable(GL_DEPTH_TEST);

        // 【关键修复 1】：禁用面剔除，让正反两面都可见
        glDisable(GL_CULL_FACE);

        glDepthMask(false);

        waterEntity.getMesh().render();

        glDepthMask(true);
        // 【关键修复 2】：渲染完后恢复（如果后续渲染需要的话）
        glEnable(GL_CULL_FACE);
    }

    private VertexLayout createStandardLayout() {
        VertexLayout layout = new VertexLayout();
        layout.pushFloat("aPos", 3);
        layout.pushFloat("aNormal", 3);
        layout.pushFloat("aTexCoord", 2);
        layout.pushFloat("aColor", 4);
        layout.pushFloat("aBoneIds", 4);
        layout.pushFloat("aWeights", 4);
        return layout;
    }

    private float[] generatePlaneData(float w, float d, int xn, int zn) {
        float[] vertices = new float[(xn + 1) * (zn + 1) * 20];
        for (int z = 0; z <= zn; z++) {
            for (int x = 0; x <= xn; x++) {
                int i = (z * (xn + 1) + x) * 20;
                vertices[i] = (float) x / xn * w - w / 2f;
                vertices[i + 1] = 0;
                vertices[i + 2] = (float) z / zn * d - d / 2f;
                vertices[i + 3] = 0; vertices[i + 4] = 1.0f; vertices[i + 5] = 0;
                vertices[i + 6] = (float) x / xn; vertices[i + 7] = (float) z / zn;
            }
        }
        int[] indices = new int[xn * zn * 6];
        int p = 0;
        for (int z = 0; z < zn; z++) {
            for (int x = 0; x < xn; x++) {
                int s = z * (xn + 1) + x;
                indices[p++] = s; indices[p++] = s + xn + 1; indices[p++] = s + 1;
                indices[p++] = s + 1; indices[p++] = s + xn + 1; indices[p++] = s + xn + 2;
            }
        }
        float[] res = new float[indices.length * 20];
        for (int i = 0; i < indices.length; i++) System.arraycopy(vertices, indices[i] * 20, res, i * 20, 20);
        return res;
    }

    private float[] generateSkyBoxData() {
        float[] c = {-1,1,-1, -1,-1,-1, 1,-1,-1, 1,-1,-1, 1,1,-1, -1,1,-1, -1,-1,1, -1,-1,-1, -1,1,-1, -1,1,-1, -1,1,1, -1,-1,1, 1,-1,-1, 1,-1,1, 1,1,1, 1,1,1, 1,1,-1, 1,-1,-1, -1,-1,1, -1,1,1, 1,1,1, 1,1,1, 1,-1,1, -1,-1,1, -1,1,-1, 1,1,-1, 1,1,1, 1,1,1, -1,1,1, -1,1,-1, -1,-1,-1, -1,-1,1, 1,-1,-1, 1,-1,-1, -1,-1,1, 1,-1,1};
        float[] d = new float[c.length / 3 * 20];
        for (int i = 0; i < c.length / 3; i++) {
            d[i*20] = c[i*3] * 900f;
            d[i*20+1] = c[i*3+1] * 900f;
            d[i*20+2] = c[i*3+2] * 900f;
        }
        return d;
    }

    public static void main(String[] args) {
        new Test().start();
    }
}