package com.zenith.render.backend.opengl.test;

import com.zenith.common.utils.InternalLogger;
import com.zenith.render.Window;
import com.zenith.render.VertexLayout;
import com.zenith.render.backend.opengl.*;
import com.zenith.render.backend.opengl.shader.SkyShader;
import com.zenith.render.backend.opengl.shader.WaterShader;
import com.zenith.render.backend.opengl.shader.RainShader;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL20.*;

public class Test6 {
    private static Vector3f camPos = new Vector3f(0, 6, 40);
    private static Vector3f camFront = new Vector3f(0, -0.1f, -1).normalize();
    private static Vector3f camUp = new Vector3f(0, 1, 0);

    private static float yaw = -90.0f, pitch = -5.0f;
    private static double lastX = 640, lastY = 360;
    private static boolean firstMouse = true;
    private static boolean[] keys = new boolean[1024];

    private static Vector4f[] splashData = new Vector4f[4];
    static {
        for (int i = 0; i < 4; i++) splashData[i] = new Vector4f(0, 0, 0, -1.0f);
    }

    private static final int RAIN_COUNT = 2000;

    public static void main(String[] args) throws Exception {
        InternalLogger.info("Starting Test6: Cinematic GPU Rain Simulation...");

        GLWindow window = new GLWindow("Zenith Engine - Cinematic Storm", 1280, 720);
        window.init();
        glfwSetInputMode(window.getHandle(), GLFW_CURSOR, GLFW_CURSOR_DISABLED);

        GLCamera camera = new GLCamera();
        camera.getProjection().updateSize(1280, 720);

        SkyShader skyShader = new SkyShader();
        WaterShader waterShader = new WaterShader();
        RainShader rainShader = new RainShader(); // 加载新的 GPU 雨水着色器

        VertexLayout layout = new VertexLayout();
        layout.pushFloat("aPos", 3);
        layout.pushFloat("aNormal", 3);
        layout.pushFloat("aTexCoord", 2);
        layout.pushFloat("aColor", 4);
        layout.pushFloat("aBoneIds", 4);
        layout.pushFloat("aWeights", 4);

        float[] waterData = generatePlaneData(1000.0f, 1000.0f, 300, 300);
        GLMesh waterMesh = new GLMesh(waterData.length / 20, layout);
        waterMesh.updateVertices(waterData);

        float[] skyData = generateSkyBoxData();
        GLMesh skyBox = new GLMesh(skyData.length / 20, layout);
        skyBox.updateVertices(skyData);

        // --- 核心：生成包含四万个面片的超级网格 ---
        float[] gpuRainData = generateGPURainMesh(RAIN_COUNT);
        GLMesh rainMesh = new GLMesh(gpuRainData.length / 20, layout);
        rainMesh.updateVertices(gpuRainData);

        window.setEventListener(new Window.WindowEventListener() {
            @Override public void onKey(int k, int s, int a, int m) {
                if (k >= 0 && k < 1024) keys[k] = (a != GLFW_RELEASE);
                if (k == GLFW_KEY_ENTER && a == GLFW_PRESS) triggerRandomSplash();
            }
            @Override public void onCursorPos(double x, double y) {
                if (firstMouse) { lastX = x; lastY = y; firstMouse = false; }
                float offsetX = (float)(x - lastX) * 0.1f, offsetY = (float)(lastY - y) * 0.1f;
                lastX = x; lastY = y;
                yaw += offsetX; pitch = Math.max(-89f, Math.min(89f, pitch + offsetY));
                camFront.set((float)(Math.cos(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch))),
                        (float)(Math.sin(Math.toRadians(pitch))),
                        (float)(Math.sin(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)))).normalize();
            }
            @Override public void onResize(int w, int h) { glViewport(0, 0, w, h); camera.getProjection().updateSize(w, h); }
            @Override public void onMouseButton(int b, int a, int m) {}
            @Override public void onScroll(double x, double y) {}
        });

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        // 雨滴使用叠加混合 (Additive-like Blending) 增强高光质感
        glBlendFunc(GL_SRC_ALPHA, GL_ONE);

        long lastTime = System.currentTimeMillis();

        while (!window.shouldClose()) {
            long currentTime = System.currentTimeMillis();
            float deltaTime = (currentTime - lastTime) / 1000.0f;
            lastTime = currentTime;

            float time = (float) glfwGetTime();
            handleInput(10.0f * deltaTime);
            updateSplashes(deltaTime);

            camera.getTransform().setPosition(camPos.x, camPos.y, camPos.z);
            camera.lookAt(new Vector3f(camPos).add(camFront), camUp);

            glClearColor(0.05f, 0.08f, 0.1f, 1.0f); // 让环境更暗，衬托出白色的雨丝
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            Matrix4f view = camera.getViewMatrix();
            Matrix4f proj = camera.getProjectionMatrix();
            Vector3f sunDir = new Vector3f(0.8f, 0.3f, 0.8f).normalize();

            // --- 1. 天空 ---
            glDepthFunc(GL_LEQUAL);
            glDepthMask(false);
            skyShader.bind();
            skyShader.setUniform("u_ViewProjection", new Matrix4f(proj).mul(new Matrix4f(view).setTranslation(0, 0, 0)));
            skyShader.setUniform("u_SunDir", sunDir);
            skyShader.setUniform("u_Time", time);
            skyShader.setUniform("u_CloudCoverage", 0.9f);
            skyShader.setUniform("u_CloudSpeed", 0.05f);
            skyBox.render();
            glDepthMask(true);
            glDepthFunc(GL_LESS);

            // --- 2. 水面 ---
            // 恢复正常的透明度混合
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            waterShader.bind();
            waterShader.setUniform("u_ViewProjection", new Matrix4f(proj).mul(view));
            waterShader.setUniform("u_Model", new Matrix4f().translation(0, 0, 0));
            waterShader.setUniform("u_ViewPos", camPos);
            waterShader.setUniform("u_LightDir", sunDir);
            waterShader.setUniform("u_Time", time);
            waterShader.setUniform("u_DeepColor", new Vector3f(0.01f, 0.05f, 0.12f));
            waterShader.setUniform("u_ShallowColor", new Vector3f(0.0f, 0.45f, 0.6f));
            waterShader.setRainIntensity(0.9f);
            waterShader.setSplashes(splashData);
            waterMesh.render();

            // --- 3. 渲染 GPU 暴雨 ---
            glDepthMask(false); // 半透明不写深度
            glBlendFunc(GL_SRC_ALPHA, GL_ONE); // 发光混合模式

            rainShader.bind();
            rainShader.setUniform("u_ViewProjection", new Matrix4f(proj).mul(view));
            rainShader.setUniform("u_ViewPos", camPos);
            rainShader.setUniform("u_Time", time);
            // 给定一个风向和风力 (X 轴和 Z 轴的偏移强度)
            rainShader.setUniform("u_Wind", new Vector2f(-0.5f, -0.2f));

            rainShader.setUniform("u_SunDir", sunDir);
            rainShader.setUniform("u_SunIntensity", new Vector3f(2.0f, 2.0f, 2.0f));
            rainShader.setUniform("u_AmbientSkyColor", new Vector3f(0.2f, 0.3f, 0.4f));

            // 一次 Draw Call 渲染四万滴雨，极为震撼
            rainMesh.render();

            glDepthMask(true);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

            window.update();
        }
        window.dispose();
    }

    // --- 一次性生成 40,000 个面片的顶点数据，所有动画全部交给 GPU ---
    private static float[] generateGPURainMesh(int count) {
        float[] v = new float[count * 4 * 20];
        int p = 0;

        for (int i = 0; i < count; i++) {
            // 修正：让雨滴分布在以原点为中心的 [-40, 40] 范围内
            float rx = (float) Math.random() * 80f - 40f;
            float ry = (float) Math.random() * 40f;
            float rz = (float) Math.random() * 80f - 40f;
            float speed = 40.0f + (float) Math.random() * 40.0f;

            int base = i * 4;
            // 这里的 rx, ry, rz 会传递给 aColor (location 3)
            fillVert(v, base,     -0.015f,  1.5f, 0,  rx, ry, rz, speed,  0, 0);
            fillVert(v, base + 1, -0.015f, -1.5f, 0,  rx, ry, rz, speed,  0, 1);
            fillVert(v, base + 2,  0.015f, -1.5f, 0,  rx, ry, rz, speed,  1, 1);
            fillVert(v, base + 3,  0.015f,  1.5f, 0,  rx, ry, rz, speed,  1, 0);
        }

        // 索引数据
        int[] indices = new int[count * 6];
        int idx = 0;
        for (int i = 0; i < count; i++) {
            int offset = i * 4;
            indices[idx++] = offset;
            indices[idx++] = offset + 1;
            indices[idx++] = offset + 2;
            indices[idx++] = offset + 2;
            indices[idx++] = offset + 3;
            indices[idx++] = offset;
        }

        float[] res = new float[indices.length * 20];
        for (int i = 0; i < indices.length; i++) {
            System.arraycopy(v, indices[i] * 20, res, i * 20, 20);
        }
        return res;
    }

    private static void fillVert(float[] arr, int index,
                                 float px, float py, float pz,
                                 float rx, float ry, float rz, float speed,
                                 float u, float v) {
        int i = index * 20;
        arr[i] = px; arr[i+1] = py; arr[i+2] = pz;      // aPos (用来拉伸四边形的局部偏移)
        arr[i+3] = 0; arr[i+4] = 0; arr[i+5] = 1;       // aNormal
        arr[i+6] = u; arr[i+7] = v;                     // aTexCoord (用于Shader内控制透明度拉丝)
        arr[i+8] = rx; arr[i+9] = ry; arr[i+10] = rz; arr[i+11] = speed; // aColor 存放随机坐标和速度
    }

    private static void updateSplashes(float dt) {
        for (int i = 0; i < 4; i++) {
            if (splashData[i].w >= 0.0f) {
                splashData[i].w += dt;
                if (splashData[i].w > 5.0f) splashData[i].w = -1.0f;
            }
        }
    }

    private static void triggerRandomSplash() {
        for (int i = 0; i < 4; i++) {
            if (splashData[i].w < 0.0f) {
                float rx = camPos.x + camFront.x * 20.0f + (float) (Math.random() * 10 - 5);
                float rz = camPos.z + camFront.z * 20.0f + (float) (Math.random() * 10 - 5);
                splashData[i].set(rx, 0, rz, 0.0f);
                break;
            }
        }
    }

    private static void handleInput(float speed) {
        Vector3f forward = new Vector3f(camFront.x, 0, camFront.z).normalize();
        Vector3f right = new Vector3f();
        forward.cross(camUp, right).normalize();
        if (keys[GLFW_KEY_W]) camPos.add(new Vector3f(forward).mul(speed));
        if (keys[GLFW_KEY_S]) camPos.sub(new Vector3f(forward).mul(speed));
        if (keys[GLFW_KEY_A]) camPos.sub(new Vector3f(right).mul(speed));
        if (keys[GLFW_KEY_D]) camPos.add(new Vector3f(right).mul(speed));
        if (keys[GLFW_KEY_SPACE]) camPos.y += speed;
        if (keys[GLFW_KEY_LEFT_SHIFT]) camPos.y -= speed;
        if (keys[GLFW_KEY_ESCAPE]) glfwSetWindowShouldClose(glfwGetCurrentContext(), true);
    }

    private static float[] generatePlaneData(float w, float d, int xn, int zn) {
        float[] vertices = new float[(xn + 1) * (zn + 1) * 20];
        for (int z = 0; z <= zn; z++) {
            for (int x = 0; x <= xn; x++) {
                int i = (z * (xn + 1) + x) * 20;
                vertices[i] = (float) x / xn * w - w / 2f;
                vertices[i + 1] = 0;
                vertices[i + 2] = (float) z / zn * d - d / 2f;
                vertices[i + 3] = 0; vertices[i + 4] = 1.0f; vertices[i + 5] = 0;
                vertices[i + 6] = (float) x / xn; vertices[i + 7] = (float) z / zn;
                vertices[i + 8] = 1; vertices[i + 9] = 1; vertices[i + 10] = 1; vertices[i + 11] = 1;
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

    private static float[] generateSkyBoxData() {
        float[] c = {-1, 1, -1, -1, -1, -1, 1, -1, -1, 1, -1, -1, 1, 1, -1, -1, 1, -1, -1, -1, 1, -1, -1, -1, -1, 1, -1, -1, 1, -1, -1, 1, 1, -1, -1, 1, 1, -1, -1, 1, -1, 1, 1, 1, 1, 1, 1, 1, 1, 1, -1, 1, -1, -1, -1, -1, 1, -1, 1, 1, 1, 1, 1, 1, 1, 1, 1, -1, 1, -1, -1, 1, -1, 1, -1, 1, 1, -1, 1, 1, 1, 1, 1, 1, -1, 1, 1, -1, 1, -1, -1, -1, -1, -1, -1, 1, 1, -1, -1, 1, -1, -1, -1, -1, 1, 1, -1, 1};
        float[] d = new float[c.length / 3 * 20];
        for (int i = 0; i < c.length / 3; i++) {
            d[i * 20] = c[i * 3]; d[i * 20 + 1] = c[i * 3 + 1]; d[i * 20 + 2] = c[i * 3 + 2];
            d[i * 20 + 8] = 1; d[i * 20 + 9] = 1; d[i * 20 + 10] = 1; d[i * 20 + 11] = 1;
        }
        return d;
    }
}