package com.zenith.ui;

import com.zenith.asset.AssetResource;
import com.zenith.asset.Resource;
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
        AssetResource logoRes = Resource.getLogoResource();
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
        // 基础配置
        float padding = 20.0f;
        float lineSpacing = 24.0f; // 稍微增加一点行间距
        float windowWidth = ctx.getScreenWidth();
        float windowHeight = ctx.getScreenHeight();
        Font font = ctx.getFont();
        if (font == null) return;
        Renderer r = engine.getRenderer();

        // 1. --- 左上角：Logo 和 引擎版本 ---
        // 先画 Logo (假设 logoComponent 内部有自己的位置，如果没有，建议设置在 padding, padding)
        if (logoComponent != null) {
            logoComponent.onRender(ctx);
        }
        // 引擎版本号移到左下角或 Logo 下方，避免在左上角显得太乱
        ctx.drawText("Zenith Engine v1.0-Alpha", padding, 135, new Color(1, 1, 1, 0.4f));

        // 2. --- 左上角：系统信息 (向下偏移，避开 Logo) ---
        float topLeftY = 160.0f; // 从 160 开始，不再和 Logo 重叠
        ctx.drawText("SYSTEM INFO", padding, topLeftY, new Color(0.5f, 0.8f, 1f, 1f));
        topLeftY += lineSpacing;
        ctx.drawText(String.format("Window: %dx%d (Aspect: %.2f)",
                engine.getWindow().getWidth(), engine.getWindow().getHeight(),
                (float) engine.getWindow().getWidth() / engine.getWindow().getHeight()), padding, topLeftY, labelColor);
        topLeftY += lineSpacing;
        ctx.drawText("OS: " + System.getProperty("os.name"), padding, topLeftY, labelColor);
        topLeftY += lineSpacing;
        ctx.drawText("Java: " + System.getProperty("java.version"), padding, topLeftY, labelColor);


        // 3. --- 右上角：性能统计 (FPS/MS 放在最上面) ---
        float upperY = padding;
        drawAlignRight(ctx, font, "FPS: " + fpsDisplay, windowWidth - padding, upperY, Color.GREEN);
        upperY += lineSpacing;
        drawAlignRight(ctx, font, String.format("%.2f ms", msDisplay), windowWidth - padding, upperY, Color.YELLOW);

        // RENDER STATS 区域 (增加一段间距)
        upperY += lineSpacing * 2.0f;
        drawAlignRight(ctx, font, "RENDER STATS", windowWidth - padding, upperY, new Color(1f, 0.5f, 0f, 1f));

        int dc = (r != null) ? r.getDrawCalls() : 0;
        int tris = (r != null) ? r.getTriangleCount() : 0;
        upperY += lineSpacing;
        drawAlignRight(ctx, font, "Draw Calls: " + dc, windowWidth - padding, upperY, valueColor);
        upperY += lineSpacing;
        drawAlignRight(ctx, font, "Triangles: " + (tris / 1000) + "K", windowWidth - padding, upperY, valueColor);

        // MEM & BUFFER 区域 (增加间距)
        upperY += lineSpacing * 2.0f;
        long maxMem = Runtime.getRuntime().maxMemory() / 1024 / 1024;
        long usedMem = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024 / 1024;
        Color memColor = (usedMem > maxMem * 0.8) ? Color.RED : valueColor;
        drawAlignRight(ctx, font, String.format("MEM: %dMB / %dMB", usedMem, maxMem), windowWidth - padding, upperY, memColor);

        upperY += lineSpacing;
        com.zenith.render.backend.opengl.buffer.GLBufferBuilder bb = ctx.getBufferBuilder();
        if (bb != null) {
            int usedBytes = bb.getUsedBytes();
            int capacity = bb.getCapacity();
            float usagePercent = (capacity > 0) ? (float) usedBytes / capacity * 100.0f : 0;
            drawAlignRight(ctx, font, String.format("UI Buffer: %dKB / %dKB (%.1f%%)",
                    usedBytes / 1024, capacity / 1024, usagePercent), windowWidth - padding, upperY, new Color(0.7f, 0.7f, 1f, 1f));
        }

        upperY += lineSpacing;
        if (engine.getSceneFBO() != null) {
            String fboInfo = String.format("FBO IDs: [Color:%d Depth:%d]",
                    engine.getSceneFBO().getColorTex(), engine.getSceneFBO().getDepthTex());
            drawAlignRight(ctx, font, fboInfo, windowWidth - padding, upperY, new Color(0.6f, 0.6f, 0.6f, 1f));
        }


        // 4. --- 左下角：相机信息 ---
        float bottomLeftY = windowHeight - padding - (lineSpacing * 2); // 预留三行的位置
        Vector3f pos = engine.getCamera().getTransform().getPosition();
        ctx.drawText("CAMERA", padding, bottomLeftY, new Color(0.4f, 1f, 0.4f, 1f));
        bottomLeftY += lineSpacing;
        ctx.drawText(String.format("Pos: %.2f / %.2f / %.2f", pos.x, pos.y, pos.z), padding, bottomLeftY, labelColor);
        bottomLeftY += lineSpacing;
        ctx.drawText(String.format("Rot: Yaw:%.1f Pitch:%.1f", engine.getYaw(), engine.getPitch()), padding, bottomLeftY, labelColor);


        // 5. --- 右下角：硬件信息 (从下往上堆叠) ---
        float bottomRightY = windowHeight - padding;
        // 最后一层
        String activeShader = "Shader: " + ((r instanceof com.zenith.render.backend.opengl.GLRenderer glr) ? glr.getLastShaderName() : "None");
        drawAlignRight(ctx, font, activeShader, windowWidth - padding, bottomRightY, new Color(0.2f, 0.7f, 1f, 1f));

        // 倒数第二层
        bottomRightY -= lineSpacing;
        drawAlignRight(ctx, font, "API: OpenGL " + glVersion, windowWidth - padding, bottomRightY, labelColor);

        // 倒数第三层
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