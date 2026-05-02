package com.zenith.render.backend.opengl.test;

import com.zenith.common.math.Color;
import com.zenith.render.Font;
import com.zenith.render.Window;
import com.zenith.render.backend.opengl.*;
import com.zenith.render.backend.opengl.shader.StandardShader;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

public class Test {
    // 交互状态
    private static float yaw = 0.0f;
    private static float pitch = 0.5f;
    private static double lastX, lastY;
    private static boolean isLeftMouseDown = false;
    private static boolean isMiddleMouseDown = false;
    private static float distance = 20.0f;

    // 观察的目标点，初始为原点
    private static Vector3f target = new Vector3f(0, 0, 0);

    public static void main(String[] args) throws Exception {
        GLWindow window = new GLWindow("Zenith Engine - Mouse Orbit & Pan", 1280, 720);
        window.init();

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
                }
                else if (isMiddleMouseDown) {
                    float panSpeed = distance * 0.001f;
                    Vector3f right = new Vector3f((float)Math.cos(yaw), 0, (float)-Math.sin(yaw));
                    Vector3f up = new Vector3f(0, 1, 0);
                    target.add(right.mul(-dx * panSpeed));
                    target.add(up.mul(dy * panSpeed));
                }

                lastX = xpos; lastY = ypos;
            }

            @Override
            public void onScroll(double xoffset, double yoffset) {
                distance -= (float) yoffset * 2.0f;
                distance = Math.max(2.0f, Math.min(100.0f, distance));
            }

            @Override public void onKey(int key, int scancode, int action, int mods) {}

            @Override
            public void onChar(int codepoint) {

            }

            @Override public void onResize(int width, int height) {}
        });

        StandardShader shader = new StandardShader();
        GLTextRenderer textRenderer = new GLTextRenderer(shader);
        Font font = new GLFont("E:\\AuraDesk\\Zenith\\src\\main\\resources\\font\\HarmonyOS_Sans_SC_Regular.ttf", 64);

        glEnable(GL_DEPTH_TEST);
        GLLight ambientLight = new GLLight(
                new Vector3f(0, 0, 0),         // 位置对环境光无意义
                new Color(0.2f, 0.2f, 0.25f, 1.0f), // 较暗的蓝灰色环境光
                1.0f                          // 强度
        );
        ambientLight.setAmbient(true);        // 【重要】：假设你的 GLLight 类有 setAmbient(boolean) 方法
        // --- 核心修改 1：启用 Alpha 混合，防止文字出现黑框 ---
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        // ------------------------------------------------

        GLCamera camera = new GLCamera();
        camera.getProjection().setPerspective(true);
        camera.getProjection().updateSize(1280, 720);

        GLLight flashlight = new GLLight(
                new Vector3f(0, 0, 0),
                new Color(1.0f, 1.0f, 1.0f, 1.0f),
                10000000000.0f
        );

        while (!window.shouldClose()) {
            glClearColor(0.05f, 0.05f, 0.05f, 1.0f);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            // --- 1. 更新相机与灯光位置 ---
            float camX = (float) (Math.cos(pitch) * Math.sin(yaw) * distance);
            float camY = (float) (Math.sin(pitch) * distance);
            float camZ = (float) (Math.cos(pitch) * Math.cos(yaw) * distance);

            camera.getTransform().setPosition(target.x + camX, target.y + camY, target.z + camZ);
            camera.lookAt(target, new Vector3f(0, 1, 0));

            flashlight.update();

            // --- 2. 渲染 3D 场景阶段 ---
            textRenderer.begin();
            shader.setUseTexture(true);
            shader.setUniform("u_TextColor", Color.WHITE);
            shader.clearLights();
            shader.addLight(ambientLight);
            shader.addLight(flashlight);
            shader.applyLights(camera.getTransform().getPosition());
            shader.setUniform("u_ViewPos", camera.getTransform().getPosition());
            textRenderer.drawString3D("Origin (0,0,0)", new Vector3f(0, 0, 0),
                    new Quaternionf(), camera, font, Color.RED, 0.5f);
            textRenderer.end();
            textRenderer.begin();
            shader.setUseTexture(true);
            Matrix4f uiProj = new Matrix4f().ortho(0, 1280, 720, 0, -1, 1);
            shader.setup(uiProj, new Matrix4f(), Color.WHITE);
            shader.setUseTexture(true);
            textRenderer.drawString("Left: Orbit | Middle: Pan Target", 20, 20, font, Color.WHITE);
            textRenderer.end();

            window.update();
        }
        window.dispose();
    }
}