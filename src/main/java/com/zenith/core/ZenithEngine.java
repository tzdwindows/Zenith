package com.zenith.core;

import com.zenith.render.Camera;
import com.zenith.render.Renderer;
import com.zenith.render.VertexLayout;
import com.zenith.render.Window;
import com.zenith.render.backend.opengl.*;
import com.zenith.render.backend.opengl.buffer.GLBufferBuilder;
import com.zenith.render.backend.opengl.shader.UIShader;
import com.zenith.ui.DebugInfoScreen;
import com.zenith.ui.UIScreen;
import com.zenith.ui.render.UIRenderContext;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL11C.glViewport;
import static org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30C.glBindFramebuffer;

/**
 * 增强型引擎抽象类：支持 3D 渲染与 UI 系统
 */
public abstract class ZenithEngine implements Window.WindowEventListener {
    protected final Window window;
    protected Renderer renderer;
    protected final GLCamera camera;

    private boolean running;
    private float lastFrameTime;
    protected final boolean[] keys = new boolean[1024];
    protected final boolean[] mouseButtons = new boolean[8];
    private float lastMouseX = 640.0f, lastMouseY = 360.0f;
    private boolean firstMouse = true;
    protected final FrustumIntersection frustumIntersection = new FrustumIntersection();
    private final Matrix4f viewProjMatrix = new Matrix4f();
    protected UIRenderContext uiContext;
    protected final List<UIScreen> screens = new ArrayList<>();
    private DebugInfoScreen debugOverlay;
    private boolean showDebug = true;
    protected boolean isCursorLocked = true;
    protected float cameraYaw = -90.0f;
    protected float cameraPitch = 0.0f;
    protected float cameraSpeed = 20.0f;
    protected float sprintMultiplier = 4.0f;
    protected float mouseSensitivity = 0.12f;

    private final Vector3f tempPos = new Vector3f();
    private final Vector3f tempFront = new Vector3f();
    private final Vector3f tempRight = new Vector3f();
    private final Vector3f worldUp = new Vector3f(0, 1, 0);
    protected SceneFramebuffer sceneFBO;
    public ZenithEngine(Window window) {
        this.window = window;
        this.camera = new GLCamera();
        this.window.setEventListener(this);
    }

    public void start() {
        if (running) return;
        if (window instanceof GLWindow glWindow) {
            glWindow.init();
            setCursorMode(true);
            glfwSwapInterval(1);
        }
        this.renderer = new GLRenderer();
        initUIContext();
        this.debugOverlay = new DebugInfoScreen(this);
        int[] w = new int[1], h = new int[1];
        glfwGetFramebufferSize(((GLWindow)window).getHandle(), w, h);
        sceneFBO = new SceneFramebuffer(w[0], h[0]);
        glViewport(0, 0, w[0], h[0]);
        this.camera.getProjection().updateSize(w[0], h[0]);
        running = true;
        lastFrameTime = (float) glfwGetTime();
        loop();
    }

    private void initUIContext() {
        GLBufferBuilder bufferBuilder = new GLBufferBuilder(1024 * 64);
        VertexLayout uiLayout = new VertexLayout();
        uiLayout.pushFloat("aPos", 2);
        uiLayout.pushFloat("aTexCoords", 2);
        uiLayout.pushFloat("aColor", 4);
        UIShader uiShader = new UIShader();
        GLMaterial uiMaterial = new GLMaterial(uiShader);
        this.uiContext = new UIRenderContext(renderer, bufferBuilder, uiMaterial, uiLayout);
    }

    protected void setCursorMode(boolean lock) {
        this.isCursorLocked = lock;
        long handle = ((GLWindow)window).getHandle();
        glfwSetInputMode(handle, GLFW_CURSOR, lock ? GLFW_CURSOR_DISABLED : GLFW_CURSOR_NORMAL);
        this.firstMouse = true;
    }

    private void loop() {
        long handle = (window).getHandle();
        while (running && !window.shouldClose()) {
            glfwPollEvents();
            if (window.getWidth() <= 0 || window.getHeight() <= 0) {
                try { Thread.sleep(50); } catch (InterruptedException ignored) {}
                continue;
            }
            boolean isFocused = glfwGetWindowAttrib(handle, GLFW_FOCUSED) == GLFW_TRUE;
            if (!isFocused) {
                try {
                    Thread.sleep(60);
                } catch (InterruptedException ignored) {}
            }
            float currentTime = (float) glfwGetTime();
            float deltaTime = currentTime - lastFrameTime;
            lastFrameTime = currentTime;
            if (deltaTime > 0.1f) deltaTime = 0.1f;
            if (isCursorLocked && isFocused) {
                updateCameraInput(deltaTime);
            }
            for (UIScreen screen : screens) {
                if (screen.isVisible()) {
                    screen.update(deltaTime);
                }
            }
            update(deltaTime);
            viewProjMatrix.set(camera.getProjection().getMatrix()).mul(camera.getViewMatrix());
            frustumIntersection.set(viewProjMatrix);
            sceneFBO.bind();
            glViewport(0, 0, window.getWidth(), window.getHeight());
            glClearColor(0.1f, 0.1f, 0.1f, 1.0f);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            renderScene();
            sceneFBO.copyToHistory();
            renderAfterOpaqueScene();
            sceneFBO.unbind();
            glViewport(0, 0, window.getWidth(), window.getHeight());
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            sceneFBO.renderToScreen();
            renderUI(deltaTime);
            window.update();
        }
        cleanup();
    }

