package com.zenith.ui;

import com.zenith.asset.AssetResource;
import com.zenith.common.math.Color;
import com.zenith.common.math.Rectf;
import com.zenith.render.Renderer;
import com.zenith.ui.component.UIImageComponent;
import com.zenith.ui.render.UIRenderContext;
import com.zenith.render.Font;
import com.zenith.core.ZenithEngine;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

public class DebugInfoScreen extends UIScreen {
    private final ZenithEngine engine;
    private String fpsDisplay = "0";
    private float msDisplay = 0.0f;
    private final String gpuName;
    private final String glVersion;

    private float timeAccumulator = 0;
    private int frameCount = 0;

    private final UIImageComponent logoComponent;
    private final Color labelColor = new Color(0.7f, 0.7f, 0.7f, 1.0f);
    private final Color valueColor = new Color(0.2f, 0.8f, 1.0f, 1.0f);

    public DebugInfoScreen(ZenithEngine engine) {
        this.engine = engine;
        this.gpuName = GL11.glGetString(GL11.GL_RENDERER);
        this.glVersion = GL11.glGetString(GL11.GL_VERSION).split(" ")[0];
        AssetResource logoRes = AssetResource.loadFromResources("textures/logo.png");
        this.logoComponent = new UIImageComponent(15, 15, 100, 100, logoRes);
    }

    @Override
    public void update(float deltaTime) {
        timeAccumulator += deltaTime;
        frameCount++;

        if (timeAccumulator >= 1.0f) {
            fpsDisplay = String.valueOf(frameCount);
            msDisplay = (timeAccumulator / frameCount) * 1000.0f; // 计算平均每帧毫秒数
            frameCount = 0;
            timeAccumulator -= 1.0f;
        }
    }

    @Override
    public void render(UIRenderContext ctx) {
        if (logoComponent != null) {
            logoComponent.onRender(ctx);
        }
        ctx.drawText("Zenith Engine v1.0-Alpha", 15, 135, new Color(1, 1, 1, 0.4f));
        Font font = ctx.getFont();
        if (font == null) return;
        float padding = 20.0f;
        float lineSpacing = 22.0f;
        float windowWidth = ctx.getScreenWidth();
        float windowHeight = ctx.getScreenHeight();
        Renderer r = engine.getRenderer();
        int dc = (r != null) ? r.getDrawCalls() : 0;
        int tris = (r != null) ? r.getTriangleCount() : 0;
        float upperY = 30.0f;
        drawAlignRight(ctx, font, "FPS: " + fpsDisplay, windowWidth - padding, upperY, Color.GREEN);
        upperY += lineSpacing;
        drawAlignRight(ctx, font, String.format("%.2f ms", msDisplay), windowWidth - padding, upperY, Color.YELLOW);
        upperY += lineSpacing * 1.5f;
        drawAlignRight(ctx, font, "DC: " + dc, windowWidth - padding, upperY, new Color(1f, 0.6f, 0.2f, 1f));
        upperY += lineSpacing;
        drawAlignRight(ctx, font, "Tris: " + (tris / 1000) + "K", windowWidth - padding, upperY, new Color(1f, 0.6f, 0.2f, 1f));
        upperY += lineSpacing * 1.5f;
        long maxMem = Runtime.getRuntime().maxMemory() / 1024 / 1024;
        long usedMem = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024 / 1024;
        Color memColor = (usedMem > maxMem * 0.8) ? Color.RED : valueColor;
        drawAlignRight(ctx, font, String.format("MEM: %dMB / %dMB", usedMem, maxMem), windowWidth - padding, upperY, memColor);
        float bottomLeftY = windowHeight - padding - lineSpacing;
        Vector3f pos = engine.getCamera().getTransform().getPosition();
        ctx.drawText(String.format("XYZ: %.2f / %.2f / %.2f", pos.x, pos.y, pos.z), padding, bottomLeftY, labelColor);
        ctx.drawText(String.format("YAW/PITCH: %.1f / %.1f", engine.getYaw(), engine.getPitch()), padding, bottomLeftY + lineSpacing, labelColor);
        float bottomRightY = windowHeight - padding;
        String activeShader = "Shader: " + ((r instanceof com.zenith.render.backend.opengl.GLRenderer glr) ? glr.getLastShaderName() : "Unknown");
        drawAlignRight(ctx, font, activeShader, windowWidth - padding, bottomRightY, valueColor);
        bottomRightY -= lineSpacing;
        drawAlignRight(ctx, font, "API: OpenGL " + glVersion, windowWidth - padding, bottomRightY, labelColor);
        bottomRightY -= lineSpacing;
        drawAlignRight(ctx, font, "GPU: " + gpuName, windowWidth - padding, bottomRightY, new Color(0.8f, 0.8f, 0.8f, 1f));
    }

    /**
     * 内部辅助：自动计算宽度并实现右对齐渲染（带文字阴影以增强对比度）
     */
    private void drawAlignRight(UIRenderContext ctx, Font font, String text, float rightX, float y, Color color) {
        float x = rightX - font.getWidth(text);
        ctx.drawText(text, x + 1.5f, y + 1.5f, new Color(0, 0, 0, 0.6f));
        ctx.drawText(text, x, y, color);
    }
}