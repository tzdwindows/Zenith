package com.zenith.animation.test;

import com.zenith.animation.data.AnimatedModel;
import com.zenith.animation.io.AssimpModelLoader;
import com.zenith.animation.runtime.AnimationClip;
import com.zenith.animation.runtime.Animator;
import com.zenith.common.math.Color;
import com.zenith.common.math.Transform;
import com.zenith.common.utils.InternalLogger;
import com.zenith.render.Window;
import com.zenith.render.backend.opengl.GLCamera;
import com.zenith.render.backend.opengl.GLWindow;
import com.zenith.render.backend.opengl.shader.AnimationShader;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*; // 确保引入 GL13

public class Test {
    private static float yaw = 0.0f;
    private static float pitch = 0.5f;
    private static double lastX, lastY;
    private static boolean isLeftMouseDown = false;
    private static boolean isMiddleMouseDown = false;
    private static float distance = 5.0f;
    private static Vector3f target = new Vector3f(0, 1, 0);
    private static double lastFrameTime = 0.0;

    public static void main(String[] args) {
        InternalLogger.info("Starting Zenith Skeletal Animation Test...");

        GLWindow window = new GLWindow("Zenith Engine - Skeletal Animation Player", 1280, 720);
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
                    target.add(new Vector3f((float) Math.cos(yaw), 0, (float) -Math.sin(yaw)).mul(-dx * panSpeed));
                    target.add(new Vector3f(0, 1, 0).mul(dy * panSpeed));
                }
                lastX = xpos; lastY = ypos;
            }

            @Override
            public void onScroll(double xoffset, double yoffset) {
                distance -= (float) yoffset * 0.5f;
                distance = Math.max(0.1f, distance);
            }

            @Override public void onKey(int key, int scancode, int action, int mods) {}
            @Override public void onResize(int width, int height) {
                glViewport(0, 0, width, height);
                camera.getProjection().updateSize(width, height);
            }
        });

        AnimationShader shader = new AnimationShader();
        glEnable(GL_DEPTH_TEST);

        // 4. 加载资源
        String modelPath = "F:\\glTF-Sample-Models-main\\2.0\\SimpleSkin\\glTF\\SimpleSkin.gltf";
        AnimatedModel animatedModel = AssimpModelLoader.load(modelPath);

        // --- 修改 1：使用新的构造函数，自动同步贴图 ---
        Animator animator = new Animator(animatedModel);

        if (!animatedModel.getAllAnimations().isEmpty()) {
            String firstAnimName = animatedModel.getAllAnimations().keySet().iterator().next();
            AnimationClip clip = animatedModel.getAnimation(firstAnimName);
            InternalLogger.info("Now playing loop animation: " + firstAnimName);
            animator.play(clip);
            animator.setLooping(true);
        }

        Transform modelTransform = new Transform();
        lastFrameTime = glfwGetTime();
        while (!window.shouldClose()) {
            double currentTime = glfwGetTime();
            float deltaTime = (float) (currentTime - lastFrameTime);
            lastFrameTime = currentTime;
            animator.update(deltaTime);
            float camX = (float) (Math.cos(pitch) * Math.sin(yaw) * distance);
            float camY = (float) (Math.sin(pitch) * distance);
            float camZ = (float) (Math.cos(pitch) * Math.cos(yaw) * distance);
            camera.getTransform().setPosition(target.x + camX, target.y + camY, target.z + camZ);
            camera.lookAt(target, new Vector3f(0, 1, 0));

            glClearColor(0.15f, 0.15f, 0.18f, 1.0f);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            Matrix4f viewProj = new Matrix4f(camera.getProjectionMatrix()).mul(camera.getViewMatrix());
            shader.bind();
            animator.bind(0);
            shader.setup(viewProj, modelTransform.getModelMatrix(), Color.WHITE);
            animatedModel.getMesh().render();

            window.update();
        }

        animatedModel.dispose();
        animator.dispose(); // 别忘了释放 animator 的内存
        window.dispose();
    }
}