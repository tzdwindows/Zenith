package org.cef.browser;

import org.cef.CefBrowserSettings;
import org.cef.CefClient;
import org.cef.callback.CefDragData;
import org.cef.handler.CefRenderHandler;
import org.cef.handler.CefScreenInfo;
import com.zenith.common.utils.InternalLogger;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

// [Zenith 专属极简版] 100% 无头渲染，杜绝 AWT 与 JOGL 冲突
public class CefBrowserOsr extends CefBrowser_N implements CefRenderHandler {
    private final Component canvas_ = new Component() {
        @Override public Point getLocationOnScreen() { return new Point(0, 0); }
        @Override public boolean isShowing() { return true; }
        @Override public boolean isVisible() { return true; }
        @Override public boolean isFocusable() { return true; }
        @Override public void requestFocus() {}
        @Override public boolean requestFocus(boolean temporary) { return true; }
        @Override public boolean requestFocusInWindow() { return true; }
        @Override public Container getParent() { return null; }
        @Override public GraphicsConfiguration getGraphicsConfiguration() { return null; }
    };

    private Rectangle browser_rect_ = new Rectangle(0, 0, 1, 1);
    private final CopyOnWriteArrayList<Consumer<CefPaintEvent>> onPaintListeners = new CopyOnWriteArrayList<>();

    public CefBrowserOsr(CefClient client, String url, boolean transparent, CefRequestContext context, CefBrowserSettings settings) {
        super(client, url, context, null, null, settings);
        InternalLogger.info("[ZENITH JCEF] 加载无 AWT 异常的终极定制版 CefBrowserOsr");
    }

    @Override
    public void createImmediately() {
        if (getNativeRef("CefBrowser") == 0) {
            // 强制设为不透明 (false) 防止 Alpha 穿透导致黑屏
            createBrowser(getClient(), 0, getUrl(), true, false, null, getRequestContext());
        }
    }

    // 暴露给外部调用的触发器
    public void triggerResize(int width, int height) {
        browser_rect_.setBounds(0, 0, width, height);
        if (getNativeRef("CefBrowser") != 0) {
            wasResized(width, height);
        }
    }

    @Override
    public Component getUIComponent() { return canvas_; }
    @Override
    public CefRenderHandler getRenderHandler() { return this; }
    @Override
    protected CefBrowser_N createDevToolsBrowser(CefClient client, String url, CefRequestContext context, CefBrowser_N parent, Point inspectAt) { return null; }
    @Override
    public Rectangle getViewRect(CefBrowser browser) { return browser_rect_; }
    @Override
    public Point getScreenPoint(CefBrowser browser, Point viewPoint) { return new Point(0, 0); }
    @Override
    public void onPopupShow(CefBrowser browser, boolean show) {}
    @Override
    public void onPopupSize(CefBrowser browser, Rectangle size) {}
    @Override
    public void addOnPaintListener(Consumer<CefPaintEvent> listener) { onPaintListeners.add(listener); }
    @Override
    public void setOnPaintListener(Consumer<CefPaintEvent> listener) {
        onPaintListeners.clear();
        onPaintListeners.add(listener);
    }
    @Override
    public void removeOnPaintListener(Consumer<CefPaintEvent> listener) { onPaintListeners.remove(listener); }

    @Override
    public void onPaint(CefBrowser browser, boolean popup, Rectangle[] dirtyRects, ByteBuffer buffer, int width, int height) {
        if (onPaintListeners.isEmpty()) return;
        for (Consumer<CefPaintEvent> l : onPaintListeners) {
            try {
                l.accept(new CefPaintEvent(browser, popup, dirtyRects, buffer, width, height));
            } catch (Throwable t) {
                InternalLogger.error("渲染监听器回调崩溃", t);
            }
        }
    }

    @Override
    public boolean onCursorChange(CefBrowser browser, int cursorType) { return true; }
    @Override
    public boolean startDragging(CefBrowser browser, CefDragData dragData, int mask, int x, int y) { return false; }
    @Override
    public void updateDragCursor(CefBrowser browser, int operation) {}
    @Override
    public boolean getScreenInfo(CefBrowser browser, CefScreenInfo screenInfo) {
        screenInfo.Set(1.0, 32, 8, false, browser_rect_.getBounds(), browser_rect_.getBounds());
        return true;
    }
    @Override
    public CompletableFuture<BufferedImage> createScreenshot(boolean nativeResolution) { return new CompletableFuture<>(); }
}