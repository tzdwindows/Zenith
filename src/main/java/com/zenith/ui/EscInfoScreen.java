package com.zenith.ui;

import com.zenith.asset.AssetResource;
import com.zenith.common.math.Color;
import com.zenith.common.math.Rectf;
import com.zenith.core.ZenithEngine;
import com.zenith.render.backend.opengl.texture.GLTexture;
import com.zenith.ui.component.UIButton;
import com.zenith.ui.component.UIComponent;
import com.zenith.ui.render.TextureAtlas;
import com.zenith.ui.render.UIRenderContext;
import com.zenith.render.Font;
import org.lwjgl.glfw.GLFW;

/**
 * 暂停菜单屏幕 - 适配修改后的 UIScreen 基类
 */
public class EscInfoScreen extends UIScreen {
    private final ZenithEngine engine;
    private final Color overlayColor = new Color(0.1f, 0.1f, 0.1f, 0.5f);
    private final Color titleColor = new Color(1.0f, 1.0f, 1.0f, 1.0f);

    public EscInfoScreen(ZenithEngine engine) {
        this.engine = engine;
        initMenu();
    }

    private void initMenu() {
        try {
            float btnWidth = 220;
            float btnHeight = 45;
            AssetResource texRes = AssetResource.loadFromResources("textures/kenney_game-icons/sheet_white2x.png");
            GLTexture buttonTexture = new GLTexture(texRes);
            AssetResource xmlRes = AssetResource.loadFromResources("textures/kenney_game-icons/sheet_white2x.xml");
            TextureAtlas uiAtlas = new TextureAtlas(buttonTexture, xmlRes);
            addComponent(new UIButton(0, 0, btnWidth, btnHeight, uiAtlas, "stop.png")
                    .setLabel("RESUME")
                    .setTooltip("Resume gameplay")
                    .setColors(Color.WHITE, new Color(0.8f, 0.8f, 0.8f, 1f), Color.GREEN)
                    .setOnClick(() -> {
                        this.setVisible(false);
                        engine.setCursorMode(true);
                    }));
            addComponent(new UIButton(0, 0, btnWidth, btnHeight, uiAtlas, "stop.png")
                    .setLabel("QUIT")
                    .setTooltip("Exit to Desktop")
                    .setColors(Color.WHITE, new Color(0.8f, 0.8f, 0.8f, 1f), new Color(1f, 0.3f, 0.3f, 1f))
                    .setOnClick(() -> {
                        GLFW.glfwSetWindowShouldClose(engine.getWindow().getHandle(), true);
                    }));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void render(UIRenderContext ctx) {
        if (!isVisible()) return;
        float sw = ctx.getScreenWidth();
        float sh = ctx.getScreenHeight();
        ctx.drawRect(new Rectf(0, 0, sw, sh), overlayColor);
        Font font = ctx.getFont();
        if (font != null) {
            String title = "GAME PAUSED";
            float titleX = (sw - font.getWidth(title)) / 2f;
            ctx.drawText(title, titleX, sh * 0.25f, titleColor);
        }
        float currentY = sh * 0.45f;
        float spacing = 20f;
        for (UIComponent comp : components) {
            comp.getBounds().x = (sw - comp.getBounds().width) / 2f;
            comp.getBounds().y = currentY;
            currentY += comp.getBounds().height + spacing;
        }
        super.render(ctx);
        ctx.drawText("Zenith Engine v1.0", 20, sh - 30, new Color(1, 1, 1, 0.3f));
    }

    @Override
    public boolean isModal() {
        return true;
    }
}