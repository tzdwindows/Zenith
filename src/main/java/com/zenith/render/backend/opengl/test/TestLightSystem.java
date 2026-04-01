package com.zenith.render.backend.opengl.test;

import com.zenith.common.math.Color;
import com.zenith.common.math.Transform;
import com.zenith.common.utils.InternalLogger;
import com.zenith.render.VertexLayout;
import com.zenith.render.backend.opengl.*;
import com.zenith.render.backend.opengl.shader.StandardShader;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.glfw.GLFW.glfwGetTime;

public class TestLightSystem {
    public static void main(String[] args) throws Exception {
        InternalLogger.info("Starting 3D Cube Light Test - V2...");

        GLWindow window = new GLWindow("Zenith Engine - 3D Light Test", 1280, 720);
        window.init();

        StandardShader shader = new StandardShader();

        Matrix4f projection = new Matrix4f().perspective((float) Math.toRadians(45.0f), 1280f/720f, 0.1f, 1000.0f);
        Vector3f cameraPos = new Vector3f(0, 3, 8);
        Matrix4f view = new Matrix4f().lookAt(cameraPos, new Vector3f(0, 0, 0), new Vector3f(0, 1, 0));
        Matrix4f viewProj = projection.mul(view);

        VertexLayout layout = new VertexLayout();
        layout.pushFloat("aPos", 3);
        layout.pushFloat("aNormal", 3);
        layout.pushFloat("aTexCoord", 2);
        layout.pushFloat("aColor", 4);
        layout.pushFloat("aBoneIds", 4);
        layout.pushFloat("aWeights", 4);

        GLMesh cubeMesh = createCube(layout);

        // --- 核心修改：大幅提升光强 ---
        // 红色灯光，强度从 50 提升到 400
        GLLight redLight = new GLLight(new Vector3f(-3, 2, 2), new Color(1, 0, 0, 1), 400.0f);
        // 蓝色灯光，强度 400
        GLLight blueLight = new GLLight(new Vector3f(3, 2, 2), new Color(0, 0, 1, 1), 400.0f);
        // 环境光稍微调亮一点，确保能看到背光面
        GLLight ambient = new GLLight(new Vector3f(0), new Color(0.2f, 0.2f, 0.2f, 1.0f), 1.0f);

        Transform cubeTransform = new Transform();
        // 基础颜色调亮一点 (0.8 灰色)，非金属
        Color cubeBaseColor = new Color(0.8f, 0.8f, 0.8f, 0.0f);

        glEnable(GL_DEPTH_TEST);

        while (!window.shouldClose()) {
            glClearColor(0.1f, 0.1f, 0.1f, 1.0f);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            // 让红色灯光左右晃动，观察动态光影
            float time = (float) glfwGetTime();
            redLight.getPosition().x = (float) Math.sin(time) * 5.0f;

            cubeTransform.getRotation().rotateY(0.01f);

            shader.bind();
            shader.setBones(null);
            shader.setUseTexture(false);
            //shader.setEmissive(false);

            shader.clearLights();
            shader.addLight(ambient);
            shader.addLight(redLight);
            shader.addLight(blueLight);

            // 务必传入正确的摄像机位置，否则 PBR 高光会计算错误
            shader.applyLights(cameraPos);

            shader.setup(viewProj, cubeTransform.getModelMatrix(), cubeBaseColor);
            cubeMesh.render();

            window.update();
        }

        cubeMesh.dispose();
        window.dispose();
    }

