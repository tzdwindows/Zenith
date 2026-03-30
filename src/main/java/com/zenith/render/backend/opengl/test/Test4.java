package com.zenith.render.backend.opengl.test;

import com.zenith.asset.AssetIdentifier;
import com.zenith.asset.AssetResource;
import com.zenith.common.math.Color;
import com.zenith.common.math.Transform;
import com.zenith.common.utils.InternalLogger;
import com.zenith.render.Window;
import com.zenith.render.VertexLayout;
import com.zenith.render.backend.opengl.*;
import com.zenith.render.backend.opengl.shader.StandardShader;
import com.zenith.render.backend.opengl.shader.EmissiveShader;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

public class Test4 {
    private static float yaw = 0.0f;
    private static float pitch = 0.5f;
    private static double lastX, lastY;
    private static boolean isLeftMouseDown = false;
    private static float distance = 12.0f;
    private static Vector3f sunPosition = new Vector3f(15.0f, 20.0f, -15.0f);
    private static Vector3f target = new Vector3f(sunPosition);
    private static Color sunColor = new Color(1.0f, 0.4f, 0.05f, 1.0f);

    public static void main(String[] args) throws Exception {
        InternalLogger.info("Starting Test4 with Cinematic Sun Shader...");

        GLWindow window = new GLWindow("Zenith Engine - Cinematic Sun Test", 1280, 720);
        window.init();

        GLCamera camera = new GLCamera();
        camera.getProjection().updateSize(1280, 720);

        StandardShader pbrShader = new StandardShader();
        EmissiveShader emissiveShader = new EmissiveShader();

        GLModel model = new GLModel("PBRModel", pbrShader);
        try (InputStream is = new FileInputStream("E:\\3d\\document_file_folder\\scene.gltf")) {
            AssetIdentifier id = new AssetIdentifier("engine", "models/scene.gltf");
            AssetResource resource = new AssetResource("GltfModel", id, is, null,0);
            model.load(resource);
        }

        VertexLayout layout = new VertexLayout();
        layout.pushFloat("aPos", 3);
        layout.pushFloat("aNormal", 3);
        layout.pushFloat("aTexCoord", 2);
        layout.pushFloat("aColor", 4);
        layout.pushFloat("aBoneIds", 4);
        layout.pushFloat("aWeights", 4);

        // 提升细分度 (从 40x40 提升到 100x100)，让日冕边缘的柔和过渡更加自然
        GLMesh sunMesh = new GLMesh(65000, layout);
        sunMesh.updateVertices(generateSphereData(4.0f, 100, 100));

        GLLight sunLight = new GLLight(sunPosition, new Color(1.0f, 0.8f, 0.6f, 1.0f), 15.0f);
        GLLight fillLight = new GLLight(new Vector3f(-10, 5, 10), new Color(0.2f, 0.3f, 0.5f, 1.0f), 1.0f);

        window.setEventListener(new Window.WindowEventListener() {
            @Override public void onMouseButton(int b, int a, int m) { if (b == GLFW_MOUSE_BUTTON_LEFT) isLeftMouseDown = (a == GLFW_PRESS); }
            @Override public void onCursorPos(double x, double y) {
                if (isLeftMouseDown) {
                    yaw += (float)(x - lastX) * 0.005f;
                    pitch = Math.max(-1.5f, Math.min(1.5f, pitch + (float)(y - lastY) * 0.005f));
                }
                lastX = x; lastY = y;
            }
            @Override public void onScroll(double x, double y) { distance = Math.max(2.0f, distance - (float)y * 0.8f); }
            @Override public void onResize(int w, int h) { glViewport(0, 0, w, h); camera.getProjection().updateSize(w, h); }
            @Override public void onKey(int k, int s, int a, int m) {}
        });

        glEnable(GL_DEPTH_TEST);
        Transform modelTransform = new Transform();
        Transform sunTransform = new Transform();
        sunTransform.setPosition(sunPosition.x, sunPosition.y, sunPosition.z);

        while (!window.shouldClose()) {
            model.update(0.016f);

            float camX = (float) (Math.cos(pitch) * Math.sin(yaw) * distance);
            float camY = (float) (Math.sin(pitch) * distance);
            float camZ = (float) (Math.cos(pitch) * Math.cos(yaw) * distance);
            camera.getTransform().setPosition(target.x + camX, target.y + camY, target.z + camZ);
            camera.lookAt(target, new Vector3f(0, 1, 0));

            glClearColor(0.02f, 0.02f, 0.03f, 1.0f);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            Matrix4f viewProj = new Matrix4f(camera.getProjectionMatrix()).mul(camera.getViewMatrix());
            float time = (float) glfwGetTime();

            // --- 渲染太阳 ---
            glEnable(GL_BLEND);
            // 修改为标准Alpha混合，这能保留太阳表面的暗色黑子，同时让边缘日冕自然淡出
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

            emissiveShader.bind();
            emissiveShader.setup(viewProj, sunTransform.getModelMatrix(), camera.getTransform().getPosition(), sunColor, 1.5f, time);
            sunMesh.render();
            glDisable(GL_BLEND);

            // --- 渲染主模型 ---
            pbrShader.bind();
            pbrShader.clearLights();
            pbrShader.addLight(sunLight);
            pbrShader.addLight(fillLight);
            pbrShader.applyLights(camera.getTransform().getPosition());
            pbrShader.setEmissive(false);
            pbrShader.setUseTexture(true);
            pbrShader.setup(viewProj, modelTransform.getModelMatrix(), new Color(1, 1, 1, 0.05f));
            model.draw();

            window.update();
        }

        sunMesh.dispose();
        model.dispose();
        window.dispose();
    }

    private static float[] generateSphereData(float radius, int rings, int sectors) {
        // ... (保持你原有的逻辑不变)
        List<Float> v = new ArrayList<>();
        for (int r = 0; r < rings; r++) {
            for (int s = 0; s < sectors; s++) {
                float phi = (float) (r * Math.PI / (rings - 1));
                float theta = (float) (s * 2 * Math.PI / (sectors - 1));
                float x = (float) (Math.sin(phi) * Math.cos(theta));
                float y = (float) Math.cos(phi);
                float z = (float) (Math.sin(phi) * Math.sin(theta));
                v.add(x * radius); v.add(y * radius); v.add(z * radius);
                v.add(x); v.add(y); v.add(z);
                v.add((float)s / (sectors - 1)); v.add((float)r / (rings - 1));
                v.add(1.0f); v.add(1.0f); v.add(1.0f); v.add(1.0f);
                v.add(0.0f); v.add(0.0f); v.add(0.0f); v.add(0.0f);
                v.add(1.0f); v.add(0.0f); v.add(0.0f); v.add(0.0f);
            }
        }
        List<Float> triangleData = new ArrayList<>();
        for (int r = 0; r < rings - 1; r++) {
            for (int s = 0; s < sectors - 1; s++) {
                int first = (r * sectors + s);
                int second = (first + sectors);
                addVertex(triangleData, v, first); addVertex(triangleData, v, second); addVertex(triangleData, v, first + 1);
                addVertex(triangleData, v, second); addVertex(triangleData, v, second + 1); addVertex(triangleData, v, first + 1);
            }
        }
        float[] res = new float[triangleData.size()];
        for(int i=0; i<res.length; i++) res[i] = triangleData.get(i);
        return res;
    }

    private static void addVertex(List<Float> dest, List<Float> src, int index) {
        for(int i=0; i<20; i++) dest.add(src.get(index * 20 + i));
    }
}