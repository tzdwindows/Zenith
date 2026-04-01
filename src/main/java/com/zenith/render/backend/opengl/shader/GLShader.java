package com.zenith.render.backend.opengl.shader;

import com.zenith.asset.AssetResource;
import com.zenith.common.math.Color;
import com.zenith.common.utils.InternalLogger;
import com.zenith.render.Shader;
import com.zenith.render.backend.opengl.LightManager;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL20.*;

public class GLShader extends Shader {
    private final int rendererID;
    private final Map<String, Integer> uniformLocationCache = new HashMap<>();
    private static final FloatBuffer matrixBuffer = BufferUtils.createFloatBuffer(16);

    public GLShader(String name, String vertexSource, String fragmentSource, AssetResource... includePath) {
        super(name);
        for (AssetResource s : includePath) {
            addInclude(s);
        }
        String processedVertexSource = ShaderPreprocessor.process(vertexSource);
        String processedFragmentSource = ShaderPreprocessor.process(fragmentSource);

        int vertexShader = compileShader(GL_VERTEX_SHADER, processedVertexSource);
        int fragmentShader = compileShader(GL_FRAGMENT_SHADER, processedFragmentSource);

        rendererID = glCreateProgram();
        glAttachShader(rendererID, vertexShader);
        glAttachShader(rendererID, fragmentShader);
        glLinkProgram(rendererID);

        if (glGetProgrami(rendererID, GL_LINK_STATUS) == GL_FALSE) {
            String log = glGetProgramInfoLog(rendererID);
            InternalLogger.error(String.format("Shader [%s] link failed: %s", name, log));
        }

        glDeleteShader(vertexShader);
        glDeleteShader(fragmentShader);
        InternalLogger.info(String.format("GLShader [%s] linked successfully (ID: %d)", name, rendererID));
    }

    protected final void addInclude(AssetResource includePath) {
        ShaderPreprocessor.addIncludeRoot(includePath);
    }

    private int compileShader(int type, String source) {
        int id = glCreateShader(type);
        glShaderSource(id, source);
        glCompileShader(id);

        if (glGetShaderi(id, GL_COMPILE_STATUS) == GL_FALSE) {
            String log = glGetShaderInfoLog(id);
            InternalLogger.error(String.format("Shader [%s] %s compile error: %s",
                    name, (type == GL_VERTEX_SHADER ? "Vertex" : "Fragment"), log));
            return 0;
        }
        return id;
    }

    public boolean hasUniform(String name) {
        return checkUniformLocation(name) != -1;
    }

    private int checkUniformLocation(String name) {
        return uniformLocationCache.computeIfAbsent(name, k -> glGetUniformLocation(rendererID, k));
    }

    @Override
    public void bind() {
        glUseProgram(rendererID);
    }

    @Override
    public void unbind() {
        glUseProgram(0);
    }

    @Override
    public void dispose() {
        glDeleteProgram(rendererID);
        InternalLogger.info("Shader disposed: " + name);
    }

    @Override
    public void setUniform(String name, Color color) {
        glUniform4f(getUniformLocation(name), color.r, color.g, color.b, color.a);
    }

    @Override
    public void setUniform(String name, Vector2f vector2f) {
        glUniform2f(getUniformLocation(name), vector2f.x, vector2f.y);
    }

    @Override
    public void setUniform(String name, Matrix4f matrix) {
        matrix.get(matrixBuffer);
        glUniformMatrix4fv(getUniformLocation(name), false, matrixBuffer);
    }

    @Override
    public void setUniform(String name, Vector3f vector) {
        glUniform3f(getUniformLocation(name), vector.x, vector.y, vector.z);
    }

    @Override
    public void setUniform(String name, Vector4f vector) {
        glUniform4f(getUniformLocation(name), vector.x, vector.y, vector.z, vector.w);
    }

    @Override
    public void setUniform(String name, float value) {
        glUniform1f(getUniformLocation(name), value);
    }

    @Override
    public void setUniform(String name, boolean value) {
        glUniform1i(getUniformLocation(name), value ? 1 : 0);
    }

    @Override
    public float getUniformFloat(String name) {
        int location = getUniformLocation(name);
        if (location == -1) return -1.0f;
        FloatBuffer fb = BufferUtils.createFloatBuffer(1);
        glGetUniformfv(this.rendererID, location, fb);
        return fb.get(0);
    }

    private int getUniformLocation(String name) {
        int location = checkUniformLocation(name);
        if (location == -1) {
            //InternalLogger.warn(String.format("Uniform '%s' not found or unused in shader [%s]", name, this.name));
        }
        return location;
    }

    public int getRendererID_Internal() {
        return rendererID;
    }
}