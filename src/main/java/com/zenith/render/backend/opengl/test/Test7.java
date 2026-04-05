package com.zenith.render.backend.opengl.test;

import com.zenith.common.utils.InternalLogger;
import com.zenith.render.Window;
import com.zenith.render.VertexLayout;
import com.zenith.render.backend.opengl.*;
import com.zenith.render.backend.opengl.shader.SkyShader;
import com.zenith.render.backend.opengl.shader.WaterShader;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL20.*;

/**
 * Test6: 晴空万里的水面环境渲染
 * 特性：高光泽水面、低云量大气、移除雨水粒子
 */
public class Test7 {
    // 摄像机参数
    private static Vector3f camPos = new Vector3f(0, 15, 60);
    private static Vector3f camFront = new Vector3f(0, -0.2f, -1).normalize();
    private static Vector3f camUp = new Vector3f(0, 1, 0);

    private static float yaw = -90.0f, pitch = -10.0f;
    private static double lastX = 640, lastY = 360;
    private static boolean firstMouse = true;
    private static boolean[] keys = new boolean[1024];

    public static void main(String[] args) throws Exception {
        InternalLogger.info("Starting Test6: Clear Sky & Calm Water...");

        // 1. 初始化窗口与上下文
        GLWindow window = new GLWindow("Zenith Engine - Sunny Day", 1280, 720);
        window.init();
        glfwSetInputMode(window.getHandle(), GLFW_CURSOR, GLFW_CURSOR_DISABLED);

        // 2. 初始化摄像机与着色器
        GLCamera camera = new GLCamera();
        camera.getProjection().updateSize(1280, 720);

        SkyShader skyShader = new SkyShader();
        WaterShader waterShader = new WaterShader();

        // 3. 配置顶点布局
        VertexLayout layout = new VertexLayout();
        layout.pushFloat("aPos", 3);
        layout.pushFloat("aNormal", 3);
        layout.pushFloat("aTexCoord", 2);
        layout.pushFloat("aColor", 4);
        layout.pushFloat("aBoneIds", 4);
        layout.pushFloat("aWeights", 4);

        // 4. 生成几何体数据
        // 水面网格
        float[] waterData = generatePlaneData(1200.0f, 1200.0f, 200, 200);
        GLMesh waterMesh = new GLMesh(waterData.length / 20, layout);
        waterMesh.updateVertices(waterData);

        // 天空盒网格
        float[] skyData = generateSkyBoxData();
        GLMesh skyBox = new GLMesh(skyData.length / 20, layout);
        skyBox.updateVertices(skyData);

        // 5. 输入回调设置
        window.setEventListener(new Window.WindowEventListener() {
            @Override public void onKey(int k, int s, int a, int m) {
                if (k >= 0 && k < 1024) keys[k] = (a != GLFW_RELEASE);
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
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        long lastTime = System.currentTimeMillis();

        // --- 主渲染循环 ---
        while (!window.shouldClose()) {
            long currentTime = System.currentTimeMillis();
            float deltaTime = (currentTime - lastTime) / 1000.0f;
            lastTime = currentTime;

            float time = (float) glfwGetTime();
            handleInput(15.0f * deltaTime);

            camera.getTransform().setPosition(camPos.x, camPos.y, camPos.z);
            camera.lookAt(new Vector3f(camPos).add(camFront), camUp);

            // 晴朗的背景底色
            glClearColor(0.45f, 0.65f, 0.85f, 1.0f);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            Matrix4f view = camera.getViewMatrix();
            Matrix4f proj = camera.getProjectionMatrix();

            // 定义明亮的太阳光 (由高处照射)
            Vector3f sunDir = new Vector3f(0.4f, 0.8f, -0.4f).normalize();
            Vector3f sunColor = new Vector3f(2.4f, 2.2f, 1.9f); // 暖白色强光

            // --- 1. 渲染天空 (晴朗参数) ---
            glDepthFunc(GL_LEQUAL);
            glDepthMask(false);
            skyShader.bind();
            skyShader.setUniform("u_ViewProjection", new Matrix4f(proj).mul(new Matrix4f(view).setTranslation(0, 0, 0)));
            skyShader.setUniform("u_SunDir", sunDir);
            skyShader.setUniform("u_Time", time);
            skyShader.setUniform("u_CloudCoverage", 0.15f); // 极低云量
            skyShader.setUniform("u_CloudSpeed", 0.005f);  // 云层几乎静止
            skyBox.render();
            glDepthMask(true);
            glDepthFunc(GL_LESS);

            // --- 2. 渲染清澈水面 ---
            waterShader.bind();
            waterShader.setUniform("u_ViewProjection", new Matrix4f(proj).mul(view));
            waterShader.setUniform("u_Model", new Matrix4f());
            waterShader.setUniform("u_ViewPos", camPos);
            waterShader.setUniform("u_LightDir", sunDir);
            waterShader.setUniform("u_Time", time);

            // 水面色彩：深蓝到浅绿的渐变
            waterShader.setUniform("u_DeepColor", new Vector3f(0.01f, 0.15f, 0.35f));
            waterShader.setUniform("u_ShallowColor", new Vector3f(0.1f, 0.5f, 0.7f));

            waterMesh.render();

            // --- 3. (雨水渲染已移除) ---
            // 晴天不再调用 rainMesh.render()

            window.update();
        }

        window.dispose();
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
                vertices[i + 3] = 0; vertices[i + 4] = 1.0f; vertices[i + 5] = 0; // Normal
                vertices[i + 6] = (float) x / xn; vertices[i + 7] = (float) z / zn; // UV
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
        float[] c = {-1,1,-1, -1,-1,-1, 1,-1,-1, 1,-1,-1, 1,1,-1, -1,1,-1, -1,-1,1, -1,-1,-1, -1,1,-1, -1,1,-1, -1,1,1, -1,-1,1, 1,-1,-1, 1,-1,1, 1,1,1, 1,1,1, 1,1,-1, 1,-1,-1, -1,-1,1, -1,1,1, 1,1,1, 1,1,1, 1,-1,1, -1,-1,1, -1,1,-1, 1,1,-1, 1,1,1, 1,1,1, -1,1,1, -1,1,-1, -1,-1,-1, -1,-1,1, 1,-1,-1, 1,-1,-1, -1,-1,1, 1,-1,1};
        float[] d = new float[c.length / 3 * 20];
        for (int i = 0; i < c.length / 3; i++) {
            d[i*20] = c[i*3] * 500f; d[i*20+1] = c[i*3+1] * 500f; d[i*20+2] = c[i*3+2] * 500f;
        }
        return d;
    }
}