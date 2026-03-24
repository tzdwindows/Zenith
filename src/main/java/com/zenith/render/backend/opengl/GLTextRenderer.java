package com.zenith.render.backend.opengl;

import com.zenith.common.math.Color;
import com.zenith.render.*;
import com.zenith.render.backend.opengl.buffer.GLBufferBuilder;
import com.zenith.render.backend.opengl.buffer.GLVertexBuffer;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13C.glActiveTexture;

public class GLTextRenderer extends TextRenderer {

    private final GLVertexBuffer vertexBuffer;
    private final GLBufferBuilder builder;
    private final Shader textShader;
    private final VertexLayout layout;
    private Font currentFont = null;
    private boolean isBuilding = false;

    public GLTextRenderer(Shader shader) {
        this.textShader = shader;
        this.layout = new VertexLayout();
        this.layout.pushFloat("aPos", 3);    // loc 0
        this.layout.pushFloat("aNormal", 3); // loc 1 (必须补上，填 0, 0, 0)
        this.layout.pushFloat("aTex", 2);    // loc 2
        this.layout.pushFloat("aColor", 4);  // loc 3
        this.builder = new GLBufferBuilder(1024 * 1024); // 增加容量
        this.vertexBuffer = new GLVertexBuffer();
    }

    @Override
    public void begin() {
        if (isBuilding) flush();
        isBuilding = true;
        builder.begin(layout);

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDisable(GL_CULL_FACE);
        textShader.bind();
    }

    @Override
    public void drawString3D(String text, Vector3f position, Quaternionf rotation, Camera camera, Font font, Color color, float thickness) {
        if (text == null || font == null || camera == null) return;

        flush();
        this.currentFont = font;

        glEnable(GL_DEPTH_TEST);
        glDepthMask(true);
        glDepthFunc(GL_LEQUAL);

        Matrix4f vp = new Matrix4f(camera.getProjectionMatrix()).mul(camera.getViewMatrix());
        textShader.setUniform("u_ViewProjection", vp);
        if (textShader.hasUniform("u_Model")) {
            textShader.setUniform("u_Model", new Matrix4f());
        }

        this.isBuilding = true;
        builder.begin(layout);

        float scale = 0.015f;
        float step = scale * 0.1f;
        int layers = (int) Math.max(1, thickness / step);
        float actualStep = thickness / layers;

        Vector3f camPos = camera.getTransform().getPosition();

        // --- 关键修改：将 localBack 定义为 (0, 0, -1) ---
        // 这样 offset 为正时，厚度是往文字的“背面”（屏幕深处）长的
        Vector3f localBack = new Vector3f(0, 0, -1).rotate(rotation).normalize();

        class LayerInfo {
            float offset;
            float distance;
            Color col;
        }
        java.util.List<LayerInfo> sortedLayers = new java.util.ArrayList<>();

        // 侧面颜色 (稍微调亮一点，防止太黑)
        Color sideColor = new Color(color.r * 0.5f, color.g * 0.5f, color.b * 0.5f, color.a);

        // 添加所有厚度层
        for (int i = 1; i <= layers; i++) {
            LayerInfo l = new LayerInfo();
            l.offset = i * actualStep;
            l.col = sideColor;
            Vector3f layerWorldPos = new Vector3f(position).add(new Vector3f(localBack).mul(l.offset));
            l.distance = layerWorldPos.distanceSquared(camPos);
            sortedLayers.add(l);
        }

        // 添加正面层 (offset 为 0)
        LayerInfo face = new LayerInfo();
        face.offset = 0;
        face.col = color;
        face.distance = position.distanceSquared(camPos);
        sortedLayers.add(face);

        // 排序：从远到近
        sortedLayers.sort((a, b) -> Float.compare(b.distance, a.distance));

        // 绘制
        for (LayerInfo l : sortedLayers) {
            // 给 offset 加一个极小的 Z 偏移增量，彻底解决 Z-Fighting
            Vector3f layerPos = new Vector3f(position).add(new Vector3f(localBack).mul(l.offset));

            Matrix4f layerModel = new Matrix4f()
                    .translate(layerPos)
                    .rotate(rotation)
                    .scale(scale);

            fillBufferWithModel(text, layerModel, l.col);
        }

        flush();
        glDisable(GL_DEPTH_TEST);

        this.isBuilding = true;
        builder.begin(layout);
    }


    /**
     * 辅助方法：提交一层的顶点数据
     */
    private void drawSingleLayer(String text, Vector3f basePos, Quaternionf rot, Vector3f backDir, float offset, float scale, Color c) {
        // 往 backDir 方向偏移。offset=0 是最明亮的正面。
        Vector3f pos = new Vector3f(basePos).add(new Vector3f(backDir).mul(offset));
        Matrix4f model = new Matrix4f().translate(pos).rotate(rot).scale(scale);
        fillBufferWithModel(text, model, c);
    }

