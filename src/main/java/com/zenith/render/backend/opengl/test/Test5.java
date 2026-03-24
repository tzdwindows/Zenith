package com.zenith.render.backend.opengl.test;

import com.zenith.common.math.Transform;
import com.zenith.common.utils.InternalLogger;
import com.zenith.render.Window;
import com.zenith.render.VertexLayout;
import com.zenith.render.backend.opengl.*;
import com.zenith.render.backend.opengl.shader.LavaShader;
import com.zenith.render.backend.opengl.shader.WaterShader;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

public class Test5 {
    private static float yaw = 0.0f;
    private static float pitch = 0.4f;
    private static double lastX, lastY;
    private static boolean isLeftMouseDown = false;
    private static float distance = 18.0f;
    private static Vector3f target = new Vector3f(0, 0, 0);

    public static void main(String[] args) throws Exception {
        InternalLogger.info("Starting Test5: Tiled Lava & Water Interaction...");

        GLWindow window = new GLWindow("Zenith Engine - Tiled Fluid Test", 1280, 720);
        window.init();

        GLCamera camera = new GLCamera();
        camera.getProjection().updateSize(1280, 720);

        LavaShader lavaShader = new LavaShader();
        WaterShader waterShader = new WaterShader();

        VertexLayout layout = new VertexLayout();
        layout.pushFloat("aPos", 3);
        layout.pushFloat("aNormal", 3);
        layout.pushFloat("aTexCoord", 2);
        layout.pushFloat("aColor", 4);
        layout.pushFloat("aBoneIds", 4);
        layout.pushFloat("aWeights", 4);

        // 1. 生成两个平行的平面
        // 水面平面
        GLMesh waterPlane = new GLMesh(65000, layout);
        waterPlane.updateVertices(generatePlaneData(20.0f, 20.0f, 100, 100));

        // 岩浆平面 (平铺，稍微偏移位置以产生交错感)
        GLMesh lavaPlane = new GLMesh(65000, layout);
        lavaPlane.updateVertices(generatePlaneData(20.0f, 20.0f, 100, 100));

        // 2. 设置变换
        Transform waterTransform = new Transform();
        waterTransform.setPosition(-10.5f, 0, 0); // 左侧水域

        Transform lavaTransform = new Transform();
        lavaTransform.setPosition(10.5f, 0.05f, 0); // 右侧岩浆，稍微抬高一点防止深度冲突

        Vector3f sunDir = new Vector3f(1.0f, 1.0f, 1.0f).normalize();

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
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        while (!window.shouldClose()) {
            float time = (float) glfwGetTime();

            // 更新摄像机
            float camX = (float) (Math.cos(pitch) * Math.sin(yaw) * distance);
            float camY = (float) (Math.sin(pitch) * distance);
            float camZ = (float) (Math.cos(pitch) * Math.cos(yaw) * distance);
            camera.getTransform().setPosition(target.x + camX, target.y + camY, target.z + camZ);
            camera.lookAt(target, new Vector3f(0, 1, 0));

            glClearColor(0.01f, 0.01f, 0.015f, 1.0f);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            Matrix4f viewProj = new Matrix4f(camera.getProjectionMatrix()).mul(camera.getViewMatrix());

            // --- 渲染平铺岩浆 ---
            lavaShader.bind();
            // 注意：因为现在岩浆是平面，我们将光照位置设在中心上方模拟自发光感
            Vector3f lavaCenterPos = lavaTransform.getPosition();
            lavaShader.update(viewProj, lavaTransform.getModelMatrix(), camera.getTransform().getPosition(), time);
            lavaShader.setParams(new Vector3f(1.8f, 0.45f, 0.05f), 0.2f); // 增强亮度
            lavaPlane.render();

            // --- 渲染流水 ---
            waterShader.bind();
            waterShader.setUniform("u_ViewProjection", viewProj);
            waterShader.setUniform("u_Model", waterTransform.getModelMatrix());
            waterShader.setUniform("u_ViewPos", camera.getTransform().getPosition());
            waterShader.setUniform("u_Time", time);

            // 消除警告：传入所有 Shader 依赖的 Uniform
            waterShader.setUniform("u_LightDir", sunDir);
            waterShader.setUniform("u_DeepColor", new Vector3f(0.01f, 0.06f, 0.12f));
            waterShader.setUniform("u_ShallowColor", new Vector3f(0.2f, 0.7f, 0.8f));

            // 物理交互：水面接收来自旁边岩浆平面的橙色漫反射
            //waterShader.setUniform("u_PointLightPos", lavaCenterPos);
            //waterShader.setUniform("u_PointLightColor", new Vector3f(2.0f, 0.5f, 0.1f));

            waterPlane.render();

            window.update();
        }

        lavaPlane.dispose();
        waterPlane.dispose();
        window.dispose();
    }

    private static float[] generatePlaneData(float width, float depth, int xDiv, int zDiv) {
        List<Float> v = new ArrayList<>();
        float xStep = width / xDiv;
        float zStep = depth / zDiv;
        float xOffset = width / 2.0f;
        float zOffset = depth / 2.0f;

        for (int z = 0; z <= zDiv; z++) {
            for (int x = 0; x <= xDiv; x++) {
                float px = x * xStep - xOffset;
                float pz = z * zStep - zOffset;
                v.add(px); v.add(0.0f); v.add(pz); // Pos
                v.add(0.0f); v.add(1.0f); v.add(0.0f); // Normal
                v.add((float)x / xDiv); v.add((float)z / zDiv); // UV
                v.add(1.0f); v.add(1.0f); v.add(1.0f); v.add(1.0f); // Color
                v.add(0.0f); v.add(0.0f); v.add(0.0f); v.add(0.0f); // Bone
                v.add(1.0f); v.add(0.0f); v.add(0.0f); v.add(0.0f); // Weight
            }
        }

        List<Float> indices = new ArrayList<>();
        for (int z = 0; z < zDiv; z++) {
            for (int x = 0; x < xDiv; x++) {
                int start = z * (xDiv + 1) + x;
                addVertex(indices, v, start);
                addVertex(indices, v, start + xDiv + 1);
                addVertex(indices, v, start + 1);
                addVertex(indices, v, start + 1);
                addVertex(indices, v, start + xDiv + 1);
                addVertex(indices, v, start + xDiv + 2);
            }
        }
        float[] res = new float[indices.size()];
        for(int i=0; i<res.length; i++) res[i] = indices.get(i);
        return res;
    }

    private static void addVertex(List<Float> dest, List<Float> src, int index) {
        for(int i = 0; i < 20; i++) dest.add(src.get(index * 20 + i));
    }
}