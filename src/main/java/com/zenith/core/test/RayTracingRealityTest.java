package com.zenith.core.test;

import com.zenith.animation.data.AnimatedModel;
import com.zenith.animation.io.AssimpModelLoader;
import com.zenith.common.config.RayTracingConfig;
import com.zenith.common.math.Transform;
import com.zenith.core.ZenithEngine;
import com.zenith.render.Texture;
import com.zenith.render.VertexLayout;
import com.zenith.render.backend.opengl.GLMesh;
import com.zenith.render.backend.opengl.GLWindow;
import com.zenith.render.backend.opengl.SoftwarePathTracerProvider;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;

/**
 * Reality test scene for software ray tracing:
 * - Physical sky (in path tracer)
 * - Sea / water plane (dielectric material in path tracer)
 * - glTF model geometry (diffuse texture optional)
 */
public class RayTracingRealityTest extends ZenithEngine {

    private GLMesh groundMesh;
    private GLMesh waterMesh;
    private AnimatedModel model;
    private final Transform modelTransform = new Transform();

    private SoftwarePathTracerProvider softwareRT;
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
        layout.pushFloat("aPos", 3);
        layout.pushFloat("aNormal", 3);
        layout.pushFloat("aTexCoord", 2);
        layout.pushFloat("aColor", 4);

        groundMesh = new GLMesh(6, layout);
        groundMesh.name = "rt_ground";
        groundMesh.updateVertices(makePlane(60.0f, 60.0f, 0.0f, new float[]{0.65f, 0.65f, 0.65f, 1.0f}));

        waterMesh = new GLMesh(6, layout);
        waterMesh.name = "rt_water";
        waterMesh.updateVertices(makePlane(60.0f, 60.0f, 0.15f, new float[]{0.9f, 0.95f, 1.0f, 1.0f}));

        // Load the model on the render thread (AssimpModelLoader creates GL buffers).
        String modelPath = "C:\\Users\\tzdwindows 7\\OneDrive\\Desktop\\myfurry\\wenqi.gltf";
        model = AssimpModelLoader.load(modelPath);
        modelTransform.setScale(0.5f);
        modelTransform.getPosition().set(0.0f, 0.0f, 0.0f);

        // RT provider + mesh registration.
        softwareRT = new SoftwarePathTracerProvider();
        setRtProvider(softwareRT);

        // Crash isolation switch: set to 1 to bypass BVH/SSBO reads in the compute shader.
        // If 1 works but 0 crashes, the issue is inside BVH/SSBO tracing logic (not compute dispatch itself).
        System.setProperty("zenith.rt.safeMode", "0");

        List<com.zenith.render.Mesh> meshes = new ArrayList<>();
        meshes.add(groundMesh);
        meshes.add(waterMesh);
        meshes.add(model.getMesh());

        clearRtMeshes();
        for (var m : meshes) addRtMesh(m);

        // Tag water material id = 1, others = 0
        softwareRT.setMeshMaterialId(waterMesh, 1);
        softwareRT.setMeshMaterialId(groundMesh, 0);
        softwareRT.setMeshMaterialId(model.getMesh(), 0);

        // If model has a diffuse texture, bind it for albedo sampling in the path tracer.
        Texture albedo = (model.getTextures() != null && !model.getTextures().isEmpty()) ? model.getTextures().get(0) : null;
        if (albedo != null) {
            softwareRT.setAlbedoTextureId(albedo.getId());
        }

        // Build BVH once for this static test scene.
        softwareRT.buildAccelerationStructures(rtMeshes);
    }

    @Override
    protected void update(float deltaTime) {
        // 获取相机
        com.zenith.render.Camera camera = getCamera();
        org.joml.Vector3f currentPos = camera.getTransform().getPosition();
        org.joml.Vector3f currentForward = camera.getForward();

        // 检查相机是否发生了移动或旋转
        boolean moved = currentPos.distanceSquared(lastCamPos) > 0.0001f;
        boolean rotated = currentForward.distanceSquared(lastCamForward) > 0.0001f;

        if (moved || rotated) {
            // 【关键！】：只要相机动了，就强制重置光追画面的累积！
            if (softwareRT != null) {
                softwareRT.resetAccumulation();
            }
            // 更新记录
            lastCamPos.set(currentPos);
            lastCamForward.set(currentForward);
        }
    }

    @Override
    protected void renderScene() {
        // RT_MODE=0 means engine won't call this in the main loop.
        // Keep as no-op.
    }

    @Override
    protected void renderAfterOpaqueScene() {
        // no-op
    }

    private static float[] makePlane(float w, float d, float y, float[] rgba) {
        // Two triangles, 6 vertices, each vertex = 3+3+2+4 = 12 floats
        float hw = w * 0.5f;
        float hd = d * 0.5f;
        float nx = 0, ny = 1, nz = 0;
        // positions (x,z), uv (0..1)
        return new float[] {
                -hw, y, -hd,  nx,ny,nz,  0,0,  rgba[0],rgba[1],rgba[2],rgba[3],
                -hw, y,  hd,  nx,ny,nz,  0,1,  rgba[0],rgba[1],rgba[2],rgba[3],
                 hw, y,  hd,  nx,ny,nz,  1,1,  rgba[0],rgba[1],rgba[2],rgba[3],

                -hw, y, -hd,  nx,ny,nz,  0,0,  rgba[0],rgba[1],rgba[2],rgba[3],
                 hw, y,  hd,  nx,ny,nz,  1,1,  rgba[0],rgba[1],rgba[2],rgba[3],
                 hw, y, -hd,  nx,ny,nz,  1,0,  rgba[0],rgba[1],rgba[2],rgba[3],
        };
    }

    public static void main(String[] args) {
        new RayTracingRealityTest().start();
    }
}
