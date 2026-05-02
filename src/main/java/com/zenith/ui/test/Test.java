package com.zenith.ui.test;

import com.zenith.asset.AssetIdentifier;
import com.zenith.asset.AssetResource;
import com.zenith.common.math.Color;
import com.zenith.render.VertexLayout;
import com.zenith.render.Window;
import com.zenith.render.backend.opengl.GLRenderer;
import com.zenith.render.backend.opengl.GLWindow;
import com.zenith.render.backend.opengl.buffer.GLBufferBuilder;
import com.zenith.render.backend.opengl.GLMaterial;
import com.zenith.render.backend.opengl.texture.GLTexture;
import com.zenith.ui.component.UIButton;
import com.zenith.ui.component.HTMLComponent;
import com.zenith.ui.render.TextureAtlas;
import com.zenith.ui.render.UIRenderContext;
import com.zenith.render.backend.opengl.shader.UIShader;
import me.friwi.jcefmaven.CefAppBuilder;
import org.cef.CefApp;
import org.cef.CefClient;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import java.io.IOException;

import static com.zenith.render.backend.opengl.test.Test2.loadFromResources;

public class Test {
    private static UIButton testButton;
    private static HTMLComponent htmlPanel;
    private static CefApp cefApp;
    private static CefClient cefClient;

    public static void main(String[] args) throws IOException {
        // --- 1. 使用 Builder 全自动下载并初始化 JCEF ---
        CefAppBuilder builder = new CefAppBuilder();

        builder.addJcefArgs(
                "--disable-gpu",
                "--disable-gpu-compositing",
                "--no-sandbox",
                "--off-screen-frame-rate=60",
                // 关键参数：强制 DPI 缩放为 1，防止 Windows 缩放导致 OpenGL 纹理尺寸错位崩溃
                "--force-device-scale-factor=1"
        );

        // 设置离屏渲染开启
        builder.getCefSettings().windowless_rendering_enabled = true;
        try {
            // build() 会完成解压、加载 DLL 和初始化 CefApp 的所有工作
            cefApp = builder.build();
        } catch (Exception e) {
            System.err.println("JCEF 初始化失败，请检查网络或原生库路径");
            e.printStackTrace();
            return;
        }

        // --- 2. 创建 Client (全局唯一) ---
        cefClient = cefApp.createClient();

        // --- 3. 初始化窗口 ---
        GLWindow window = new GLWindow("Zenith UI Test - HTML & Button", 1280, 720);
        window.init();

        // --- 4. 初始化渲染环境 ---
        GLRenderer renderer = new GLRenderer();
        GLBufferBuilder bufferBuilder = new GLBufferBuilder(1024 * 512);
        VertexLayout uiLayout = new VertexLayout();
        uiLayout.pushFloat("aPos", 2);
        uiLayout.pushFloat("aTexCoords", 2);
        uiLayout.pushFloat("aColor", 4);

        UIShader uiShader = new UIShader();
        GLMaterial uiMaterial = new GLMaterial(uiShader);
        UIRenderContext uiContext = new UIRenderContext(renderer, bufferBuilder, uiMaterial, uiLayout);

        // --- 5. 加载按钮资源 ---
        AssetIdentifier id = new AssetIdentifier("zenith", "textures/kenney_game-icons/sheet_white2x.png");
        AssetResource res = loadFromResources(id);
        GLTexture buttonTexture = new GLTexture(res);
        AssetResource xmlRes = AssetResource.loadFromResources("textures/kenney_game-icons/sheet_white2x.xml");
        TextureAtlas atlas = new TextureAtlas(buttonTexture, xmlRes);

        testButton = new UIButton(460, 320, 360, 80, atlas, "buttonA.png")
                .setPressedScale(0.85f)
                .setOnClick(() -> System.out.println("Button Click!"))
                .setColors(Color.WHITE, Color.LIGHT_GRAY, Color.GREEN);

        // --- 6. 初始化 HTMLComponent (传入已经创建好的 cefClient) ---
        // 确保你的 HTMLComponent 构造函数接受 CefClient 参数
        htmlPanel = new HTMLComponent(cefClient, "https://www.google.com/", 20, 20, 800, 450);

        // --- 7. 事件监听 ---
        window.setEventListener(new Window.WindowEventListener() {
            @Override
            public void onCursorPos(double xpos, double ypos) {
                // 按钮交互
                float bx = (float) xpos - testButton.getBounds().x;
                float by = (float) ypos - testButton.getBounds().y;
                testButton.onMouseMove(bx, by);

                // HTML 组件交互 (局部坐标)
                float hx = (float) xpos - htmlPanel.getBounds().x;
                float hy = (float) ypos - htmlPanel.getBounds().y;
                if (htmlPanel.getBounds().contains((float)xpos, (float)ypos)) {
                    htmlPanel.onMouseMove(hx, hy);
                }
            }

            @Override
            public void onMouseButton(int button, int action, int mods) {
                if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                    double[] x = new double[1], y = new double[1];
                    GLFW.glfwGetCursorPos(window.getHandle(), x, y);

                    // 按钮
                    float bx = (float) x[0] - testButton.getBounds().x;
                    float by = (float) y[0] - testButton.getBounds().y;
                    testButton.onMouseButton(button, action, bx, by);

                    // HTML
                    float hx = (float) x[0] - htmlPanel.getBounds().x;
                    float hy = (float) y[0] - htmlPanel.getBounds().y;
                    if (htmlPanel.getBounds().contains((float)x[0], (float)y[0])) {
                        htmlPanel.onMouseButton(button, action, hx, hy); // ✅ 加上 button
                    }
                }
            }
            @Override public void onResize(int width, int height) {}
            @Override public void onKey(int key, int scancode, int action, int mods) {
                htmlPanel.onKey(key, scancode, action, mods); // 测试代码记得把键盘输入传进去
            }
            @Override public void onChar(int codepoint) {
                htmlPanel.onChar(codepoint); // 测试代码记得把字符输入传进去
            }

            // 🌟 修复：捕获 Test 中的滚轮事件并传给 HTMLPanel
            @Override public void onScroll(double xoffset, double yoffset) {
                double[] cx = new double[1], cy = new double[1];
                GLFW.glfwGetCursorPos(window.getHandle(), cx, cy);
                float hx = (float) cx[0] - htmlPanel.getBounds().x;
                float hy = (float) cy[0] - htmlPanel.getBounds().y;

                if (htmlPanel.getBounds().contains((float)cx[0], (float)cy[0])) {
                    htmlPanel.onScroll((float)xoffset, (float)yoffset);
                }
            }
        });

        // --- 8. 主循环 ---
        while (!window.shouldClose()) {
            GL11.glClearColor(0.1f, 0.1f, 0.1f, 1.0f);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);

            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

            // 更新 HTML 纹理内容
            testButton.update(0.016f);
            htmlPanel.update(0.016f);

            uiContext.begin(window.getWidth(), window.getHeight());
            htmlPanel.render(uiContext);
            testButton.render(uiContext);
            uiContext.end();

            window.update();
        }

        // --- 9. 销毁 ---
        htmlPanel.dispose();
        cefApp.dispose();
        buttonTexture.dispose();
        uiContext.dispose();
        window.dispose();

        System.exit(0);
    }
}