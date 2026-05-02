package com.zenith.ui.component;

import com.zenith.common.utils.HTMLLogger;
import com.zenith.common.utils.Win32IMEHelper;
import org.cef.CefClient;
import org.cef.CefSettings;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefDevToolsClient;
import com.zenith.ui.render.UIRenderContext;
import com.zenith.render.backend.opengl.texture.GLTexture;
import com.zenith.common.utils.InternalLogger;
import org.cef.browser.CefFrame;
import org.cef.handler.CefDisplayHandlerAdapter;
import org.cef.handler.CefLifeSpanHandlerAdapter;
import org.cef.handler.CefRequestHandlerAdapter;
import org.cef.network.CefRequest;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import javax.swing.SwingUtilities;
import java.awt.*;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class HTMLComponent extends UIComponent {
    private final CefBrowser browser;
    private GLTexture zenithTexture;

    private ByteBuffer pendingBuffer;
    private final Object bufferLock = new Object();
    private boolean hasNewFrame = false;

    private int realFrameWidth = 0;
    private int realFrameHeight = 0;

    // 记录鼠标状态，用于支持拖拽和多键同时按下
    private int mouseButtonsMask = 0;
    private float lastMouseX = 0;
    private float lastMouseY = 0;

    public HTMLComponent(CefClient client, String url, float x, float y, float width, float height) {
        super(x, y, width, height);

        this.zenithTexture = new GLTexture((int)width, (int)height);
        this.browser = client.createBrowser(url, true, false);
        this.browser.createImmediately();

        SwingUtilities.invokeLater(() -> {
            try {
                Class<?> clazz = this.browser.getClass();
                if (clazz.getSimpleName().equals("CefBrowserOsr")) {
                    java.lang.reflect.Method m = clazz.getDeclaredMethod("triggerResize", int.class, int.class);
                    m.setAccessible(true);
                    m.invoke(this.browser, (int)width, (int)height);
                }
            } catch (Throwable t) {
                InternalLogger.error("CEF 触发 Resize 失败", t);
            }
        });

        this.browser.getRenderHandler().addOnPaintListener(event -> {
            try {
                ByteBuffer buffer = event.getRenderedFrame();
                if (buffer == null) return;

                synchronized (bufferLock) {
                    int w = event.getWidth();
                    int h = event.getHeight();
                    ByteBuffer safeBuffer = buffer.duplicate();
                    int expectedSize = w * h * 4;

                    if (safeBuffer.remaining() < expectedSize) return;

                    if (pendingBuffer == null || pendingBuffer.capacity() < expectedSize) {
                        pendingBuffer = ByteBuffer.allocateDirect(expectedSize).order(ByteOrder.nativeOrder());
                    }

                    pendingBuffer.clear();
                    safeBuffer.limit(safeBuffer.position() + expectedSize);
                    pendingBuffer.put(safeBuffer);
                    pendingBuffer.flip();

                    this.realFrameWidth = w;
                    this.realFrameHeight = h;
                    this.hasNewFrame = true;
                }
            } catch (Throwable t) {
                InternalLogger.error("渲染帧处理异常", t);
            }
        });

        client.addDisplayHandler(new CefDisplayHandlerAdapter() {
            @Override
            public boolean onConsoleMessage(CefBrowser browser, CefSettings.LogSeverity level,
                                            String message, String source, int line) {
                HTMLLogger.log(level.name(), message, source, line);
                return true;
            }
        });

        client.addRequestHandler(new org.cef.handler.CefRequestHandlerAdapter() {
            @Override
            public boolean onBeforeBrowse(CefBrowser browser, org.cef.browser.CefFrame frame, org.cef.network.CefRequest request, boolean user_gesture, boolean is_redirect) {
                if (user_gesture) {
                    String targetUrl = request.getURL();
                    openSystemBrowserNative(targetUrl);
                    return true; // 拦截
                }
                return false;
            }
        });

        // 🌟 修复 2: 拦截“新窗口”弹出（B站视频点击的关键）
        client.addLifeSpanHandler(new CefLifeSpanHandlerAdapter() {
            @Override
            public boolean onBeforePopup(CefBrowser browser, CefFrame frame, String target_url, String target_frame_name) {
                openSystemBrowserNative(target_url);
                return true;
            }
        });
    }

    /**
     * 使用原生 Runtime 指令打开浏览器，避开 AWT Desktop 类的崩溃
     */
    private void openSystemBrowserNative(String url) {
        if (url == null || url.isEmpty()) return;

        new Thread(() -> {
            try {
                String os = System.getProperty("os.name").toLowerCase();
                if (os.contains("win")) {
                    // Windows 下最稳妥的办法：调用 cmd 的 start 命令
                    Runtime.getRuntime().exec(new String[]{"cmd", "/c", "start", url.replace("&", "^&")});
                } else if (os.contains("mac")) {
                    Runtime.getRuntime().exec(new String[]{"open", url});
                } else {
                    Runtime.getRuntime().exec(new String[]{"xdg-open", url});
                }
                InternalLogger.info("已通过系统命令打开 URL: " + url);
            } catch (Exception e) {
                InternalLogger.error("原生打开浏览器失败: " + url, e);
            }
        }).start();
    }

    @Override
    public void update(float deltaTime) {
        synchronized (bufferLock) {
            if (hasNewFrame && pendingBuffer != null && realFrameWidth == zenithTexture.getWidth() && realFrameHeight == zenithTexture.getHeight()) {
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, zenithTexture.getId());
                GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);

                GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, realFrameWidth, realFrameHeight, GL12.GL_BGRA, GL11.GL_UNSIGNED_BYTE, pendingBuffer);

                GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
                hasNewFrame = false;
            }
        }
    }

    @Override
    protected void onRender(UIRenderContext ctx) {
        if (zenithTexture != null) {
            ctx.bindTexture(zenithTexture);
            ctx.drawTextureRect(0, 0, bounds.width, bounds.height, 0, 0, 1, 1, com.zenith.common.math.Color.WHITE);
        }
    }

    /**
     * 调用系统默认浏览器打开 URL
     */
    private void openSystemBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(new URI(url));
            } else {
                Runtime runtime = Runtime.getRuntime();
                runtime.exec("rundll32 url.dll,FileProtocolHandler " + url);
            }
        } catch (Exception e) {
            InternalLogger.error("无法打开系统浏览器: " + url, e);
        }
    }

    @Override
    public boolean onMouseMove(float mx, float my) {
        this.lastMouseX = mx;
        this.lastMouseY = my;
        CefDevToolsClient devTools = browser.getDevToolsClient();
        if (devTools != null && !devTools.isClosed()) {
            // 决定拖拽时的主按键
            String mainButton = "none";
            if ((mouseButtonsMask & 1) != 0) mainButton = "left";
            else if ((mouseButtonsMask & 2) != 0) mainButton = "right";
            else if ((mouseButtonsMask & 4) != 0) mainButton = "middle";

            // 🌟 修复拖拽：必须传入 buttons 掩码参数
            String json = String.format("{\"type\":\"mouseMoved\", \"x\":%d, \"y\":%d, \"button\":\"%s\", \"buttons\":%d}",
                    (int)mx, (int)my, mainButton, mouseButtonsMask);
            devTools.executeDevToolsMethod("Input.dispatchMouseEvent", json);
        }
        return true;
    }

    @Override
    public boolean onMouseButton(int button, int action, float mx, float my) {
        if (action == 1) {
            try {
                long glfwHandle = org.lwjgl.glfw.GLFW.glfwGetCurrentContext();
                if (glfwHandle != 0) {
                    long hwnd = org.lwjgl.glfw.GLFWNativeWin32.glfwGetWin32Window(glfwHandle);
                    int winX = (int) (bounds.x + mx);
                    int winY = (int) (bounds.y + my + 15);
                    Win32IMEHelper.setIMEPosition(hwnd, winX, winY);
                }
            } catch (Throwable t) {
            }
            SwingUtilities.invokeLater(() -> {
                try {
                    browser.setFocus(true);
                } catch (Throwable ignored) {
                }
            });
        }

        // 🌟 3. 发送事件给 CEF 内核
        CefDevToolsClient devTools = browser.getDevToolsClient();
        if (devTools != null && !devTools.isClosed()) {
            boolean pressed = (action == 1);
            String type = pressed ? "mousePressed" : "mouseReleased";

            String btnName = "none";
            int mask = 0;
            switch (button) {
                case 0: btnName = "left"; mask = 1; break;
                case 1: btnName = "right"; mask = 2; break;
                case 2: btnName = "middle"; mask = 4; break;
                case 3: btnName = "back"; mask = 8; break;
                case 4: btnName = "forward"; mask = 16; break;
            }

            if (pressed) mouseButtonsMask |= mask;
            else mouseButtonsMask &= ~mask;

            if (!btnName.equals("none")) {
                // 🌟 修复：构造标准的 CDP 鼠标事件 JSON
                String json = String.format(
                        "{\"type\":\"%s\", \"x\":%d, \"y\":%d, \"button\":\"%s\", \"buttons\":%d, \"clickCount\":1}",
                        type, (int)mx, (int)my, btnName, mouseButtonsMask
                );
                devTools.executeDevToolsMethod("Input.dispatchMouseEvent", json);
            }
        }
        return true;
    }

    private int translateModifiers(int glfwMods) {
        int cdpMods = 0;
        if ((glfwMods & 0x0001) != 0) cdpMods |= 8; // Shift
        if ((glfwMods & 0x0002) != 0) cdpMods |= 2; // Control
        if ((glfwMods & 0x0004) != 0) cdpMods |= 1; // Alt
        if ((glfwMods & 0x0008) != 0) cdpMods |= 4; // Meta (Command/Windows键)
        return cdpMods;
    }

    @Override
    public boolean onScroll(float dx, float dy) {
        CefDevToolsClient devTools = browser.getDevToolsClient();
        if (devTools != null && !devTools.isClosed()) {
            // 🌟 修复滚轮：dy 在 GLFW 中向下滚是 -1，在 CDP 中向下滚 deltaY 是正数，所以乘 -100
            String json = String.format("{\"type\":\"mouseWheel\", \"x\":%d, \"y\":%d, \"deltaX\":%f, \"deltaY\":%f}",
                    (int)lastMouseX, (int)lastMouseY, dx * -100f, dy * -100f);
            devTools.executeDevToolsMethod("Input.dispatchMouseEvent", json);
            return true;
        }
        return false;
    }

    private int mapGlfwToVKey(int glfwKey) {
        // 🌟 修复打字：补充了最基本的字母、数字和常用控制键的映射
        if (glfwKey >= 65 && glfwKey <= 90) return glfwKey; // A-Z
        if (glfwKey >= 48 && glfwKey <= 57) return glfwKey; // 0-9

        switch (glfwKey) {
            case 259: return 0x08; // Backspace
            case 258: return 0x09; // Tab
            case 257: return 0x0D; // Enter
            case 256: return 0x1B; // Esc
            case 262: return 0x27; // Right
            case 263: return 0x25; // Left
            case 264: return 0x28; // Down
            case 265: return 0x26; // Up
            case 261: return 0x2E; // Delete
            case 32:  return 0x20; // Space
            case 340: case 344: return 0x10; // Shift
            case 341: case 345: return 0x11; // Ctrl
            case 342: case 346: return 0x12; // Alt
            default: return 0;
        }
    }

    @Override
    public boolean onKey(int key, int scancode, int action, int mods) {
        CefDevToolsClient devTools = browser.getDevToolsClient();
        if (devTools != null && !devTools.isClosed()) {
            int vkey = mapGlfwToVKey(key);
            if (vkey != 0) {
                int cdpMods = translateModifiers(mods);
                String type = (action == 0) ? "keyUp" : "rawKeyDown";
                String json = String.format(
                        "{\"type\":\"%s\", \"windowsVirtualKeyCode\":%d, \"modifiers\":%d}",
                        type, vkey, cdpMods
                );
                devTools.executeDevToolsMethod("Input.dispatchKeyEvent", json);
                return true;
            }
        }
        return false;
    }


    @Override
    public boolean onChar(int codepoint) {
        CefDevToolsClient devTools = browser.getDevToolsClient();
        if (devTools != null && !devTools.isClosed()) {
            String text = String.valueOf((char)codepoint).replace("\\", "\\\\").replace("\"", "\\\"");
            devTools.executeDevToolsMethod("Input.dispatchKeyEvent",
                    String.format("{\"type\":\"char\", \"text\":\"%s\"}", text));
            return true;
        }
        return false;
    }

    public void dispose() {
        if (zenithTexture != null) zenithTexture.dispose();
        browser.close(true);
    }
}