    /**
     * 处理相机的键盘输入逻辑（平移、升降、冲刺）
     */
    private void updateCameraInput(float deltaTime) {
        Vector3f pos = camera.getTransform().getPosition();
        float currentSpeed = (keys[GLFW_KEY_LEFT_CONTROL] || keys[GLFW_KEY_RIGHT_CONTROL])
                ? cameraSpeed * sprintMultiplier : cameraSpeed;
        float moveStep = currentSpeed * deltaTime;
        tempFront.set(
                (float) (Math.cos(Math.toRadians(cameraYaw)) * Math.cos(Math.toRadians(cameraPitch))),
                (float) Math.sin(Math.toRadians(cameraPitch)),
                (float) (Math.sin(Math.toRadians(cameraYaw)) * Math.cos(Math.toRadians(cameraPitch)))
        ).normalize();
        tempFront.cross(worldUp, tempRight).normalize();
        if (keys[GLFW_KEY_W]) pos.add(new Vector3f(tempFront).mul(moveStep));
        if (keys[GLFW_KEY_S]) pos.sub(new Vector3f(tempFront).mul(moveStep));
        if (keys[GLFW_KEY_A]) pos.sub(new Vector3f(tempRight).mul(moveStep));
        if (keys[GLFW_KEY_D]) pos.add(new Vector3f(tempRight).mul(moveStep));
        if (keys[GLFW_KEY_SPACE]) pos.y += moveStep;
        if (keys[GLFW_KEY_LEFT_SHIFT]) pos.y -= moveStep;
        tempPos.set(pos).add(tempFront);
        camera.lookAt(tempPos, worldUp);
    }

    private void renderUI(float deltaTime) {
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);

        uiContext.begin(window.getWidth(), window.getHeight());

        for (UIScreen screen : screens) {
            if (screen.isVisible()) {
                screen.render(uiContext);
            }
        }

        if (debugOverlay != null && showDebug) {
            debugOverlay.update(deltaTime);
            debugOverlay.render(uiContext);
        }

        uiContext.end();
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glDepthMask(true);
    }

    @Override
    public void onKey(int key, int scancode, int action, int mods) {
        if (key >= 0 && key < keys.length) keys[key] = (action != GLFW_RELEASE);

        if (action == GLFW_PRESS) {
            switch (key) {
                case GLFW_KEY_F11:
                    showDebug = !showDebug;
                    break;
                case GLFW_KEY_ESCAPE:
                    setCursorMode(!isCursorLocked);
                    break;
                case GLFW_KEY_END:
                    break;
            }
        }
    }

    @Override
    public void onCursorPos(double xpos, double ypos) {
        if (isCursorLocked) {
            if (firstMouse) {
                lastMouseX = (float) xpos;
                lastMouseY = (float) ypos;
                firstMouse = false;
                return;
            }
            float dx = (float) xpos - lastMouseX;
            float dy = lastMouseY - (float) ypos;
            lastMouseX = (float) xpos;
            lastMouseY = (float) ypos;

            cameraYaw += dx * mouseSensitivity;
            cameraPitch += dy * mouseSensitivity;

            if (cameraPitch > 89.0f) cameraPitch = 89.0f;
            if (cameraPitch < -89.0f) cameraPitch = -89.0f;
        } else {
            for (int i = screens.size() - 1; i >= 0; i--) {
                UIScreen screen = screens.get(i);
                if (screen.isVisible()) {
                    screen.onMouseMove((float)xpos, (float)ypos);
                }
            }
        }
    }

    @Override
    public void onMouseButton(int button, int action, int mods) {
        if (button >= 0 && button < 8) mouseButtons[button] = (action != GLFW_RELEASE);

        if (!isCursorLocked) {
            double[] x = new double[1], y = new double[1];
            glfwGetCursorPos(((GLWindow)window).getHandle(), x, y);
            float mx = (float)x[0];
            float my = (float)y[0];
            for (int i = screens.size() - 1; i >= 0; i--) {
                UIScreen screen = screens.get(i);
                if (screen.isVisible()) {
                    if (screen.onMouseButton(action, mx, my)) {
                        return;
                    }
                }
            }
        }
    }

    @Override
    public void onScroll(double x, double y) {
        if (isCursorLocked) {
            camera.getTransform().getPosition().y += (float)y * 2.0f;
        }
    }

    @Override
    public void onResize(int w, int h) {
        glViewport(0, 0, w, h);
        sceneFBO.resize(w, h);
        camera.getProjection().updateSize(w, h);
    }

    public Renderer getRenderer() { return renderer; }
    public Camera getCamera() { return camera; }

    protected abstract void update(float deltaTime);
    protected abstract void renderScene();
    protected abstract void renderAfterOpaqueScene();

    private void cleanup() {
        if (uiContext != null) uiContext.dispose();
        window.dispose();
    }

    public float getYaw() { return cameraYaw; }
    public float getPitch() { return cameraPitch; }

    /**
     * 清空当前所有屏幕并设置一个新的主屏幕
     */
    protected void setUIScreen(UIScreen screen) {
        this.screens.clear();
        if (screen != null) {
            this.screens.add(screen);
        }
    }

    /**
     * 在当前屏幕之上添加一个覆盖层
     */
    protected void pushOverlay(UIScreen screen) {
        if (screen != null && !screens.contains(screen)) {
            this.screens.add(screen);
        }
    }

    public SceneFramebuffer getSceneFBO() {
        return sceneFBO;
    }

    public Window getWindow() {
        return window;
    }

    /**
     * 移除指定的覆盖层
     */
    protected void popOverlay(UIScreen screen) {
        this.screens.remove(screen);
    }
    protected boolean isInsideFrustum(Vector3f min, Vector3f max) { return frustumIntersection.testAab(min, max); }
    protected boolean isInsideFrustum(Vector3f center, float radius) { return frustumIntersection.testSphere(center, radius); }
}