package com.zenith.render.backend.opengl.test;

import com.zenith.common.math.Color;
import com.zenith.common.math.Transform;
import com.zenith.common.utils.InternalLogger;
import com.zenith.render.backend.opengl.GLRenderer;
import com.zenith.render.VertexLayout;
import com.zenith.render.backend.opengl.*;
import com.zenith.render.backend.opengl.shader.StandardShader;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.glfw.GLFW.glfwGetCursorPos;

public class Test1 {
    public static void main(String[] args) throws Exception {
        InternalLogger.info("Starting Test1: High-Contrast Lighting on a Paper...");

        // 1. 初始化窗口
        GLWindow window = new GLWindow("Zenith Engine - High-Contrast Paper Test", 1280, 720);
        window.init();

        GLRenderer renderer = new GLRenderer();
        StandardShader shader = new StandardShader();

        Matrix4f uiProj = new Matrix4f().setOrtho(0, 1280, 720, 0, -100.0f, 100.0f);
        renderer.setViewProjection(uiProj);

        // 2. 创建一张“铺满全屏”的纸
        VertexLayout layout = new VertexLayout();
        layout.pushFloat("aPos", 3);      // 3
        layout.pushFloat("aNormal", 3);   // 3 (法线)
        layout.pushFloat("aTexCoord", 2); // 2
        layout.pushFloat("aColor", 4);    // 4
// 总计 12 个 float

        GLMesh paperMesh = new GLMesh(6, layout);
// 计算每个顶点数据 (Pos, Normal, UV, Color)
        float[] paperVertices = {
                // x, y, z,   nx, ny, nz,   u, v,   r, g, b, a
                0f,    0f,    0f,   0f, 0f, 1f,   0f, 0f,  1f, 1f, 1f, 1f,
                1280f, 0f,    0f,   0f, 0f, 1f,   1f, 0f,  1f, 1f, 1f, 1f,
                1280f, 720f,  0f,   0f, 0f, 1f,   1f, 1f,  1f, 1f, 1f, 1f,

                1280f, 720f,  0f,   0f, 0f, 1f,   1f, 1f,  1f, 1f, 1f, 1f,
                0f,    720f,  0f,   0f, 0f, 1f,   0f, 1f,  1f, 1f, 1f, 1f,
                0f,    0f,    0f,   0f, 0f, 1f,   0f, 0f,  1f, 1f, 1f, 1f
        };
        paperMesh.updateVertices(paperVertices);

        // 3. 准备材质与光源
        GLMaterial whitePaperMaterial = new GLMaterial(shader);
        whitePaperMaterial.setColor(Color.WHITE);

        // 创建点光源（电灯）：白色，极高强度，且 Z 轴离纸面非常近 (20-30)
        // 确保强度足够大，例如 2000.0f
        GLLight mouseLight = new GLLight(new Vector3f(640, 360, 30), Color.WHITE, 2000.0f);

        // 创建环境光：为了看到对比，环境光要调得很暗，例如红色环境光增加底色调
        GLLight ambientLight = new GLLight(new Vector3f(0), new Color(0.2f, 0.0f, 0.0f, 1.0f), 1.0f);

        Transform paperTransform = new Transform();

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glEnable(GL_DEPTH_TEST);

        while (!window.shouldClose()) {
            // 背景全黑
            glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            // --- A. 更新动态光源位置 ---
            double[] xPos = new double[1], yPos = new double[1];
            glfwGetCursorPos(window.getHandle(), xPos, yPos);
            // Z=30 保证光是从纸张上方近距离照下来的点光源
            mouseLight.setPosition(new Vector3f((float)xPos[0], (float)yPos[0], 30.0f));

            // --- B. 设置全局光照状态 ---
            shader.bind();
            shader.clearLights();
            shader.addLight(ambientLight);
            shader.addLight(mouseLight);
            shader.applyLights(new Vector3f(640, 360, 500));

// 2. 然后再提交和绘制（这里面会调用被我们刚刚修好的 setup 方法）
            renderer.submit(paperMesh, whitePaperMaterial, paperTransform);
            renderer.flush();
            window.update();
        }

        paperMesh.dispose();
        window.dispose();
    }
}