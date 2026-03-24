package com.zenith.render;

import com.zenith.common.math.Color;
import com.zenith.common.utils.InternalLogger;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Shader 抽象类，负责管理 GPU 上的着色器程序。
 */
public abstract class Shader {
    protected String name;

    protected Shader(String name) {
        this.name = name;
        InternalLogger.info("Creating Shader: " + name);
    }

    public abstract void bind();
    public abstract void unbind();

    // 统一变量 (Uniforms) 上传接口
    public abstract void setUniform(String name, Color color);
    public abstract void setUniform(String name, Vector2f vector2f);
    public abstract void setUniform(String name, Matrix4f matrix);
    public abstract void setUniform(String name, Vector3f vector);
    public abstract void setUniform(String name, Vector4f vector);
    public abstract void setUniform(String name, float value);
    public abstract void setUniform(String name, boolean value);
    public abstract boolean hasUniform(String uLightCount);

    public abstract float getUniformFloat(String name);
    public abstract void dispose();
}