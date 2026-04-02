package com.zenith.render.backend.opengl.shader;

import com.zenith.asset.AssetResource;
import com.zenith.common.math.Color;
import com.zenith.common.utils.InternalLogger;
import com.zenith.render.Shader;
import org.joml.*;
import org.lwjgl.BufferUtils;

import java.io.IOException;
import java.nio.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL41.*;

public class GLShader extends Shader {

    protected int rendererID;
    private final Map<String, Integer> uniformLocationCache = new HashMap<>();
    private static final FloatBuffer matrixBuffer = BufferUtils.createFloatBuffer(16);
    private static final String CACHE_DIR = "ShaderCache";

    public GLShader(String name, String vertexSource, String fragmentSource, AssetResource... includePath) {
        super(name);

        for (AssetResource s : includePath) {
            addInclude(s);
        }

        try {
            Files.createDirectories(Paths.get(CACHE_DIR));
        } catch (IOException e) {
            InternalLogger.error("无法创建缓存目录: " + CACHE_DIR);
        }

        compileAndLink(vertexSource, fragmentSource);
    }

    /**
     * ⭐ GLSL + Program Binary 缓存方案
     */
    protected void compileAndLink(String vertexSource, String fragmentSource) {

        String vs = ShaderPreprocessor.process(vertexSource);
        String fs = ShaderPreprocessor.process(fragmentSource);

        String hash = getMd5(vs + fs);
        Path binaryPath = Paths.get(CACHE_DIR, name + "_" + hash + ".bin");
        Path formatPath = Paths.get(CACHE_DIR, name + "_" + hash + ".fmt");

        // ===== 1. 尝试加载 binary =====
        if (Files.exists(binaryPath) && Files.exists(formatPath)) {
            int program = loadProgramBinary(binaryPath, formatPath);
            if (program != 0) {
                rendererID = program;
                InternalLogger.info("Shader [" + name + "] 从缓存加载成功");
                return;
            }
        }

        // ===== 2. 编译 GLSL =====
        int vertexShader = glCreateShader(GL_VERTEX_SHADER);
        glShaderSource(vertexShader, vs);
        glCompileShader(vertexShader);
        checkCompileErrors(vertexShader, "VERTEX");

        int fragmentShader = glCreateShader(GL_FRAGMENT_SHADER);
        glShaderSource(fragmentShader, fs);
        glCompileShader(fragmentShader);
        checkCompileErrors(fragmentShader, "FRAGMENT");

        int program = glCreateProgram();

        // ⭐ 必须启用 binary 支持
        glProgramParameteri(program, GL_PROGRAM_BINARY_RETRIEVABLE_HINT, GL_TRUE);

        glAttachShader(program, vertexShader);
        glAttachShader(program, fragmentShader);
        glLinkProgram(program);

        if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE) {
            InternalLogger.error("Shader 链接失败 [" + name + "]: " + glGetProgramInfoLog(program));
            return;
        }

        glDeleteShader(vertexShader);
        glDeleteShader(fragmentShader);

        rendererID = program;

        // ===== 3. 保存 binary =====
        try {
            saveProgramBinary(program, binaryPath, formatPath);
        } catch (Exception e) {
            InternalLogger.warn("保存 Shader Binary 失败: " + e.getMessage());
        }

        InternalLogger.info("Shader [" + name + "] 编译完成并缓存");
    }

    /**
     * 保存 binary
     */
    private void saveProgramBinary(int program, Path path, Path formatPath) throws IOException {
        IntBuffer length = BufferUtils.createIntBuffer(1);
        IntBuffer format = BufferUtils.createIntBuffer(1);

        ByteBuffer binary = BufferUtils.createByteBuffer(1024 * 1024);

        glGetProgramBinary(program, length, format, binary);

        int size = length.get(0);
        binary.limit(size);

        byte[] data = new byte[size];
        binary.get(data);

        Files.write(path, data);
        Files.write(formatPath, new byte[]{
                (byte) (format.get(0) & 0xFF),
                (byte) ((format.get(0) >> 8) & 0xFF),
                (byte) ((format.get(0) >> 16) & 0xFF),
                (byte) ((format.get(0) >> 24) & 0xFF)
        });
    }

    /**
     * 加载 binary
     */
    private int loadProgramBinary(Path path, Path formatPath) {
        try {
            byte[] data = Files.readAllBytes(path);
            byte[] fmtBytes = Files.readAllBytes(formatPath);

            int format = (fmtBytes[0] & 0xFF)
                    | ((fmtBytes[1] & 0xFF) << 8)
                    | ((fmtBytes[2] & 0xFF) << 16)
                    | ((fmtBytes[3] & 0xFF) << 24);

            int program = glCreateProgram();

            ByteBuffer buffer = BufferUtils.createByteBuffer(data.length);
            buffer.put(data).flip();

            glProgramBinary(program, format, buffer);

            if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE) {
                glDeleteProgram(program);
                return 0;
            }

            return program;

        } catch (Exception e) {
            return 0;
        }
    }

    private void checkCompileErrors(int shader, String type) {
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            InternalLogger.error(type + " 编译错误: " + glGetShaderInfoLog(shader));
        }
    }

    public final void addInclude(AssetResource includePath) {
        ShaderPreprocessor.addIncludeRoot(includePath);
    }

    private String getMd5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return String.valueOf(input.hashCode());
        }
    }

    @Override public void bind() { glUseProgram(rendererID); }
    @Override public void unbind() { glUseProgram(0); }
    @Override public void dispose() { glDeleteProgram(rendererID); }

    @Override
    public boolean hasUniform(String name) {
        return getUniformLocation(name) != -1;
    }

    private int getUniformLocation(String n) {
        return uniformLocationCache.computeIfAbsent(n,
                k -> glGetUniformLocation(rendererID, k));
    }

    @Override public void setUniform(String n, Color c) {
        glUniform4f(getUniformLocation(n), c.r, c.g, c.b, c.a);
    }

    @Override public void setUniform(String n, Vector2f v) {
        glUniform2f(getUniformLocation(n), v.x, v.y);
    }

    @Override public void setUniform(String n, Matrix4f m) {
        m.get(matrixBuffer);
        glUniformMatrix4fv(getUniformLocation(n), false, matrixBuffer);
    }

    @Override public void setUniform(String n, Vector3f v) {
        glUniform3f(getUniformLocation(n), v.x, v.y, v.z);
    }

    @Override public void setUniform(String n, Vector4f v) {
        glUniform4f(getUniformLocation(n), v.x, v.y, v.z, v.w);
    }

    @Override public void setUniform(String n, float v) {
        glUniform1f(getUniformLocation(n), v);
    }

    @Override public void setUniform(String n, boolean v) {
        glUniform1i(getUniformLocation(n), v ? 1 : 0);
    }

    @Override
    public float getUniformFloat(String n) {
        int loc = getUniformLocation(n);
        if (loc == -1) return -1.0f;

        FloatBuffer fb = BufferUtils.createFloatBuffer(1);
        glGetUniformfv(rendererID, loc, fb);
        return fb.get(0);
    }

    public int getRendererID_Internal() {
        return rendererID;
    }
}