    private static GLMesh createCube(VertexLayout layout) {
        // ... (保持之前的 createCube 代码不变，建议补全 6 个面以获得最佳效果)
        float[] v = {
                // 前
                -1,-1, 1,  0,0,1, 0,0, 1,1,1,1, 0,0,0,0, 1,0,0,0,
                1,-1, 1,  0,0,1, 1,0, 1,1,1,1, 0,0,0,0, 1,0,0,0,
                1, 1, 1,  0,0,1, 1,1, 1,1,1,1, 0,0,0,0, 1,0,0,0,
                1, 1, 1,  0,0,1, 1,1, 1,1,1,1, 0,0,0,0, 1,0,0,0,
                -1, 1, 1,  0,0,1, 0,1, 1,1,1,1, 0,0,0,0, 1,0,0,0,
                -1,-1, 1,  0,0,1, 0,0, 1,1,1,1, 0,0,0,0, 1,0,0,0,
                // 后
                -1,-1,-1,  0,0,-1, 0,0, 1,1,1,1, 0,0,0,0, 1,0,0,0,
                1,-1,-1,  0,0,-1, 1,0, 1,1,1,1, 0,0,0,0, 1,0,0,0,
                1, 1,-1,  0,0,-1, 1,1, 1,1,1,1, 0,0,0,0, 1,0,0,0,
                1, 1,-1,  0,0,-1, 1,1, 1,1,1,1, 0,0,0,0, 1,0,0,0,
                -1, 1,-1,  0,0,-1, 0,1, 1,1,1,1, 0,0,0,0, 1,0,0,0,
                -1,-1,-1,  0,0,-1, 0,0, 1,1,1,1, 0,0,0,0, 1,0,0,0,
                // 上
                -1, 1,-1,  0,1,0, 0,1, 1,1,1,1, 0,0,0,0, 1,0,0,0,
                -1, 1, 1,  0,1,0, 0,0, 1,1,1,1, 0,0,0,0, 1,0,0,0,
                1, 1, 1,  0,1,0, 1,0, 1,1,1,1, 0,0,0,0, 1,0,0,0,
                1, 1, 1,  0,1,0, 1,0, 1,1,1,1, 0,0,0,0, 1,0,0,0,
                1, 1,-1,  0,1,0, 1,1, 1,1,1,1, 0,0,0,0, 1,0,0,0,
                -1, 1,-1,  0,1,0, 0,1, 1,1,1,1, 0,0,0,0, 1,0,0,0,
                // 下
                -1,-1,-1,  0,-1,0, 0,1, 1,1,1,1, 0,0,0,0, 1,0,0,0,
                1,-1,-1,  0,-1,0, 1,1, 1,1,1,1, 0,0,0,0, 1,0,0,0,
                1,-1, 1,  0,-1,0, 1,0, 1,1,1,1, 0,0,0,0, 1,0,0,0,
                1,-1, 1,  0,-1,0, 1,0, 1,1,1,1, 0,0,0,0, 1,0,0,0,
                -1,-1, 1,  0,-1,0, 0,0, 1,1,1,1, 0,0,0,0, 1,0,0,0,
                -1,-1,-1,  0,-1,0, 0,1, 1,1,1,1, 0,0,0,0, 1,0,0,0,
                // 左
                -1,-1,-1, -1,0,0, 0,0, 1,1,1,1, 0,0,0,0, 1,0,0,0,
                -1,-1, 1, -1,0,0, 1,0, 1,1,1,1, 0,0,0,0, 1,0,0,0,
                -1, 1, 1, -1,0,0, 1,1, 1,1,1,1, 0,0,0,0, 1,0,0,0,
                -1, 1, 1, -1,0,0, 1,1, 1,1,1,1, 0,0,0,0, 1,0,0,0,
                -1, 1,-1, -1,0,0, 0,1, 1,1,1,1, 0,0,0,0, 1,0,0,0,
                -1,-1,-1, -1,0,0, 0,0, 1,1,1,1, 0,0,0,0, 1,0,0,0,
                // 右
                1,-1,-1,  1,0,0, 0,0, 1,1,1,1, 0,0,0,0, 1,0,0,0,
                1, 1,-1,  1,0,0, 0,1, 1,1,1,1, 0,0,0,0, 1,0,0,0,
                1, 1, 1,  1,0,0, 1,1, 1,1,1,1, 0,0,0,0, 1,0,0,0,
                1, 1, 1,  1,0,0, 1,1, 1,1,1,1, 0,0,0,0, 1,0,0,0,
                1,-1, 1,  1,0,0, 1,0, 1,1,1,1, 0,0,0,0, 1,0,0,0,
                1,-1,-1,  1,0,0, 0,0, 1,1,1,1, 0,0,0,0, 1,0,0,0
        };
        GLMesh mesh = new GLMesh(v.length / 20, layout);
        mesh.updateVertices(v);
        return mesh;
    }
}