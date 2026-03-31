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
                asyncLoad();
                runOnMainThread(() -> {
                    try {
                        init();
                        isLoading = false;
                        setCursorMode(true);
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
        loadingShader = new LoadingScreenShader();
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
     * <h3>异步数据加载回调 (非主线程)</h3>
     * * <p>该方法在独立的后台线程 {@code Zenith-Async-Load-Thread} 中执行。
     * 适合进行以下操作：</p>
     * <ul>
     * <li>硬盘 I/O：读取配置文件、加载大型模型文件、解析 JSON/二进制数据。</li>
     * <li>计算密集型任务：地形网格生成、寻路算法预计算、纹理像素处理。</li>
     * <li>网络请求：下载必要的资源包。</li>
     * </ul>
     * * <p><b>注意：</b> 由于 OpenGL 不是线程安全的，你<b>不能</b>在此方法中直接调用任何
     * {@code glGenXXX} 或 {@code glBindTexture} 等绘图 API。
     * 如果需要生成 OpenGL 对象，请使用 {@link #runOnMainThread(Runnable)}。</p>
     */
    protected void asyncLoad() { }

    /**
     * <h3>同步初始化回调 (主线程)</h3>
     * * <p>该方法在 {@link #asyncLoad()} 完成后，在主线程（渲染线程）中被调用。
     * 此时加载画面即将关闭，标志着引擎正式进入游戏状态。</p>
     * * <p>适合进行以下操作：</p>
     * <ul>
     * <li>将 {@code asyncLoad} 准备好的原始数据上传至 GPU (生成 VAO, VBO, EBO)。</li>
     * <li>初始化着色器变量 Uniforms。</li>
     * <li>绑定最终的纹理对象。</li>
     * </ul>
     */
    protected void init() { }

    /**
     * <h3>逻辑更新 (每帧执行)</h3>
     * * @param deltaTime 从上一帧到当前帧的时间间隔（秒）。
     * <br>当引擎暂停（例如打开 ESC 菜单）时，若 {@code isRunning} 为 false，则此值为 0.0。
     * * <p>实现类应在此处理：实体位移、物理碰撞检测、AI 逻辑、计时器更新等。
     * 建议所有位移计算都乘以 {@code deltaTime} 以确保帧率无关性。</p>
     */
    protected abstract void update(float deltaTime);

    /**
     * <h3>全屏后期处理回调</h3>
     * * @param realDeltaTime 真实的物理时间间隔（不受暂停影响）。
     * @param screenShader 当前场景用于渲染到屏幕的 {@link ScreenShader} 实例。
     * * <p>此方法在场景 FBO 渲染完成后、正式绘制到屏幕前触发。
     * 你可以在此处设置后期处理着色器的参数，例如：</p>
     * <ul>
     * <li>调整曝光度、对比度或 Gamma 值。</li>
     * <li>传递动态模糊所需的运动矢量。</li>
     * <li>应用复古滤镜的时间偏移量。</li>
     * </ul>
     */
    protected void onBufferToScreen(float realDeltaTime, ScreenShader screenShader) { }

    /**
     * <h3>主场景渲染 (不透明物体)</h3>
     * * <p>该方法在 {@code sceneFBO} 绑定期间被调用。
     * 实现类应在此渲染所有的 3D 几何体（如地形、玩家、建筑物）。</p>
     * * <p><b>渲染顺序建议：</b> 先绘制离相机近的物体，以利用深度测试（Early-Z）优化性能。</p>
     */
    protected abstract void renderScene();

    /**
     * <h3>场景渲染扩展 (半透明/装饰物体)</h3>
     * * <p>该方法在 {@link #renderScene()} 之后、FBO 解绑前执行。</p>
     * * <p>适合渲染：</p>
     * <ul>
     * <li>具有 Alpha 混合的半透明物体（如玻璃、水面）。</li>
     * <li>粒子系统（火焰、烟雾）。</li>
     * <li>调试线框或 3D 辅助线。</li>
     * </ul>
     */
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