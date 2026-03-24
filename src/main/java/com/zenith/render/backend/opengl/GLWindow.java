package com.zenith.render.backend.opengl;

import com.zenith.common.utils.InternalLogger;
import com.zenith.render.Window;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;

import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryUtil.NULL;

/**
 * GLWindow 是 Window 接口的 OpenGL/GLFW 实现版本。
 */
public class GLWindow implements Window {

    private long windowHandle;
    private String title;
    private int width, height;

    private WindowEventListener eventListener;

    public GLWindow(String title, int width, int height) {
        this.title = title;
        this.width = width;
        this.height = height;
    }

    public void init() {
        InternalLogger.info("Starting GLWindow initialization...");

        if (!glfwInit()) {
            InternalLogger.error("Failed to initialize GLFW!");
            throw new IllegalStateException("GLFW Initialization failed.");
        }

        glfwDefaultWindowHints();
        GLFWErrorCallback.createPrint(System.err).set();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);

        if (System.getProperty("os.name").toLowerCase().contains("mac")) {
            glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);
        }

        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_COMPAT_PROFILE);

        windowHandle = glfwCreateWindow(width, height, title, NULL, NULL);
        if (windowHandle == NULL) {
            InternalLogger.error("Failed to create GLFW window handle!");
            throw new RuntimeException("Window Handle is NULL.");
        }

        // 注册回调
        glfwSetFramebufferSizeCallback(windowHandle, (window, w, h) -> {
            this.width = w;
            this.height = h;
            glViewport(0, 0, w, h);
            if (eventListener != null) eventListener.onResize(w, h);
        });

        glfwSetKeyCallback(windowHandle, (window, key, scancode, action, mods) -> {
            if (eventListener != null) eventListener.onKey(key, scancode, action, mods);
        });

        glfwSetCursorPosCallback(windowHandle, (window, xpos, ypos) -> {
            if (eventListener != null) eventListener.onCursorPos(xpos, ypos);
        });

        glfwSetMouseButtonCallback(windowHandle, (window, button, action, mods) -> {
            if (eventListener != null) eventListener.onMouseButton(button, action, mods);
        });

        glfwSetScrollCallback(windowHandle, (window, xoffset, yoffset) -> {
            if (eventListener != null) eventListener.onScroll(xoffset, yoffset);
        });

        glfwMakeContextCurrent(windowHandle);
        glfwSwapInterval(1);
        glfwShowWindow(windowHandle);

        GL.createCapabilities();

        InternalLogger.info("GLWindow initialized successfully: " + glGetString(GL_RENDERER));
    }

    @Override
    public void update() {
        glfwPollEvents();
        glfwSwapBuffers(windowHandle);
    }

    @Override
    public boolean shouldClose() {
        return glfwWindowShouldClose(windowHandle);
    }

    @Override
    public int getWidth() { return width; }

    @Override
    public int getHeight() { return height; }

    @Override
    public long getNativeHandle() { return windowHandle; }

    @Override
    public long getHandle() { return windowHandle; }

    @Override
    public void setEventListener(WindowEventListener listener) {
        this.eventListener = listener;
    }

    @Override
    public void dispose() {
        InternalLogger.info("Disposing GLWindow...");
        glfwFreeCallbacks(windowHandle);
        glfwDestroyWindow(windowHandle);
        glfwTerminate();
    }
}