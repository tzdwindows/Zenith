package com.zenith.core;

import com.zenith.render.Camera;
import com.zenith.render.Renderer;
import com.zenith.render.VertexLayout;
import com.zenith.render.Window;
import com.zenith.render.backend.opengl.*;
import com.zenith.render.backend.opengl.buffer.GLBufferBuilder;
import com.zenith.render.backend.opengl.shader.LoadingScreenShader;
import com.zenith.render.backend.opengl.shader.ScreenShader;
import com.zenith.render.backend.opengl.shader.UIShader;
import com.zenith.ui.DebugInfoScreen;
import com.zenith.ui.EscInfoScreen;
import com.zenith.ui.UIScreen;
import com.zenith.ui.render.UIRenderContext;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL11C.glViewport;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * 增强型引擎抽象类：支持 3D 渲染、UI 系统与 高级异步加载画面
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
    private EscInfoScreen escScreen;
    private boolean showDebug = true;
    protected boolean isCursorLocked = false;
    protected float cameraYaw = -90.0f;
    protected float cameraPitch = 0.0f;
    protected float cameraSpeed = 20.0f;
    protected float sprintMultiplier = 4.0f;
    protected float mouseSensitivity = 0.12f;
    private final Vector3f tempPos = new Vector3f();
    private final Vector3f tempFront = new Vector3f();
    private final Vector3f tempRight = new Vector3f();
    private final Vector3f worldUp = new Vector3f(0, 1, 0);
    private boolean isRunning = true;
    protected SceneFramebuffer sceneFBO;

    // --- 异步加载系统相关变量 ---
    private volatile boolean isLoading = true;
    private volatile float loadingProgress = 0.0f;
    private final ConcurrentLinkedQueue<Runnable> mainThreadTasks = new ConcurrentLinkedQueue<>();
    private LoadingScreenShader loadingShader;
    private int loadingVAO, loadingVBO;

    public ZenithEngine(Window window) {
        this.window = window;
        this.camera = new GLCamera();
        this.window.setEventListener(this);
    }

    public void start() {
        if (running) return;

        if (window instanceof GLWindow glWindow) {
            glWindow.init();
            setCursorMode(false);

            // 加载阶段先关闭 vsync，减少卡顿感
            glfwSwapInterval(0);
        }

        this.renderer = new GLRenderer();
        initUIContext();
        initLoadingScreenShader();

        this.debugOverlay = new DebugInfoScreen(this);
        this.escScreen = new EscInfoScreen(this);
        this.escScreen.setVisible(false);

        int[] w = new int[1], h = new int[1];
        glfwGetFramebufferSize(((GLWindow)window).getHandle(), w, h);
        sceneFBO = new SceneFramebuffer(w[0], h[0]);
        glViewport(0, 0, w[0], h[0]);
        this.camera.getProjection().updateSize(w[0], h[0]);

        running = true;
        lastFrameTime = (float) glfwGetTime();

        startAsyncInitialization();
        loop();
    }

    /**
     * 将需要在主线程执行的 OpenGL 任务推入队列（供子线程调用）
     */
    public void runOnMainThread(Runnable task) {
        mainThreadTasks.offer(task);
    }

    /**
     * 更新加载进度条 (0.0 ~ 1.0)
     */
    public void setLoadingProgress(float progress) {
        this.loadingProgress = Math.max(0.0f, Math.min(1.0f, progress));
    }

    private void startAsyncInitialization() {
        Thread loadThread = new Thread(() -> {
            try {
                // 后台线程：只做 CPU / IO 密集型工作
                asyncLoad();

                // 主线程：只做 OpenGL 资源绑定等轻量操作
                runOnMainThread(() -> {
                    try {
                        init();
                        isLoading = false;
                        setCursorMode(true);

                        // 加载结束后再恢复 vsync
                        glfwSwapInterval(1);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, "Zenith-Async-Load-Thread");
        loadThread.setPriority(Thread.MIN_PRIORITY + 1);
        loadThread.start();
    }

    private void initLoadingScreenShader() {
        // 直接使用封装好的类
        loadingShader = new LoadingScreenShader();

        // 使用全屏大三角形代替 Quad (性能更好)
        float[] vertices = {
                -1.0f, -1.0f,
                3.0f, -1.0f,
                -1.0f,  3.0f
        };

        loadingVAO = glGenVertexArrays();
        loadingVBO = glGenBuffers();
        glBindVertexArray(loadingVAO);
        glBindBuffer(GL_ARRAY_BUFFER, loadingVBO);
        glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 2 * Float.BYTES, 0);
        glEnableVertexAttribArray(0);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
    }

    private void renderLoadingAnimation(float time) {
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_BLEND);
        glDisable(GL_CULL_FACE);

        int w = Math.max(1, window.getWidth());
        int h = Math.max(1, window.getHeight());
        glViewport(0, 0, w, h);

        glClearColor(0.02f, 0.02f, 0.05f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        if (loadingShader == null) return;

        loadingShader.bind();
        loadingShader.setTime(time);
        loadingShader.setResolution(w, h);
        loadingShader.setProgress(loadingProgress);

        glBindVertexArray(loadingVAO);
        glDrawArrays(GL_TRIANGLES, 0, 3);
        glBindVertexArray(0);
        glUseProgram(0);
    }

    // ==========================================================

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

    public void setCursorMode(boolean lock) {
        this.isCursorLocked = lock;
        long handle = ((GLWindow)window).getHandle();
        glfwSetInputMode(handle, GLFW_CURSOR, lock ? GLFW_CURSOR_DISABLED : GLFW_CURSOR_NORMAL);
        this.firstMouse = true;
    }

    private void loop() {
        long handle = window.getHandle();

        while (running && !window.shouldClose()) {
            glfwPollEvents();

            // 每帧处理少量主线程任务，避免队列堆积
            Runnable task;
            int tasksProcessed = 0;
            final int maxTasksPerFrame = 4;
            while ((task = mainThreadTasks.poll()) != null && tasksProcessed < maxTasksPerFrame) {
                try {
                    task.run();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                tasksProcessed++;
            }

            if (window.getWidth() <= 0 || window.getHeight() <= 0) {
                try { Thread.sleep(16); } catch (InterruptedException ignored) {}
                continue;
            }

            float currentTime = (float) glfwGetTime();
            float realDeltaTime = currentTime - lastFrameTime;
            lastFrameTime = currentTime;

            // 加载动画阶段：只渲染，不做别的重逻辑
            if (isLoading) {
                renderLoadingAnimation(currentTime);
                window.update();
                continue;
            }

            boolean isFocused = glfwGetWindowAttrib(handle, GLFW_FOCUSED) == GLFW_TRUE;
            if (!isFocused) {
                try { Thread.sleep(16); } catch (InterruptedException ignored) {}
            }

            if (realDeltaTime > 0.1f) realDeltaTime = 0.1f;
            float logicDeltaTime = isRunning ? realDeltaTime : 0.0f;

            if (isCursorLocked && isFocused) {
                updateCameraInput(logicDeltaTime);
            }

            for (UIScreen screen : screens) {
                if (screen.isVisible()) {
                    screen.update(realDeltaTime);
                }
            }

            update(logicDeltaTime);

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

            sceneFBO.ensureResources();
            onBufferToScreen(logicDeltaTime, sceneFBO.getScreenShader());
            sceneFBO.renderToScreen();
            renderUI(realDeltaTime);

            window.update();
        }

        cleanup();
    }

    public void setRunning(boolean running) {
        this.isRunning = running;
    }

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

    private void renderUI(float realDeltaTime) {
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);

        uiContext.begin(window.getWidth(), window.getHeight());

        for (UIScreen screen : screens) {
            if (screen.isVisible()) {
                screen.update(realDeltaTime);
                screen.render(uiContext);
            }
        }

        if (debugOverlay != null && showDebug) {
            debugOverlay.update(realDeltaTime);
            debugOverlay.render(uiContext);
        }

        if (escScreen != null && escScreen.isVisible()) {
            escScreen.update(realDeltaTime);
            escScreen.render(uiContext);
            setRunning(false);
        } else {
            setRunning(true);
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

        // 加载时禁用快捷键
        if (isLoading) return;

        if (action == GLFW_PRESS) {
            switch (key) {
                case GLFW_KEY_F11:
                    showDebug = !showDebug;
                    break;
                case GLFW_KEY_ESCAPE:
                    setCursorMode(!isCursorLocked);
                    if (escScreen != null) {
                        escScreen.setVisible(!isCursorLocked);
                    }
                    break;
            }
        }
    }

    @Override
    public void onCursorPos(double xpos, double ypos) {
        if (isLoading) return;

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
            float mx = (float) xpos;
            float my = (float) ypos;
            if (escScreen != null && escScreen.isVisible()) {
                escScreen.onMouseMove(mx, my);
            }
            for (int i = screens.size() - 1; i >= 0; i--) {
                UIScreen screen = screens.get(i);
                if (screen.isVisible()) {
                    screen.onMouseMove(mx, my);
                }
            }
        }
    }

    @Override
    public void onMouseButton(int button, int action, int mods) {
        if (button >= 0 && button < 8) mouseButtons[button] = (action != GLFW_RELEASE);

        if (isLoading) return;

        if (!isCursorLocked) {
            double[] x = new double[1], y = new double[1];
            glfwGetCursorPos(((GLWindow)window).getHandle(), x, y);
            float mx = (float)x[0];
            float my = (float)y[0];

            if (escScreen != null && escScreen.isVisible()) {
                if (escScreen.onMouseButton(action, mx, my)) {
                    return;
                }
            }

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
        if (isLoading) return;
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

    /**
     * 【新增】异步数据加载。
     * 此方法在单独的后台线程运行。适合做文件读取、模型解析、地形计算等 CPU 密集型操作。
     */
    protected void asyncLoad() { }

    /**
     * 当渲染器初始化时 (在主线程运行，适合绑定 OpenGL 资源如 VAO、Texture)
     */
    protected void init() { }

    protected abstract void update(float deltaTime);

    protected void onBufferToScreen(float realDeltaTime, ScreenShader screenShader) { }

    protected abstract void renderScene();

    protected abstract void renderAfterOpaqueScene();

    private void cleanup() {
        if (uiContext != null) uiContext.dispose();
        // 清理加载着色器
        loadingShader.dispose();
        glDeleteVertexArrays(loadingVAO);
        glDeleteBuffers(loadingVBO);
        window.dispose();
    }

    public float getYaw() { return cameraYaw; }
    public float getPitch() { return cameraPitch; }

    protected void setUIScreen(UIScreen screen) {
        this.screens.clear();
        if (screen != null) {
            this.screens.add(screen);
        }
    }

    protected void pushOverlay(UIScreen screen) {
        if (screen != null && !screens.contains(screen)) {
            this.screens.add(screen);
        }
    }

    public SceneFramebuffer getSceneFBO() { return sceneFBO; }
    public Window getWindow() { return window; }
    protected void popOverlay(UIScreen screen) { this.screens.remove(screen); }
    protected boolean isInsideFrustum(Vector3f min, Vector3f max) { return frustumIntersection.testAab(min, max); }
    protected boolean isInsideFrustum(Vector3f center, float radius) { return frustumIntersection.testSphere(center, radius); }
}