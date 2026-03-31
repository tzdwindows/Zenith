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
import com.zenith.ui.render.TextureAtlas;
import com.zenith.ui.render.UIRenderContext;
import com.zenith.render.backend.opengl.shader.UIShader;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import java.io.IOException;

import static com.zenith.render.backend.opengl.test.Test2.loadFromResources;

public class Test {

    private static UIButton testButton;

    public static void main(String[] args) throws IOException {
        GLWindow window = new GLWindow("Zenith UI Test - Button Only", 1280, 720);
        window.init();

        GLRenderer renderer = new GLRenderer();
        GLBufferBuilder bufferBuilder = new GLBufferBuilder(1024 * 64);

        VertexLayout uiLayout = new VertexLayout();
        uiLayout.pushFloat("aPos", 2);
        uiLayout.pushFloat("aTexCoords", 2);
        uiLayout.pushFloat("aColor", 4);

        UIShader uiShader = new UIShader();
        GLMaterial uiMaterial = new GLMaterial(uiShader);

        UIRenderContext uiContext = new UIRenderContext(renderer, bufferBuilder, uiMaterial, uiLayout);
        AssetIdentifier id = new AssetIdentifier("zenith", "textures/kenney_game-icons/sheet_white2x.png");
        AssetResource res = loadFromResources(id);
        GLTexture buttonTexture = new GLTexture(res);
        AssetResource xmlRes = AssetResource.loadFromResources("textures/kenney_game-icons/sheet_white2x.xml");
        TextureAtlas atlas = new TextureAtlas(buttonTexture, xmlRes);
        testButton = new UIButton(460, 320, 360, 80, atlas, "buttonA.png")
                .setPressedScale(0.85f)
                .setOnClick(() -> System.out.println("Click!"))
                .setColors(Color.WHITE, Color.LIGHT_GRAY, Color.GREEN);
        testButton.setColors(Color.WHITE, Color.LIGHT_GRAY, Color.GREEN);

        testButton.setOnClick(() -> {
            System.out.println("Button Triggered!");
        });

        window.setEventListener(new Window.WindowEventListener() {
            @Override
            public void onCursorPos(double xpos, double ypos) {
                // 直接计算相对于按钮自己的偏移
                float bx = (float) xpos - testButton.getBounds().x;
                float by = (float) ypos - testButton.getBounds().y;
                testButton.onMouseMove(bx, by);
            }

            @Override
            public void onMouseButton(int button, int action, int mods) {
                if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                    double[] x = new double[1], y = new double[1];
                    GLFW.glfwGetCursorPos(window.getHandle(), x, y);
                    float bx = (float) x[0] - testButton.getBounds().x;
                    float by = (float) y[0] - testButton.getBounds().y;
                    testButton.onMouseButton(action, bx, by);
                }
            }
            @Override public void onResize(int width, int height) {}
            @Override public void onKey(int key, int scancode, int action, int mods) {}
            @Override public void onScroll(double xoffset, double yoffset) {}
        });

        while (!window.shouldClose()) {
            GL11.glClearColor(0.1f, 0.1f, 0.1f, 1.0f);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);

            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

            // 模拟简单的逻辑更新
            testButton.update(0.016f);

            uiContext.begin(window.getWidth(), window.getHeight());

            // --- 关键：如果 UIButton.render 内部没有处理绝对坐标转换，这里手动平移 ---
            // 如果你的 UIButton 内部是用 (0,0,w,h) 画的，请取消下面这行的注释
            // uiContext.pushTransform(testButton.getBounds().x, testButton.getBounds().y);

            testButton.render(uiContext);

            // uiContext.popTransform();

            uiContext.end();

            window.update();
        }

        buttonTexture.dispose();
        uiContext.dispose();
        window.dispose();
    }
}