package com.zenith.ui.render;

import com.zenith.common.math.*;
import com.zenith.render.*;
import com.zenith.render.backend.opengl.GLMesh;
import com.zenith.render.backend.opengl.GLRenderer;
import com.zenith.render.backend.opengl.buffer.GLBufferBuilder;
import com.zenith.render.backend.opengl.shader.UIShader;
import com.zenith.render.backend.opengl.texture.GLTexture;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Stack;

public class UIRenderContext {
    private float screenWidth;
    private float screenHeight;
    private final GLBufferBuilder bufferBuilder;
    private final Renderer renderer;
    private final Stack<Matrix4f> matrixStack = new Stack<>();
    private final VertexLayout uiLayout;

    private Material uiMaterial;
    private final GLMesh uiMesh;
    private Matrix4f projectionMatrix;
    private final Stack<Material> materialStack = new Stack<>();
    private com.zenith.render.Texture currentTexture = null;
    private com.zenith.render.Texture whiteTexture;

    public UIRenderContext(Renderer renderer, GLBufferBuilder bufferBuilder, Material uiMaterial, VertexLayout layout) {
        this.renderer = renderer;
        this.bufferBuilder = bufferBuilder;
        this.uiMaterial = uiMaterial;
        this.uiLayout = layout;

        this.uiMesh = new GLMesh(4000, uiLayout);
        this.matrixStack.push(new Matrix4f().identity());
        this.materialStack.push(uiMaterial);

        ByteBuffer buffer = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder());
        buffer.put((byte) 255); // R
        buffer.put((byte) 255); // G
        buffer.put((byte) 255); // B
        buffer.put((byte) 255); // A
        buffer.flip();
        this.whiteTexture = new GLTexture(1, 1, buffer, GL11.GL_RGBA);
    }

    public void begin(float width, float height) {
        if (width <= 0 || height <= 0) return;

        this.screenWidth = width;    // 记录像素宽度
        this.screenHeight = height;  // 记录像素高度
        this.projectionMatrix = new Matrix4f().setOrtho(0, width, height, 0, -1.0f, 1.0f);

        // 开启构建
        if (!this.bufferBuilder.isBuilding()) {
            this.bufferBuilder.begin(uiLayout);
        }

        this.matrixStack.clear();
        this.matrixStack.push(new Matrix4f().identity());

        Material rootMaterial = materialStack.firstElement();
        this.materialStack.clear();
        this.materialStack.push(rootMaterial);
        this.uiMaterial = rootMaterial;
    }

    public void pushMaterial(Material newMaterial) {
        if (newMaterial == null || this.uiMaterial == newMaterial) {
            materialStack.push(this.uiMaterial);
            return;
        }

        // 切换材质前，如果 buffer 有数据，必须先画掉
        if (bufferBuilder.getVertexCount() > 0) {
            flushBatch();
        }

        this.uiMaterial = newMaterial;
        materialStack.push(newMaterial);
    }

    /**
     * 在 UI 空间渲染文字
     * 自动处理材质切换与矩阵变换同步
     */
    public void drawText(String text, float x, float y, Color color) {
        if (text == null || text.isEmpty()) return;
        com.zenith.render.Font font = getFont();
        com.zenith.render.TextRenderer textRenderer = getTextRenderer();
        if (font == null || textRenderer == null) return;
        if (bufferBuilder.getVertexCount() > 0) {
            flushBatch();
        }
        com.zenith.render.Shader shader = null;
        if (textRenderer instanceof com.zenith.render.backend.opengl.GLTextRenderer glTextRenderer) {
            shader = glTextRenderer.getShader();
        } else {
            shader = uiMaterial.getShader();
        }

        if (shader instanceof com.zenith.render.backend.opengl.shader.UITextShader textShader) {
            textShader.bind();
            textShader.setUniform("u_Projection", projectionMatrix);
            textShader.setUniform("u_Texture", 0);
            textShader.setUniform("u_TextColor", color);
            textShader.setUseTexture(true);
        }
        org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_BLEND);
        org.lwjgl.opengl.GL11.glBlendFunc(org.lwjgl.opengl.GL11.GL_SRC_ALPHA, org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA);
        org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);
        Matrix4f model = matrixStack.peek();
        org.joml.Vector3f pos = model.transformPosition(new org.joml.Vector3f(x, y, 0), new org.joml.Vector3f());
        textRenderer.begin();
        if (font.getTexture() != null) {
            font.getTexture().bind(0);
        }
        textRenderer.drawString(text, pos.x, pos.y, font, color);
        textRenderer.end();
        if (!bufferBuilder.isBuilding()) {
            bufferBuilder.begin(uiLayout);
        }
    }

    /**
     * 简化版：按原始大小渲染图标
     */
    public void drawSprite(TextureAtlas atlas, String spriteName, float x, float y, Color color) {
        TextureAtlas.SpriteRegion region = atlas.getRegion(spriteName);
        if (region != null) {
            drawSprite(atlas, spriteName, x, y, region.w, region.h, color);
        }
    }

    /**
     * 使用图集渲染指定的图标
     * @param atlas 解析后的图集对象
     * @param spriteName XML 中的名称，例如 "gear.png"
     * @param x 屏幕位置 X
     * @param y 屏幕位置 Y
     * @param w 渲染宽度 (通常设为 region.w)
     * @param h 渲染高度 (通常设为 region.h)
     * @param color 颜色过滤（通过白色图标乘法实现变色）
     */
    public void drawSprite(TextureAtlas atlas, String spriteName, float x, float y, float w, float h, Color color) {
        TextureAtlas.SpriteRegion region = atlas.getRegion(spriteName);
        if (region == null) return;
        bindTexture(atlas.getTexture());
        drawTextureRect(
                x, y, w, h,
                region.u, region.v,
                region.uw, region.uh,
                color
        );
    }

    /**
     * 渲染带背景的 Tooltip 专用快捷方法
     */
    public void drawTooltip(String text, float mouseX, float mouseY) {
        if (text == null || text.isEmpty()) return;
        Font font = getFont();

        float padding = 8.0f;
        float textWidth = font.getWidth(text);
        float textHeight = font.getSize();

        // 偏移鼠标一点距离，防止遮挡鼠标指针
        float renderX = mouseX + 12.0f;
        float renderY = mouseY + 12.0f;

        // 1. 先画背景矩形
        // 注意：我们将背景色设置为全黑 0.9 透明度，增加厚度感
        drawRect(new Rectf(renderX, renderY, textWidth + padding * 2, textHeight + padding * 2),
                new Color(0, 0, 0, 0.9f));

        // 2. 画文字
        // 【已修复】：补上了遗漏的 textHeight * 0.8f 偏移，使文字坐标下压至基线位置
        // 如果文字依然偏下/偏上，可以微调 0.8f 这个系数
        drawText(text, renderX + padding, renderY + padding + textHeight * 0.8f, Color.WHITE);
    }


    public void popMaterial() {
        if (materialStack.size() <= 1) return;

        Material previous = materialStack.pop();
        Material current = materialStack.peek();

        if (previous != current) {
            // 回退材质前，如果 buffer 有数据，必须先画掉
            if (bufferBuilder.getVertexCount() > 0) {
                flushBatch();
            }
            this.uiMaterial = current;
        }
    }

    public void drawRect(Rectf bounds, Color color) {
        bindTexture(whiteTexture);

        if (bufferBuilder.getVertexCount() + 6 >= 4000) {
            flushBatch();
        }

        Matrix4f modelMatrix = matrixStack.peek();
        float x1 = bounds.getLeft();
        float y1 = bounds.getTop();
        float x2 = bounds.getRight();
        float y2 = bounds.getBottom();

        Vector3f v1 = modelMatrix.transformPosition(new Vector3f(x1, y1, 0), new Vector3f());
        Vector3f v2 = modelMatrix.transformPosition(new Vector3f(x1, y2, 0), new Vector3f());
        Vector3f v3 = modelMatrix.transformPosition(new Vector3f(x2, y2, 0), new Vector3f());
        Vector3f v4 = modelMatrix.transformPosition(new Vector3f(x2, y1, 0), new Vector3f());

        // UV 设置为 0~1，配合白色纹理
        emitVertex(v1, 0, 0, color);
        emitVertex(v2, 0, 1, color);
        emitVertex(v3, 1, 1, color);

        emitVertex(v1, 0, 0, color);
        emitVertex(v3, 1, 1, color);
        emitVertex(v4, 1, 0, color);
    }

    private void emitVertex(Vector3f pos, float u, float v, Color color) {
        bufferBuilder.putFloat(pos.x);
        bufferBuilder.putFloat(pos.y);
        bufferBuilder.putFloat(u);
        bufferBuilder.putFloat(v);
        bufferBuilder.putFloat(color.r);
        bufferBuilder.putFloat(color.g);
        bufferBuilder.putFloat(color.b);
        bufferBuilder.putFloat(color.a);
        bufferBuilder.endVertex();
    }

    public void pushTransform(float offsetX, float offsetY) {
        Matrix4f next = new Matrix4f(matrixStack.peek()).translate(offsetX, offsetY, 0);
        matrixStack.push(next);
    }

    public void pushTransform() {
        matrixStack.push(new Matrix4f(matrixStack.peek()));
    }

    public void translate(float x, float y) {
        matrixStack.peek().translate(x, y, 0);
    }

    public void scale(float sx, float sy) {
        matrixStack.peek().scale(sx, sy, 1.0f);
    }

    public void popTransform() {
        if (matrixStack.size() > 1) matrixStack.pop();
    }

    public void end() {
        if (bufferBuilder.isBuilding()) {
            if (bufferBuilder.getVertexCount() > 0) {
                flushBatch();
            }
            bufferBuilder.end();
        }
    }

    private void flushBatch() {
        GLBufferBuilder.RenderedBuffer rb = bufferBuilder.end();
        if (rb == null || rb.vertexCount() == 0) {
            bufferBuilder.begin(uiLayout);
            return;
        }
        ByteBuffer byteBuf = rb.data();
        FloatBuffer floatBuf = byteBuf.asFloatBuffer();
        float[] floatArray = new float[floatBuf.remaining()];
        floatBuf.get(floatArray);
        uiMesh.updateVertices(floatArray);
        if (uiMaterial.getShader() instanceof UIShader uiShader) {
            uiShader.bind();
            uiShader.setProjection(projectionMatrix);
            uiShader.setTextureSlot(0);
        }
        if (this.currentTexture != null) {
            this.currentTexture.bind(0);
        } else if (rb.texture() != null) {
            rb.texture().bind(0);
        }
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        uiMesh.bind();
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, rb.vertexCount());
        uiMesh.unbind();
        bufferBuilder.begin(uiLayout);
    }

    public GLBufferBuilder getBufferBuilder() {
        return this.bufferBuilder;
    }

    public void dispose() {
        if (uiMesh != null) uiMesh.dispose();
    }

    public Font getFont() {
        return uiMaterial.getFont();
    }

    public void setFont(Font font) {
        uiMaterial.setFont(font);
    }

    public TextRenderer getTextRenderer() {
        return uiMaterial.getTextRenderer();
    }

    /**
     * 绑定当前 UI 渲染批次使用的纹理
     */
    public void bindTexture(com.zenith.render.Texture texture) {
        if (this.currentTexture == texture) return;

        // 如果纹理发生变化，且当前 Buffer 中已经有顶点，必须先踢出（Flush）旧的批次
        if (bufferBuilder.getVertexCount() > 0) {
            flushBatch();
        }
        this.currentTexture = texture;
    }

    /**
     * 重载 drawRect，支持自定义 UV 坐标（用于文本或图集渲染）
     */
    public void drawTextureRect(float x, float y, float w, float h, float u, float v, float uw, float uh, Color color) {
        if (bufferBuilder.getVertexCount() + 6 >= 4000) {
            flushBatch();
        }

        Matrix4f modelMatrix = matrixStack.peek();

        // 计算四个角的局部坐标
        Vector3f v1 = modelMatrix.transformPosition(new Vector3f(x, y, 0), new Vector3f());
        Vector3f v2 = modelMatrix.transformPosition(new Vector3f(x, y + h, 0), new Vector3f());
        Vector3f v3 = modelMatrix.transformPosition(new Vector3f(x + w, y + h, 0), new Vector3f());
        Vector3f v4 = modelMatrix.transformPosition(new Vector3f(x + w, y, 0), new Vector3f());

        // 绘制两个三角形（注意 UV 映射）
        emitVertex(v1, u, v, color);
        emitVertex(v2, u, v + uh, color);
        emitVertex(v3, u + uw, v + uh, color);

        emitVertex(v1, u, v, color);
        emitVertex(v3, u + uw, v + uh, color);
        emitVertex(v4, u + uw, v, color);
    }

    public Matrix4f getProjectionMatrix() {
        return this.projectionMatrix;
    }

    public float getScreenWidth() { return screenWidth; }
    public float getScreenHeight() { return screenHeight; }
}