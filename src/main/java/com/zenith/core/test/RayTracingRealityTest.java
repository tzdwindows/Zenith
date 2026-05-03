package com.zenith.core.test;

import com.zenith.animation.data.AnimatedModel;
import com.zenith.animation.io.AssimpModelLoader;
import com.zenith.common.config.RayTracingConfig;
import com.zenith.common.math.Color;
import com.zenith.common.math.Transform;
import com.zenith.core.ZenithEngine;
import com.zenith.render.Texture;
import com.zenith.render.VertexLayout;
import com.zenith.render.backend.opengl.GLMesh;
import com.zenith.render.backend.opengl.GLWindow;
import com.zenith.render.backend.opengl.SoftwarePathTracerProvider;
import com.zenith.render.backend.opengl.shader.WaterShader; // 导入你的WaterShader
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;

/**
 * Reality test scene for software ray tracing:
 * - Physical sky (in path tracer)
 * - Ground & Model (in path tracer)
 * - Sea / water plane (Rasterized using WaterShader)
 */
public class RayTracingRealityTest extends ZenithEngine {

    private GLMesh groundMesh;
    private GLMesh waterMesh;
    private AnimatedModel model;
    private final Transform modelTransform = new Transform();
    private final Transform waterTransform = new Transform(); // 水面的Transform

    private SoftwarePathTracerProvider softwareRT;
    private WaterShader waterShader; // 声明 WaterShader

    private org.joml.Vector3f lastCamPos = new org.joml.Vector3f();
    private org.joml.Vector3f lastCamForward = new org.joml.Vector3f();

    public RayTracingRealityTest() {
        super(new GLWindow("Zenith Engine - Software Ray Tracing Reality Test", 1280, 720));
    }

    @Override
    protected void init() {
        // Full software path tracing mode.
        RayTracingConfig.ENABLE_RAY_TRACING = true;
        RayTracingConfig.RT_MODE = 0;
        RayTracingConfig.DYNAMIC_AS_UPDATE = false;

        setCursorMode(true);

        getCamera().getTransform().setPosition(0.0f, 2.0f, 6.0f);
        getCamera().lookAt(new Vector3f(0, 1.2f, 0), new Vector3f(0, 1, 0));

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);

        VertexLayout layout = new VertexLayout();
        layout.pushFloat("aPos", 4);       // 改为 4
        layout.pushFloat("aNormal", 4);    // 改为 4
        layout.pushFloat("aTexCoord", 4);  // 改为 4
        layout.pushFloat("aColor", 4);     // 保持 4

        groundMesh = new GLMesh(6, layout);
        groundMesh.name = "rt_ground";
        groundMesh.updateVertices(makePlane(60.0f, 60.0f, 0.0f, new float[]{0.65f, 0.65f, 0.65f, 1.0f}, 2.0f));

        // 水面的顶点数据依然保留，但不会进入光追
        waterMesh = new GLMesh(6, layout);
        waterMesh.name = "rt_water";
        waterMesh.updateVertices(makePlane(60.0f, 60.0f, 0.15f, new float[]{0.1f, 0.5f, 0.8f, 1.0f}, 1.0f));

        // 初始化水面 Shader
        waterShader = new WaterShader();
        waterShader.setScreenSize(1280, 720);

        // Load the model on the render thread (AssimpModelLoader creates GL buffers).
        String modelPath = "C:\\Users\\tzdwindows 7\\OneDrive\\Desktop\\myfurry\\wenqi.gltf";
        model = AssimpModelLoader.load(modelPath);
        modelTransform.setScale(0.5f);
        modelTransform.getPosition().set(0.0f, 0.0f, 0.0f);

        // RT provider + mesh registration.
        softwareRT = new SoftwarePathTracerProvider();
        setRtProvider(softwareRT);

        System.setProperty("zenith.rt.safeMode", "0");

        List<com.zenith.render.Mesh> meshes = new ArrayList<>();
        meshes.add(groundMesh);
        meshes.add(waterMesh);
        meshes.add(model.getMesh());

        clearRtMeshes();
        for (var m : meshes) addRtMesh(m);

        addRtMesh(waterMesh); // 让光追知道水面的存在

        // 设置材质 ID (0=狐狸, 1=水面, 2=地面)
        softwareRT.setMeshMaterialId(model.getMesh(), 0);
        softwareRT.setMeshMaterialId(waterMesh, 1);
        softwareRT.setMeshMaterialId(groundMesh, 2);

