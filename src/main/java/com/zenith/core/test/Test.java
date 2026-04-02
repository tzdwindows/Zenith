package com.zenith.core.test;

import com.zenith.asset.AssetResource;
import com.zenith.core.ZenithEngine;
import com.zenith.render.VertexLayout;
import com.zenith.render.backend.opengl.*;
import com.zenith.render.backend.opengl.shader.*;
import com.zenith.common.math.Color;
import com.zenith.render.backend.opengl.texture.GLTexture;
import org.joml.Vector3f;
import org.joml.Matrix4f;

import java.io.IOException;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13C.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13C.GL_TEXTURE1;
import static org.lwjgl.opengl.GL13C.glActiveTexture;

public class Test extends ZenithEngine {
    private WaterEntity waterEntity;
    private GLMesh skyBoxMesh;
    private GLMesh terrainMesh;
    private SkyShader skyShader;
    private WaterShader waterShader;
    private TerrainShader terrainShader;

    private float time = 0.0f;
    private final Matrix4f viewMatrix = new Matrix4f();
    private final Matrix4f projMatrix = new Matrix4f();
    private final Vector3f camPosCached = new Vector3f();

    private GLLight sunLight;
    private final TerrainShader.TerrainMaterialParams terrainParams = new TerrainShader.TerrainMaterialParams();

    private float[] skyBoxRawData;
    private float[] terrainRawData;
    private float[] waterRawData;

    private GLTexture grassAlbedo, grassNormal, grassRoughness;
    private GLTexture rockAlbedo, rockNormal, rockRoughness;
    private GLTexture waterAlbedo, waterNormal;
    private SoftwarePathTracerProvider softwareRT;
    public Test() {
        super(new GLWindow("Zenith World System - Optical PBR", 1280, 720));
    }

    @Override
    protected void init() {
        this.getCamera().getTransform().getPosition().set(0, 150, 200);

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        VertexLayout layout = createStandardLayout();

        skyShader = GLShaderRegistry.get(GLShaderRegistry.SKY);
        skyBoxMesh = new GLMesh(skyBoxRawData.length / 20, layout);
        skyBoxMesh.updateVertices(skyBoxRawData);

        terrainShader = new TerrainShader();
        terrainParams.hasGrassMap = true;
        terrainParams.hasRockMap = true;
        terrainParams.hasNormalMap = true;
        terrainParams.uvScale = 120.0f;
        terrainParams.amplitude = 140.0f;
        terrainParams.frequency = 0.0016f;
        terrainParams.snowHeight = 90.0f;

        terrainMesh = new GLMesh(terrainRawData.length / 20, layout);
        terrainMesh.updateVertices(terrainRawData);

        waterShader = new WaterShader();
        GLMesh waterMesh = new GLMesh(waterRawData.length / 20, layout);
        waterMesh.updateVertices(waterRawData);
        this.waterEntity = new WaterEntity(waterMesh, new GLMaterial(waterShader));

        sunLight = new GLLight(0);
        sunLight.setColor(new Color(1.0f, 0.98f, 0.9f));
        sunLight.setIntensity(1.6f);
        sunLight.setAmbientStrength(0.18f);

        softwareRT = new SoftwarePathTracerProvider();
        setRtProvider(softwareRT);
        addRtMesh(terrainMesh);
        LightManager.get().addLight(sunLight);
        softwareRT.buildAccelerationStructures(rtMeshes);
    }

    @Override
    protected void asyncLoad() {
        setLoadingProgress(0.1f);
        skyBoxRawData = generateSkyBoxData();
        terrainRawData = generatePlaneData(5000.0f, 5000.0f, 256, 256);
        waterRawData = generatePlaneData(5000.0f, 5000.0f, 300, 300);

        loadTextureSafe(0.50f, "textures/grass/Grass004_4K-PNG_Color.png",      tex -> grassAlbedo = tex);
        loadTextureSafe(0.55f, "textures/grass/Grass004_4K-PNG_NormalGL.png",   tex -> grassNormal = tex);
        loadTextureSafe(0.60f, "textures/grass/Grass004_4K-PNG_Roughness.png",  tex -> grassRoughness = tex);
        loadTextureSafe(0.70f, "textures/rock/Rock051_1K-PNG_Color.png",        tex -> rockAlbedo = tex);
        loadTextureSafe(0.80f, "textures/rock/Rock051_1K-PNG_NormalGL.png",     tex -> rockNormal = tex);
        loadTextureSafe(0.90f, "textures/rock/Rock051_1K-PNG_Roughness.png",    tex -> rockRoughness = tex);
        loadTextureSafe(0.95f, "textures/water/Water_0341.jpg",                 tex -> waterAlbedo = tex);
        loadTextureSafe(1.00f, "textures/water/Water_0341normal.jpg",           tex -> waterNormal = tex);
    }

