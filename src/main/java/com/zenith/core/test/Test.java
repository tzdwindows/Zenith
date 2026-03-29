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
        // 1. 初始化相机位置 (稍微抬高，以便看到远处的群山)
        this.getCamera().getTransform().getPosition().set(0, 80, 100);

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        VertexLayout layout = createStandardLayout();

        // 2. 天空盒
        skyShader = GLShaderRegistry.get(GLShaderRegistry.SKY);
        skyBoxMesh = new GLMesh(generateSkyBoxData().length / 20, layout);
        skyBoxMesh.updateVertices(generateSkyBoxData());

        // 3. 地形 (【关键修复】：提升网格精度到 600x600，消除三角形锯齿感)
        terrainShader = new TerrainShader();

        terrainParams.hasGrassMap = true;
        terrainParams.hasRockMap = true;
        terrainParams.hasNormalMap = true;
        terrainParams.uvScale = 8.0f;
        terrainParams.amplitude = 85.0f;
        terrainParams.frequency = 0.0025f;
        terrainParams.snowHeight = 50.0f;

        terrainShader.setMaterial(terrainParams);

        float[] terrainData = generatePlaneData(3000.0f, 3000.0f, 250, 250);// 视野扩大到 3公里
        terrainMesh = new GLMesh(terrainData.length / 20, layout);

        terrainMesh.updateVertices(terrainData);

        // 4. 水体 (水体不需要和地形一样高的细分)
        waterShader = GLShaderRegistry.get(GLShaderRegistry.WATER);
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

        // 缓慢的时间流速
        time += deltaTime;

        // 模拟地球 23.5度 倾角的太阳轨迹
        float tilt = 0.41f;
        sunDir.set(
                (float) (Math.cos(time) * Math.sin(tilt)),
                (float) (Math.sin(time)),
                (float) (Math.cos(time) * Math.cos(tilt))
        ).normalize();

        float sunY = Math.max(0.0f, sunDir.y);

        // 晨昏过渡颜色
        Vector3f sunsetColor = new Vector3f(1.0f, 0.45f, 0.15f);
        Vector3f noonColor = new Vector3f(1.0f, 0.98f, 0.95f);

        float blend = (float) Math.pow(sunY, 0.4);
        sunIntensityVec.set(sunsetColor).lerp(noonColor, blend);

        // 【修复核心：告别死白】
        // 之前这里是 sunY * 25.0f，能量太高直接把材质照成了白色发光体
        // 降至 3.5f，刚好能触发 PBR 的质感，又不会过曝
        float intensity = Math.max(0.0f, sunY * 3.5f);
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
        if (waterEntity == null) return;

        waterShader.bind();

        // 1. 设置基础矩阵
        waterShader.setMatrices(camera.getProjection().getMatrix(), camera.getViewMatrix());

        Matrix4f viewProj = new Matrix4f();
        camera.getProjection().getMatrix().mul(camera.getViewMatrix(), viewProj);
        waterShader.setUniform("u_ViewProjection", viewProj);

        // 模型矩阵：水面位置
        Matrix4f modelMat = new Matrix4f().translate(0, 15, 0);
        waterShader.setUniform("u_Model", modelMat);

        // 2. 传递屏幕分辨率 【注意：这行必须取消注释！】
        // 因为 Shader 内部需要用它来计算屏幕 UV，从而正确采样场景颜色和深度
        waterShader.setScreenSize(window.getWidth(), window.getHeight());

        // 3. 绑定各种贴图到对应的纹理单元 (Texture Unit)

        // Unit 0: 场景颜色 (用于折射)
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, sceneFBO.getColorCopyTex());
        waterShader.setUniform("u_SceneColor", 0);

        // Unit 1: 场景深度 (用于水深计算、白沫、岸边抗扭曲)
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, sceneFBO.getDepthCopyTex());
        waterShader.setUniform("u_SceneDepth", 1);

        // Unit 2: 水面法线贴图 (提供微波纹细节)
        if (waterNormal != null) {
            waterNormal.bind(2); // GLTexture 自带的绑定到单元 2 的方法
            waterShader.setUniform("u_WaterNormalTex", 2);
        }
        // 注意：真实物理水不需要 waterAlbedo，所以不绑定它。

        // 4. 水面光照和物理参数
        waterShader.updateUniforms(
                camPosCached,                 // 相机位置
                sunDir,                       // 太阳方向
                sunIntensityVec,              // 光强度向量
                new Color(0.01f, 0.04f, 0.12f), // 深水颜色 (深海蓝)
                new Color(0.1f, 0.5f, 0.6f),    // 浅水颜色 (清澈青绿)
                time,                           // 运行时间，驱动波浪流动
                0.0f                            // 下雨强度 (0.0~1.0)
        );

        // 5. 传入水花数据 (如果没有发生水花交互，传入空数组避免 Shader 崩溃)
        waterShader.setSplashes(new Vector4f[0]);

        // 6. 设置渲染状态
        glEnable(GL_DEPTH_TEST);
        glDepthMask(false); // 保持关闭深度写入，防止水面遮挡后面的半透明粒子

        // 【关键修改】物理折射水面本质是“不透明”渲染，透明感来自对背景的采样。
        // 必须关闭混合，否则会导致颜色发白或双重叠加。
        glDisable(GL_BLEND);

        // 7. 渲染水面网格
        waterEntity.getMesh().render();

        // 8. 恢复状态
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