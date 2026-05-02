package com.zenith.core.test;

import com.zenith.common.utils.InternalLogger;
import com.zenith.common.utils.ZenithStackTraceInterceptor;
import com.zenith.core.ZenithEngine;
import com.zenith.render.Window;
import com.zenith.render.backend.opengl.GLWindow;
import com.zenith.ui.UIScreen;
import com.zenith.ui.component.HTMLComponent;
import me.friwi.jcefmaven.CefAppBuilder;
import org.cef.CefApp;
import org.cef.CefClient;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.callback.CefContextMenuParams;
import org.cef.callback.CefMenuModel;
import org.cef.handler.CefContextMenuHandlerAdapter;
import org.lwjgl.opengl.GL11;

import java.awt.*;

/**
 * ZenithEngine 浏览器组件集成测试
 */
public class BrowserEngineTest  extends ZenithEngine {

    private static CefApp cefApp;
    private static CefClient cefClient;

    private HTMLComponent browserComponent;
    private UIScreen mainScreen;

    public BrowserEngineTest(Window window, CefClient client) {
        super(window);
        BrowserEngineTest.cefClient = client;
    }

    /**
     * 在 asyncLoad (异步加载) 完成后，在主线程同步调用此方法。
     * 适合在这里初始化 UI 和 OpenGL 相关的组件。
     */
    @Override
    protected void init() {
        super.init();

        // 1. 创建一个新的 UI 屏幕
        mainScreen = new UIScreen() {
            @Override
            public boolean isModal() {
                return true; // 拦截底层事件
            }
        };

        // 2. 创建 HTML 组件
        // 放置在屏幕中间 (1280x720 窗口，大小 1000x600，居中坐标大约为 140, 60)
        browserComponent = new HTMLComponent(
                cefClient,
                "https://www.bilibili.com", // 测试 B站，验证视频渲染、滚动和输入框
                140, 60, 1000, 600
        );

        // 3. 将组件添加到屏幕，并将屏幕注册到引擎
        mainScreen.addComponent(browserComponent);
        setUIScreen(mainScreen);

        // 4. 确保鼠标处于显示状态（非锁定视角模式），以便我们操作网页
        setCursorMode(false);
    }

    /**
     * 引擎每帧逻辑更新
     */
    @Override
    protected void update(float deltaTime) {
        // 由于 ZenithEngine 会自动调用 setUIScreen 传入的 screen 的 update()
        // 所以这里不需要手动调用 browserComponent.update()，引擎已经代劳了。

        // 如果想按 ESC 退出网页全屏或释放鼠标，可以在这里写逻辑
        setCursorMode(false);
    }

    /**
     * 渲染 3D 场景 (不透明物体)
     */
    @Override
    protected void renderScene() {
        // 我们在网页后面画一个深蓝色的纯色 3D 背景，证明网页是悬浮在 UI 层的
        GL11.glClearColor(0.1f, 0.2f, 0.3f, 1.0f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
    }

    /**
     * 渲染 3D 场景 (半透明物体/后期)
     */
    @Override
    protected void renderAfterOpaqueScene() {
        // 留空即可
    }

    public static void main(String[] args) {
        System.setProperty("sun.awt.exception.handler", "com.zenith.common.utils.AWTDebugHandler");
        EventQueue queue = Toolkit.getDefaultToolkit().getSystemEventQueue();
        queue.push(new EventQueue() {
            @Override
            protected void dispatchEvent(AWTEvent event) {
                try {
                    super.dispatchEvent(event);
                } catch (Throwable t) {
                    System.err.println("--- 捕获到 AWT 内部异常 ---");
                    t.printStackTrace();
                    // 这里可以打断点
                }
            }
        });
        ZenithStackTraceInterceptor.install();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            String threadName = thread.getName();
            if (threadName.contains("AWT-EventQueue-0")) {
                InternalLogger.error("捕获到 AWT 线程异常 [" + threadName + "]: " + throwable.getMessage(), throwable);
            } else {
                InternalLogger.error("线程 " + threadName + " 发生未捕获异常", throwable);
            }
        });
        CefAppBuilder builder = new CefAppBuilder();

        // 关键 CEF 参数配置
        builder.addJcefArgs(
                "--disable-gpu",
                "--disable-gpu-compositing",
                "--no-sandbox",
                "--off-screen-frame-rate=60",      // 离屏渲染帧率
                "--force-device-scale-factor=1",    // 防止 Windows DPI 缩放导致像素错位崩溃

                "--enable-logging",
                "--v=1"
        );
        builder.getCefSettings().windowless_rendering_enabled = true; // 开启离屏渲染(OSR)

        try {
            System.out.println("正在初始化 CEF 内核，首次运行可能需要下载...");
            cefApp = builder.build();
        } catch (Exception e) {
            System.err.println("JCEF 初始化失败！");
            e.printStackTrace();
            return;
        }

        // 创建全局唯一的 Client
        CefClient client = cefApp.createClient();

        client.addContextMenuHandler(new CefContextMenuHandlerAdapter() {
            @Override
            public void onBeforeContextMenu(CefBrowser browser, CefFrame frame, CefContextMenuParams params, CefMenuModel model) {
                model.clear();
            }
        });

        // ==========================================
        // 2. 创建 ZenithEngine 窗口和引擎实例
        // ==========================================
        GLWindow window = new GLWindow("ZenithEngine - CEF Browser Test", 1280, 720);
        BrowserEngineTest engine = new BrowserEngineTest(window, client);

        // ==========================================
        // 3. 启动引擎 (此处会阻塞，直到关闭窗口)
        // ==========================================
        engine.start();

        // ==========================================
        // 4. 资源清理 (窗口关闭后执行)
        // ==========================================
        if (engine.browserComponent != null) {
            engine.browserComponent.dispose();
        }
        cefApp.dispose();
        System.exit(0);
    }
}