    private void loadTextureSafe(float progress, String path, java.util.function.Consumer<GLTexture> onLoaded) {
        try {
            var rawImageData = AssetResource.loadFromResources(path);
            runOnMainThread(() -> {
                try {
                    GLTexture texture = new GLTexture(rawImageData);
                    onLoaded.accept(texture);
                } catch (IOException e) { e.printStackTrace(); }
            });
            setLoadingProgress(progress);
        } catch (Exception e) {
            System.err.println("Failed to load asset: " + path);
        }
    }

    @Override
    protected void update(float deltaTime) {
        Vector3f oldPos = new Vector3f(getCamera().getTransform().getPosition());
        float oldYaw = getYaw();
        float oldPitch = getPitch();

        time += deltaTime;

        boolean moved = oldPos.distance(getCamera().getTransform().getPosition()) > 0.01f;
        boolean turned = Math.abs(oldYaw - getYaw()) > 0.01f || Math.abs(oldPitch - getPitch()) > 0.01f;

        if (moved || turned) {
            softwareRT.resetAccumulation();
        }

        // 太阳轨迹角
        float angle = time * 0.15f;
        float sunHeight = (float) Math.sin(angle);
        float sunElevation = Math.max(0.0f, sunHeight);

        // 强制太阳光从天空射向地面
        sunLight.getDirection().set(
                -(float) Math.cos(angle),
                -sunHeight,
                -(float) Math.cos(angle * 0.5f)
        ).normalize();

        // 1. 设置太阳直射强度
        sunLight.setIntensity(sunElevation * 2.5f);

        // ⭐ 2. 关键修复：设置环境光强度！
        // 晚上(sunElevation=0)时，环境光降至 0.02 (模拟微弱的月光)，白天最高为 0.2
        sunLight.setAmbientStrength(Math.max(0.02f, sunElevation * 0.2f));
    }

