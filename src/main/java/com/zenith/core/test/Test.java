package com.zenith.core.test;

import com.zenith.animation.data.AnimatedModel;
import com.zenith.animation.io.AssimpModelLoader;
import com.zenith.animation.runtime.Animator;
import com.zenith.asset.AssetResource;
import com.zenith.common.math.Color;
import com.zenith.common.math.Transform;
import com.zenith.common.utils.MeshUtils;
import com.zenith.core.ZenithEngine;
import com.zenith.render.backend.opengl.*;
import com.zenith.render.backend.opengl.shader.AnimationShader;
import com.zenith.render.backend.opengl.shader.ScreenShader;
import com.zenith.render.backend.opengl.shader.SkyShader;
import com.zenith.render.backend.opengl.shader.WaterShader;
import com.zenith.render.backend.opengl.texture.GLTexture;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;

public class Test extends ZenithEngine {
    private AnimatedModel animatedModel;
    private Animator animator;
    private AnimationShader animShader;
    private final Transform modelTransform = new Transform();

    private GLMesh skyMesh;
    private SkyShader skyShader;

    private GLMesh waterMesh;
    private WaterShader waterShader;
    private GLTexture waterNormal;

    private final Matrix4f vp = new Matrix4f();
    private final Matrix4f view = new Matrix4f();
    private final Matrix4f proj = new Matrix4f();

    // 太阳方向改为非 final，以便在 update 中修改
    private Vector3f sunDir = new Vector3f(0.8f, 0.3f, 0.8f).normalize();
    private GLLight sunLight; // 引用主光源
    private float time = 0f;

    public Test() {
        super(new GLWindow("Zenith Engine - Time Flowing Test", 1280, 720));
    }

    @Override
    protected void init() {
        getCamera().getTransform().setPosition(0, 3f, 8f);
        getCamera().lookAt(new Vector3f(0, 1, 0), new Vector3f(0, 1, 0));

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);

        animShader = new AnimationShader();
        String modelPath = "C:\\Users\\tzdwindows 7\\OneDrive\\Desktop\\myfurry\\wenqi.gltf";
        animatedModel = AssimpModelLoader.load(modelPath);
        animator = new Animator(animatedModel);

        if (!animatedModel.getAllAnimations().isEmpty()) {
            String anim = animatedModel.getAllAnimations().keySet().iterator().next();
            animator.play(animatedModel.getAnimation(anim));
            animator.setLooping(true);
        }
        modelTransform.setScale(0.5f);

        skyShader = new SkyShader();
        skyMesh = new GLMesh(MeshUtils.generateSkyBoxData().length / 20, MeshUtils.createStandardLayout());
        skyMesh.updateVertices(MeshUtils.generateSkyBoxData());

        waterShader = new WaterShader();
        waterMesh = new GLMesh(MeshUtils.generatePlaneData(1000f, 1000f, 200, 200).length / 20, MeshUtils.createStandardLayout());
        waterMesh.updateVertices(MeshUtils.generatePlaneData(1000f, 1000f, 200, 200));

        try {
            waterNormal = new GLTexture(AssetResource.loadFromResources("textures/water/Water_0341normal.jpg"));
        } catch (Exception ignored) {}

        // 初始化主光源
        LightManager.get().clear();
        sunLight = new GLLight(0);
        sunLight.setType(0); // 平行光
        sunLight.setColor(new Color(1, 0.95f, 0.8f, 1));
        sunLight.setIntensity(1.5f);
        sunLight.setAmbientStrength(0.15f);
        LightManager.get().addLight(sunLight);

