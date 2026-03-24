package com.zenith.render;

import com.zenith.common.math.Color;
import com.zenith.common.utils.InternalLogger;
import java.util.HashMap;
import java.util.Map;

/**
 * Material 封装了 Shader 及其对应的参数（纹理、颜色、数值）。
 * 它是连接几何体 (Mesh) 与着色器 (Shader) 的纽带。
 */
public abstract class Material {

    protected Shader shader;
    protected Color diffuseColor = Color.WHITE;
    protected Font font;
    protected Map<String, Texture> textures = new HashMap<>();
    protected TextRenderer textRenderer;

    protected Material(Shader shader) {
        if (shader == null) {
            InternalLogger.error("Material: Cannot create a material with a null shader!");
        }
        this.shader = shader;
    }

    public void setShader(Shader shader) {
        this.shader = shader;
    }

    /**
     * 将材质的所有属性（Uniforms）提交给 GPU。
     * 在绘制 Mesh 之前必须调用此方法。
     */
    public abstract void apply();

    /* -------------------------------------------------------------------------- */
    /* Setter & Getter                                                            */
    /* -------------------------------------------------------------------------- */

    public void setTexture(String name, Texture texture) {
        if (texture == null) {
            InternalLogger.warn("Material: Setting null texture for unit: " + name);
        }
        textures.put(name, texture);
    }

    /**
     * 释放材质引用的资源。
     */
    public abstract void dispose();



    public Texture getTexture(String name) {
        return textures.get(name);
    }

    public void setColor(Color color) {
        this.diffuseColor = color;
    }

    public Color getColor() {
        return diffuseColor;
    }

    public Shader getShader() {
        return shader;
    }

    public Font getFont() {
        return font;
    }

    public TextRenderer getTextRenderer() {
        return textRenderer;
    }

    public void setFont(Font font) {
        this.font = font;
    }
}