    @Override
    protected void renderScene() {
        if (skyBoxMesh == null || terrainMesh == null || sunLight == null) return;
        getCamera().getProjection().setFar(5000.0f);

        glClearColor(0.01f, 0.02f, 0.03f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        viewMatrix.set(getCamera().getViewMatrix());
        projMatrix.set(getCamera().getProjection().getMatrix());
        camPosCached.set(getCamera().getTransform().getPosition());

        // --- 【渲染天空】 ---
        glDisable(GL_CULL_FACE);
        glDepthMask(false);
        glDepthFunc(GL_LEQUAL);

        skyShader.bind();
        Matrix4f viewNoTrans = new Matrix4f(viewMatrix).setTranslation(0, 0, 0);
        Matrix4f skyVP = new Matrix4f(projMatrix).mul(viewNoTrans);
        skyShader.setUniform("u_ViewProjection", skyVP);

        // ⭐ 关键修复：天空盒需要的是“从地面指向太阳的位置” (向上)
        // 所以这里克隆一份太阳光线方向(向下)，并取反(negate)变成向上，专门喂给天空盒
        Vector3f sunPosInSky = new Vector3f(sunLight.getDirection()).negate();
        skyShader.setUniform("u_SunDir", sunPosInSky);

        skyShader.setUniform("u_Time", time);
        skyBoxMesh.render();

        glDepthFunc(GL_LESS);
        glDepthMask(true);
        glEnable(GL_CULL_FACE);

        // --- 【渲染地形】 ---
        terrainShader.bind();
        terrainShader.setMaterial(terrainParams);
        Matrix4f terrainModel = new Matrix4f().translate(0, -40, 0);
        terrainShader.setUniform("u_Model", terrainModel);
        terrainShader.setUniform("u_ViewProjection", new Matrix4f(projMatrix).mul(viewMatrix));

        // 地形接收的是物理系统里向下的光线，高光正常！
        LightManager.get().apply(terrainShader, camPosCached);
        terrainShader.bindGrassMaps(grassAlbedo, grassNormal, grassRoughness);
        terrainShader.bindRockMaps(rockAlbedo, rockNormal, rockRoughness);
        terrainMesh.render();
    }

    @Override
    protected void onBufferToScreen(float realDeltaTime, ScreenShader screenShader) {
        // --- 6. 后期处理适配 ---
        // 当光追开启时，告诉后期着色器使用光追生成的 HDR 纹理
        if (softwareRT != null) {
            glActiveTexture(GL_TEXTURE1);
            glBindTexture(GL_TEXTURE_2D, sceneFBO.getRayTraceTargetID());

            screenShader.bind();
            screenShader.setUniform("pathTraceTexture", 1);

            // 传入 1.0 / sampleCount 供 output.frag 进行均值混合
            float invSample = 1.0f / Math.max(1.0f, softwareRT.getSampleCount());
            screenShader.setUniform("invSampleCounter", invSample);

            // 启用 ACES ToneMapping，让光追的 HDR 结果看起来更真实
            screenShader.setUniform("enableAces", true);
        }
    }

    @Override
    protected void renderAfterOpaqueScene() {
        if (waterEntity == null || sceneFBO == null || waterNormal == null) return;

        waterShader.bind();

        Matrix4f vpMatrix = new Matrix4f(projMatrix).mul(viewMatrix);
        waterShader.setUniform("u_ViewProjection", vpMatrix);

        // 利用 CPU 算好逆矩阵，杜绝驱动 Bug
        Matrix4f invVPMatrix = new Matrix4f(vpMatrix).invert();
        waterShader.setUniform("u_InvViewProjection", invVPMatrix);

        waterShader.setUniform("u_Model", new Matrix4f().translate(0, 40, 0));

        // 传入安全的屏幕分辨率
        waterShader.setScreenSize(window.getWidth(), window.getHeight());

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, sceneFBO.getColorCopyTex());
        waterShader.setUniform("u_SceneColor", 0);

        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, sceneFBO.getDepthCopyTex());
        waterShader.setUniform("u_SceneDepth", 1);

        waterShader.bindWaterNormal(waterNormal);
        LightManager.get().apply(waterShader, camPosCached);

        // ⭐ 调亮底色，确保即使没有灯光也能看见水
        waterShader.updateUniforms(
                camPosCached,
                new Color(0.05f, 0.35f, 0.55f), // 非常亮的蓝绿色
                new Color(0.20f, 0.65f, 0.80f), // 浅水更亮
                time,
                0.02f
        );

        glEnable(GL_BLEND);
        glDepthMask(false);
        waterEntity.getMesh().render();
        glDepthMask(true);
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
        float[] c = {
                -1,1,-1, -1,-1,-1, 1,-1,-1, 1,-1,-1, 1,1,-1, -1,1,-1,
                -1,-1,1, -1,-1,-1, -1,1,-1, -1,1,-1, -1,1,1, -1,-1,1,
                1,-1,-1, 1,-1,1, 1,1,1, 1,1,1, 1,1,-1, 1,-1,-1,
                -1,-1,1, -1,1,1, 1,1,1, 1,1,1, 1,-1,1, -1,-1,1,
                -1,1,-1, 1,1,-1, 1,1,1, 1,1,1, -1,1,1, -1,1,-1,
                -1,-1,-1, -1,-1,1, 1,-1,-1, 1,-1,-1, -1,-1,1, 1,-1,1
        };
        int vertexCount = c.length / 3;
        float[] d = new float[vertexCount * 20];
        for (int i = 0; i < vertexCount; i++) {
            int offset = i * 20;
            d[offset]     = c[i * 3] * 1200f;
            d[offset + 1] = c[i * 3 + 1] * 1200f;
            d[offset + 2] = c[i * 3 + 2] * 1200f;
            for(int j = 3; j < 20; j++) d[offset + j] = 0.0f;
        }
        return d;
    }

    public static void main(String[] args) {
        new Test().start();
    }
}