        Texture albedo = (model.getTextures() != null && !model.getTextures().isEmpty()) ? model.getTextures().get(0) : null;
        if (albedo != null) {
            softwareRT.setAlbedoTextureId(albedo.getId());
        }

        // Build BVH once for this static test scene (不再包含水面)
        softwareRT.buildAccelerationStructures(rtMeshes);
    }

    @Override
    protected void update(float deltaTime) {
        com.zenith.render.Camera camera = getCamera();
        org.joml.Vector3f currentPos = camera.getTransform().getPosition();
        org.joml.Vector3f currentForward = camera.getForward();

        boolean moved = currentPos.distanceSquared(lastCamPos) > 0.0001f;
        boolean rotated = currentForward.distanceSquared(lastCamForward) > 0.0001f;

        if (moved || rotated) {
            if (softwareRT != null) {
                softwareRT.resetAccumulation();
            }
            lastCamPos.set(currentPos);
            lastCamForward.set(currentForward);
        }
    }

    @Override
    protected void renderScene() {
        // 光追场景此处留空
    }

    @Override
    protected void renderAfterOpaqueScene() {
        //if (waterMesh != null && waterShader != null) {
        //    glEnable(GL_BLEND);
        //    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        //    glEnable(GL_DEPTH_TEST);
        //    // 重要：确保深度掩码开启，否则深度测试可能失效
        //    glDepthMask(true);
//
        //    waterShader.bind();
//
        //    // --- 修正点 1：传递正确的屏幕尺寸，防止 screenUV 偏移 ---
        //    waterShader.setScreenSize(window.getWidth(), window.getHeight());
//
        //    // --- 修正点 2：手动传递最新的 ViewProjection ---
        //    // 确保 getCamera().getViewProjectionMatrix() 返回的是 Projection * View
        //    waterShader.setUniform("u_ViewProjection", getCamera().getViewProjectionMatrix());
//
        //    // --- 修正点 3：确保 Model 矩阵被重置 ---
        //    // 如果 waterTransform 没有被正确初始化，它可能包含上一帧的残余数据
        //    waterShader.setUniform("u_Model", waterTransform.getModelMatrix());
//
        //    waterShader.updateUniforms(
        //            getCamera().getTransform().getPosition(),
        //            new Color(0.0f, 0.3f, 0.4f, 1.0f),
        //            new Color(0.1f, 0.5f, 0.6f, 1.0f),
        //            (float) getEngineTime(),
        //            0.0f
        //    );
//
        //    waterMesh.render();
        //    glDisable(GL_BLEND);
        //}
    }

    private static float[] makePlane(float w, float d, float y, float[] rgba, float matId) {
        float hw = w * 0.5f;
        float hd = d * 0.5f;
        float nx = 0, ny = 1, nz = 0;
        return new float[] {
                // vec4 aPos (x, y, z, padding)
                -hw, y, -hd,  0.0f,
                // vec4 aNormal (nx, ny, nz, padding)
                nx, ny, nz,  0.0f,
                // vec4 aTexCoord (u, v, matId, padding)
                0.0f, 0.0f, matId, 0.0f,
                // vec4 aColor (r, g, b, a)
                rgba[0], rgba[1], rgba[2], rgba[3],

                -hw, y,  hd,  0.0f,
                nx, ny, nz,  0.0f,
                0.0f, 1.0f, matId, 0.0f,
                rgba[0], rgba[1], rgba[2], rgba[3],

                hw, y,  hd,  0.0f,
                nx, ny, nz,  0.0f,
                1.0f, 1.0f, matId, 0.0f,
                rgba[0], rgba[1], rgba[2], rgba[3],

                -hw, y, -hd,  0.0f,
                nx, ny, nz,  0.0f,
                0.0f, 0.0f, matId, 0.0f,
                rgba[0], rgba[1], rgba[2], rgba[3],

                hw, y,  hd,  0.0f,
                nx, ny, nz,  0.0f,
                1.0f, 1.0f, matId, 0.0f,
                rgba[0], rgba[1], rgba[2], rgba[3],

                hw, y, -hd,  0.0f,
                nx, ny, nz,  0.0f,
                1.0f, 0.0f, matId, 0.0f,
                rgba[0], rgba[1], rgba[2], rgba[3],
        };
    }


    public static void main(String[] args) {
        new RayTracingRealityTest().start();
    }
}