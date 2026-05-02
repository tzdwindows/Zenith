package com.zenith.render;

/**
 * 通用窗口接口，定义了渲染引擎对显示设备的基本需求。
 */
public interface Window {

    /**
     * 定义窗口事件的回调接口
     */
    interface WindowEventListener {
        // 键盘事件：key(键位), scancode(扫描码), action(按下/释放/重复), mods(修饰键如Shift/Ctrl)
        void onKey(int key, int scancode, int action, int mods);

        default void onChar(int codepoint){};

        // 鼠标移动事件
        void onCursorPos(double xpos, double ypos);

        // 鼠标按键事件
        void onMouseButton(int button, int action, int mods);

        // 鼠标滚轮事件
        void onScroll(double xoffset, double yoffset);

        // 窗口尺寸变更
        void onResize(int width, int height);
    }

    void update();
    boolean shouldClose();
    int getWidth();
    int getHeight();
    long getNativeHandle();

    long getHandle();

    void setEventListener(WindowEventListener listener);

    void dispose();
}