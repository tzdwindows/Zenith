package com.zenith.render.backend.opengl.shader;

import com.zenith.common.math.Color;
import com.zenith.common.utils.InternalLogger;
import com.zenith.render.Shader;
import org.joml.*;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL43.*;

public class GLComputeShader extends Shader {
    private final int rendererID;
    private final Map<String, Integer> uniformLocationCache = new HashMap<>();
    private static final FloatBuffer matrixBuffer = BufferUtils.createFloatBuffer(16);

    /**
     * 构造函数：支持通过 Preprocessor 处理源码
     */
    public GLComputeShader(String name, String source, Map<String, String> defines) {
        super(name);

        int shader = glCreateShader(GL_COMPUTE_SHADER);
        // 使用 ShaderPreprocessor 注入宏定义并处理 #include
        String processedSource = ShaderPreprocessor.process(source, defines);
        glShaderSource(shader, processedSource);
        glCompileShader(shader);

        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            InternalLogger.error("Compute Shader [" + name + "] 编译失败: \n" + glGetShaderInfoLog(shader));
        }

        this.rendererID = glCreateProgram();
        glAttachShader(rendererID, shader);
        glLinkProgram(rendererID);

        if (glGetProgrami(rendererID, GL_LINK_STATUS) == GL_FALSE) {
            InternalLogger.error("Compute Shader [" + name + "] 链接失败: " + glGetProgramInfoLog(rendererID));
        }

        glDeleteShader(shader);
    }

    /**
     * 核心辅助方法：解决“无法解析”问题的关键
     * 必须定义在类内部，以便 setUniform 方法调用
     */
    private int getUniformLocation(String paramName) {
        if (uniformLocationCache.containsKey(paramName)) {
            return uniformLocationCache.get(paramName);
        }
        int location = glGetUniformLocation(rendererID, paramName);
        uniformLocationCache.put(paramName, location);
        return location;
    }

    /**
     * 绑定 Image 纹理，用于路径追踪输出
     */
    public void bindImage(int unit, int textureID, int access) {
        // 默认使用 RGBA16F 格式，适配 Zenith 的 FBO 配置
        glBindImageTexture(unit, textureID, 0, false, 0, access, GL_RGBA16F);
    }

    public void dispatch(int width, int height) {
        bind();
        int numGroupsX = (width + 7) / 8;
        int numGroupsY = (height + 7) / 8;
        glDispatchCompute(numGroupsX, numGroupsY, 1);
        // 专门针对图像写入的内存屏障
        glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);
    }

    @Override public void bind() { glUseProgram(rendererID); }
    @Override public void unbind() { glUseProgram(0); }
    @Override public void dispose() { glDeleteProgram(rendererID); }

    // --- 实现 Shader 基类的所有抽象方法 ---

    @Override public boolean hasUniform(String name) { return getUniformLocation(name) != -1; }

    @Override public void setUniform(String n, Color c) { glUniform4f(getUniformLocation(n), c.r, c.g, c.b, c.a); }
    @Override public void setUniform(String n, Vector2f v) { glUniform2f(getUniformLocation(n), v.x, v.y); }
    @Override public void setUniform(String n, Vector3f v) { glUniform3f(getUniformLocation(n), v.x, v.y, v.z); }
    @Override public void setUniform(String n, Vector4f v) { glUniform4f(getUniformLocation(n), v.x, v.y, v.z, v.w); }
    @Override public void setUniform(String n, float v) { glUniform1f(getUniformLocation(n), v); }
    @Override public void setUniform(String n, int v) { glUniform1i(getUniformLocation(n), v); }
    @Override public void setUniform(String n, boolean v) { glUniform1i(getUniformLocation(n), v ? 1 : 0); }

    // 额外支持 int，对路径追踪的计数器至关重要


    @Override
    public void setUniform(String n, Matrix4f m) {
        m.get(matrixBuffer);
        glUniformMatrix4fv(getUniformLocation(n), false, matrixBuffer);
    }

    @Override
    public float getUniformFloat(String name) {
        float[] params = new float[1];
        glGetUniformfv(rendererID, getUniformLocation(name), params);
        return params[0];
    }

    public int getRendererID() { return rendererID; }
}