        // 绑定光源到模型 Shader
        animShader.addLight(sunLight);
    }

    @Override
    protected void update(float deltaTime) {
        time += deltaTime;
        if (animator != null) animator.update(deltaTime);

        // =========================
        // 让时间流逝：模拟太阳旋转
        // =========================
        float sunSpeed = 0.15f; // 旋转速度
        // 让太阳绕 X 轴旋转，模拟从早到晚
        sunDir.set(
                0.5f,
                (float) Math.sin(time * sunSpeed),
                (float) Math.cos(time * sunSpeed)
        ).normalize();

        // 更新光源方向 (光的方向是从太阳指向地面，所以取反)
        sunLight.setDirection(new Vector3f(sunDir).negate());

        // 根据太阳高度调整光照强度 (落山后变暗)
        float heightFactor = Math.max(0.1f, sunDir.y);
        sunLight.setIntensity(1.5f * heightFactor);
    }

    @Override
    protected void onBufferToScreen(float realDeltaTime, ScreenShader screenShader) {
        if (screenShader ==  null)
            return;
        screenShader.bind();
        screenShader.setUniform("u_Time", time);

        // 调出 3A 感的四个参数：
        // 1. 稍微拉高曝光，让白色部分有“发光感”
        screenShader.setUniform("u_Exposure", 1.2f);

        // 2. 增强对比度，让阴影变深
        screenShader.setUniform("u_Contrast", 1.3f);

        // 3. 开启锐化，让毛发纹理清晰（RT 截图很锐利）
        screenShader.setUniform("u_Sharpness", 0.4f);

        // 4. 加上极轻微的暗角，引导视觉中心
        screenShader.setUniform("u_VignetteStrength", 0.35f);

        // 5. 色差偏移 (Chromatic Aberration) 设小一点，模拟高级镜头
        screenShader.setChromaticAberration(0.001f);
    }

    @Override
    protected void renderScene() {
        view.set(getCamera().getViewMatrix());
        proj.set(getCamera().getProjection().getMatrix());

        // 清屏
        glClearColor(0.05f, 0.08f, 0.1f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        // 1️⃣ 渲染天空 (使用最新的 sunDir)
        glDisable(GL_CULL_FACE);
        glDepthMask(false);
        glDepthFunc(GL_LEQUAL);
        skyShader.bind();
        Matrix4f viewNoTrans = new Matrix4f(view);
        viewNoTrans.m30(0); viewNoTrans.m31(0); viewNoTrans.m32(0);
        skyShader.setUniform("u_ViewProjection", new Matrix4f(proj).mul(viewNoTrans));
        skyShader.setUniform("u_SunDir", sunDir);
        skyMesh.render();
        glDepthMask(true);
        glDepthFunc(GL_LESS);
        glEnable(GL_CULL_FACE);

        // 2️⃣ 渲染模型
        if (animatedModel != null && animator != null) {
            vp.set(proj).mul(view);

            animShader.bind();
            animShader.setUniform("u_SunDir", sunDir);
            // 将 Color 转为 Vector3f
            animShader.setUniform("u_SunColor", new Vector3f(sunLight.getColor().r, sunLight.getColor().g, sunLight.getColor().b));
            animShader.setUniform("u_Time", time);

            animator.bind(0);

            // 槽位状态重置
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, 0);

            boolean hasTexture = animatedModel.getTextures() != null && !animatedModel.getTextures().isEmpty();
            animShader.setUseTexture(hasTexture);
            if (hasTexture) {
                animatedModel.getTextures().get(0).bind(0);
                animShader.setUniform("u_DiffuseMap", 0);
            }

            // Alpha 设为 1.0 保证不透明
            animShader.setup(vp, modelTransform.getModelMatrix(), new Color(1.0f, 1.0f, 1.0f, 1.0f));

            // 重要：实时应用更新后的光源方向
            animShader.applyLights(getCamera().getTransform().getPosition());
            animShader.setEmissive(false, new Vector3f(0), 0.0f);

            animatedModel.getMesh().render();
        }
    }

    @Override
    protected void renderAfterOpaqueScene() {
        SceneFramebuffer sceneFBO = getSceneFBO();
        if (sceneFBO == null) return;

        waterShader.bind();

        // 绑定场景贴图
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, sceneFBO.getColorCopyTex());

        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, sceneFBO.getDepthCopyTex());

        // 修正屏幕分辨率，解决影子错位问题
        waterShader.setUniform("u_ScreenSize", new Vector2f(getWindow().getWidth(), getWindow().getHeight()));
        waterShader.setUniform("u_SceneColor", 0);
        waterShader.setUniform("u_SceneDepth", 1);

        if (waterNormal != null) waterShader.bindWaterNormal(waterNormal);

        waterShader.setUniform("u_ViewProjection", new Matrix4f(proj).mul(view));
        waterShader.setUniform("u_Model", new Matrix4f().translate(0, 0, 0));

        waterShader.setSun(sunDir, new Vector3f(sunLight.getColor().r, sunLight.getColor().g, sunLight.getColor().b));
        // 传入当前 time 让水面波动
        waterShader.updateUniforms(
                getCamera().getTransform().getPosition(),
                new Color(0.01f, 0.05f, 0.12f, 1),
                new Color(0.1f, 0.45f, 0.6f, 1),
                time,
                0.9f
        );


        glDisable(GL_BLEND);
        glDepthMask(false);
        waterMesh.render();
        glDepthMask(true);
    }

    public static void main(String[] args) {
        new Test().start();
    }
}