package com.zenith.render.backend.opengl.test;

import com.zenith.asset.AssetIdentifier;
import com.zenith.asset.AssetResource;
import com.zenith.common.math.Color;
import com.zenith.common.math.Transform;
import com.zenith.common.utils.InternalLogger;
import com.zenith.render.Window;
import com.zenith.render.backend.opengl.GLLight;
import com.zenith.render.backend.opengl.GLModel;
import com.zenith.render.backend.opengl.GLCamera;
import com.zenith.render.backend.opengl.GLWindow;
import com.zenith.render.backend.opengl.shader.StandardShader;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

public class Test3 {
    private static float yaw = 0.0f;
    private static float pitch = 0.5f;
    private static double lastX, lastY;
    private static boolean isLeftMouseDown = false;
    private static boolean isMiddleMouseDown = false;
    private static float distance = 10.0f;
    private static Vector3f target = new Vector3f(0, 0, 0);
    private static double lastFrameTime = 0.0;

    public static void main(String[] args) throws Exception {
        InternalLogger.info("Starting Test3: 3D Model Animation Viewer...");

        GLWindow window = new GLWindow("Zenith Engine - Animation Player", 1280, 720);
        window.init();

        GLCamera camera = new GLCamera();
        camera.getProjection().updateSize(1280, 720);

        window.setEventListener(new Window.WindowEventListener() {
            @Override
            public void onMouseButton(int button, int action, int mods) {
                if (button == GLFW_MOUSE_BUTTON_LEFT) isLeftMouseDown = (action == GLFW_PRESS);
                if (button == GLFW_MOUSE_BUTTON_MIDDLE) isMiddleMouseDown = (action == GLFW_PRESS);
            }

            @Override
            public void onCursorPos(double xpos, double ypos) {
                float dx = (float) (xpos - lastX);
                float dy = (float) (ypos - lastY);
                if (isLeftMouseDown) {
                    yaw += dx * 0.01f;
                    pitch += dy * 0.01f;
                    pitch = Math.max(-1.5f, Math.min(1.5f, pitch));
                } else if (isMiddleMouseDown) {
                    float panSpeed = distance * 0.001f;
                    Vector3f right = new Vector3f((float) Math.cos(yaw), 0, (float) -Math.sin(yaw));
                    target.add(right.mul(-dx * panSpeed));
                    target.add(new Vector3f(0, 1, 0).mul(dy * panSpeed));
                }
                lastX = xpos; lastY = ypos;
            }

            @Override
            public void onScroll(double xoffset, double yoffset) {
                distance -= (float) yoffset * 1.5f;
                distance = Math.max(0.001f, Math.min(500.0f, distance));
            }

            @Override public void onKey(int key, int scancode, int action, int mods) {}
            @Override public void onResize(int width, int height) {
                glViewport(0, 0, width, height);
                camera.getProjection().updateSize(width, height);
            }
        });

        StandardShader shader = new StandardShader();
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);

        GLModel model = new GLModel("MyGLTFModel", shader);
        try (InputStream is = new FileInputStream("E:\\3d\\document_file_folder\\scene.gltf")) {
            AssetIdentifier id = new AssetIdentifier("engine", "models/scene.gltf");
            AssetResource resource = new AssetResource("GltfModel", id, is, null,0);
            model.load(resource);
        }

        // --- 动画处理部分 ---
        List<String> animations = model.getAnimationNames();
        InternalLogger.info("Found animations: " + animations);

        // 播放所有发现的动画 (取决于 GLModel 内部是否支持多轨道，
        // 如果 GLModel 只支持单轨道，则最后调用的 playAnimation 会生效)
        for (String animName : animations) {
            model.playAnimation(animName);
        }

        GLLight sunLight = new GLLight(new Vector3f(10, 20, 10), Color.WHITE, 0.5f);
        Transform modelTransform = new Transform();
        //modelTransform.setScale(0.01f);

        lastFrameTime = glfwGetTime();

        while (!window.shouldClose()) {
            // 1. 计算时间增量 (Delta Time)
            double currentTime = glfwGetTime();
            float deltaTime = (float) (currentTime - lastFrameTime);
            lastFrameTime = currentTime;

            // 2. 更新模型动画
            model.update(deltaTime);

            // 3. 更新摄像机矩阵
            float camX = (float) (Math.cos(pitch) * Math.sin(yaw) * distance);
            float camY = (float) (Math.sin(pitch) * distance);
            float camZ = (float) (Math.cos(pitch) * Math.cos(yaw) * distance);
            camera.getTransform().setPosition(target.x + camX, target.y + camY, target.z + camZ);
            camera.lookAt(target, new Vector3f(0, 1, 0));

            // 4. 渲染
            glClearColor(0.2f, 0.3f, 0.4f, 1.0f);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            Matrix4f viewProj = new Matrix4f(camera.getProjectionMatrix()).mul(camera.getViewMatrix());

            shader.bind();
            shader.clearLights();
            shader.addLight(sunLight);
            shader.applyLights(camera.getTransform().getPosition());
            shader.setup(viewProj, modelTransform.getModelMatrix(), Color.WHITE);

            model.draw();

            window.update();
        }

        model.dispose();
        window.dispose();
    }
}