    @Override
    public void drawString3D(String text, Vector3f position, Camera camera, Font font, Color color, float thickness) {
        Quaternionf camRot = new Quaternionf();
        camera.getViewMatrix().invert().getUnnormalizedRotation(camRot);
        drawString3D(text, position, camRot, camera, font, color, thickness);
    }

    @Override
    public void drawString(String text, float x, float y, Font font, Color color) {
        if (!isBuilding || (currentFont != null && currentFont != font)) {
            flush();
            isBuilding = true;
            builder.begin(layout);
        }

        float savedLightCount = 0.0f;
        boolean hasLightCount = textShader.hasUniform("u_LightCount");
        if (hasLightCount) {
            savedLightCount = textShader.getUniformFloat("u_LightCount");
            textShader.setUniform("u_LightCount", 0.0f);
        }

        this.currentFont = font;
        if (textShader.hasUniform("u_Model")) {
            textShader.setUniform("u_Model", new Matrix4f());
        }

        fillBuffer(text, x, y, font, color);
        if (hasLightCount) {
            textShader.setUniform("u_LightCount", savedLightCount);
        }
    }

    private void fillBuffer(String text, float x, float y, Font font, Color color) {
        float curX = x;
        for (char c : text.toCharArray()) {
            Font.Glyph g = font.getGlyph(c);
            if (g == null) continue;
            float x1 = curX + g.offsetX;
            float y1 = y + g.offsetY;
            float x2 = x1 + g.width;
            float y2 = y1 + g.height;

            putVertex2D(x1, y2, g.u,  g.v2, color);
            putVertex2D(x2, y2, g.u2, g.v2, color);
            putVertex2D(x2, y1, g.u2, g.v,  color);
            putVertex2D(x1, y2, g.u,  g.v2, color);
            putVertex2D(x2, y1, g.u2, g.v,  color);
            putVertex2D(x1, y1, g.u,  g.v,  color);
            curX += g.advance;
        }
    }

    private void putVertex2D(float x, float y, float u, float v, Color c) {
        builder.putFloat(x).putFloat(y).putFloat(0.0f)      // aPos (3)
                .putFloat(0.0f).putFloat(0.0f).putFloat(0.0f) // aNormal (3) - 必须补上！
                .putFloat(u).putFloat(v)                    // aTex (2)
                .putFloat(c.r).putFloat(c.g).putFloat(c.b).putFloat(c.a) // aColor (4)
                .endVertex();
    }

    private void fillBufferWithModel(String text, Matrix4f model, Color color) {
        float curX = 0;
        for (char c : text.toCharArray()) {
            Font.Glyph g = currentFont.getGlyph(c);
            if (g == null) continue;

            float x1 = curX + g.offsetX;
            float y1 = -g.offsetY;
            float x2 = x1 + g.width;
            float y2 = y1 - g.height;

            // CCW 绕序
            putVertex(model, x1, y2, g.u,  g.v2, color);
            putVertex(model, x2, y2, g.u2, g.v2, color);
            putVertex(model, x2, y1, g.u2, g.v,  color);
            putVertex(model, x1, y2, g.u,  g.v2, color);
            putVertex(model, x2, y1, g.u2, g.v,  color);
            putVertex(model, x1, y1, g.u,  g.v,  color);

            curX += g.advance;
        }
    }

    private void putVertex(Matrix4f m, float x, float y, float u, float v, Color c) {
        Vector4f pos = new Vector4f(x, y, 0, 1.0f);
        m.transform(pos);
        builder.putFloat(pos.x).putFloat(pos.y).putFloat(pos.z)
                .putFloat(0.0f).putFloat(0.0f).putFloat(0.0f) // 补上法线
                .putFloat(u).putFloat(v)
                .putFloat(c.r).putFloat(c.g).putFloat(c.b).putFloat(c.a)
                .endVertex();
    }


    @Override
    public void end() {
        flush();
        isBuilding = false;
        glDisable(GL_BLEND);
        textShader.unbind();
    }

    private void flush() {
        if (!isBuilding) return;
        GLBufferBuilder.RenderedBuffer rendered = builder.end();
        if (rendered.vertexCount() == 0) { isBuilding = false; return; }

        glActiveTexture(GL_TEXTURE0);
        if (currentFont != null) currentFont.getTexture().bind(0);
        textShader.setUniform("u_Texture", 0);

        vertexBuffer.upload(rendered);
        vertexBuffer.draw();
        isBuilding = false;
    }

    public com.zenith.render.Shader getShader() {
        return this.textShader;
    }
}