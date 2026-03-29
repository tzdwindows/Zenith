package com.zenith.render.backend.opengl;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

public class SceneFramebuffer {

    private int fbo;
    private int colorTex;
    private int depthTex;
    private int colorCopyTex;
    private int depthCopyTex;
    private int width, height;

    // ====== 用于 renderToScreen 的全屏 quad ======
    private int quadVao = 0;
    private int quadVbo = 0;
    private int screenProgram = 0;

    public SceneFramebuffer(int width, int height) {
        this.width = width;
        this.height = height;
        init();
    }

    private void init() {
        fbo = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);

        // -------- Color --------
        colorTex = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, colorTex);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA16F, width, height, 0, GL_RGBA, GL_FLOAT, 0);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, colorTex, 0);

        // -------- Depth --------
        depthTex = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, depthTex);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_DEPTH_COMPONENT32F, width, height, 0, GL_DEPTH_COMPONENT, GL_FLOAT, 0);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, depthTex, 0);

        int status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
        if (status != GL_FRAMEBUFFER_COMPLETE) {
            throw new RuntimeException("FBO not complete, status=" + status);
        }

        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    public void bind() {
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
    }

    public void unbind() {
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    public int getColorTex() {
        return colorTex;
    }

    public int getDepthTex() {
        return depthTex;
    }

    public void renderToScreen() {
        ensureScreenResources();

        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);

        glUseProgram(screenProgram);

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, colorTex);
        int loc = glGetUniformLocation(screenProgram, "u_Texture");
        glUniform1i(loc, 0);

        glBindVertexArray(quadVao);
        glDrawArrays(GL_TRIANGLES, 0, 6);
        glBindVertexArray(0);

        glUseProgram(0);

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
    }

    private void ensureScreenResources() {
        if (screenProgram == 0) {
            screenProgram = createScreenProgram();
        }
        if (quadVao == 0) {
            createQuad();
        }
    }

    private void createQuad() {
        float[] quadVertices = {
                // pos      // uv
                -1f,  1f,   0f, 1f,
                -1f, -1f,   0f, 0f,
                1f, -1f,   1f, 0f,

                -1f,  1f,   0f, 1f,
                1f, -1f,   1f, 0f,
                1f,  1f,   1f, 1f
        };

        quadVao = glGenVertexArrays();
        quadVbo = glGenBuffers();

        glBindVertexArray(quadVao);
        glBindBuffer(GL_ARRAY_BUFFER, quadVbo);
        glBufferData(GL_ARRAY_BUFFER, quadVertices, GL_STATIC_DRAW);

        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 4 * Float.BYTES, 0L);

        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 4 * Float.BYTES, 2L * Float.BYTES);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
    }

    private int createScreenProgram() {
        String vs = """
                #version 330 core
                layout (location = 0) in vec2 aPos;
                layout (location = 1) in vec2 aTexCoord;

                out vec2 vTexCoord;

                void main() {
                    vTexCoord = aTexCoord;
                    gl_Position = vec4(aPos, 0.0, 1.0);
                }
                """;

        String fs = """
                #version 330 core
                in vec2 vTexCoord;
                out vec4 FragColor;

                uniform sampler2D u_Texture;

                void main() {
                    FragColor = texture(u_Texture, vTexCoord);
                }
                """;

        int vertexShader = glCreateShader(GL_VERTEX_SHADER);
        glShaderSource(vertexShader, vs);
        glCompileShader(vertexShader);
        checkShader(vertexShader, "SCREEN_VERTEX");

        int fragmentShader = glCreateShader(GL_FRAGMENT_SHADER);
        glShaderSource(fragmentShader, fs);
        glCompileShader(fragmentShader);
        checkShader(fragmentShader, "SCREEN_FRAGMENT");

        int program = glCreateProgram();
        glAttachShader(program, vertexShader);
        glAttachShader(program, fragmentShader);
        glLinkProgram(program);

        if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE) {
            String log = glGetProgramInfoLog(program);
            glDeleteShader(vertexShader);
            glDeleteShader(fragmentShader);
            glDeleteProgram(program);
            throw new RuntimeException("Screen program link failed:\n" + log);
        }

        glDeleteShader(vertexShader);
        glDeleteShader(fragmentShader);

        return program;
    }

    private void checkShader(int shader, String name) {
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            String log = glGetShaderInfoLog(shader);
            glDeleteShader(shader);
            throw new RuntimeException(name + " compile failed:\n" + log);
        }
    }

    public void dispose() {
        if (screenProgram != 0) {
            glDeleteProgram(screenProgram);
            screenProgram = 0;
        }
        if (quadVbo != 0) {
            glDeleteBuffers(quadVbo);
            quadVbo = 0;
        }
        if (quadVao != 0) {
            glDeleteVertexArrays(quadVao);
            quadVao = 0;
        }
        if (colorTex != 0) {
            glDeleteTextures(colorTex);
            colorTex = 0;
        }
        if (depthTex != 0) {
            glDeleteTextures(depthTex);
            depthTex = 0;
        }
        if (fbo != 0) {
            glDeleteFramebuffers(fbo);
            fbo = 0;
        }
    }

    public void copyToHistory() {
        glBindFramebuffer(GL_READ_FRAMEBUFFER, fbo);

        int copyFbo = glGenFramebuffers();
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, copyFbo);

        // 颜色复制
        glBindTexture(GL_TEXTURE_2D, colorCopyTex);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA16F, width, height, 0, GL_RGBA, GL_FLOAT, (java.nio.ByteBuffer) null);
        glFramebufferTexture2D(GL_DRAW_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, colorCopyTex, 0);
        glReadBuffer(GL_COLOR_ATTACHMENT0);
        glDrawBuffer(GL_COLOR_ATTACHMENT0);
        glBlitFramebuffer(0, 0, width, height, 0, 0, width, height, GL_COLOR_BUFFER_BIT, GL_NEAREST);

        // 深度复制
        glBindTexture(GL_TEXTURE_2D, depthCopyTex);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_DEPTH_COMPONENT32F, width, height, 0, GL_DEPTH_COMPONENT, GL_FLOAT, (java.nio.ByteBuffer) null);
        glFramebufferTexture2D(GL_DRAW_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, depthCopyTex, 0);
        glBlitFramebuffer(0, 0, width, height, 0, 0, width, height, GL_DEPTH_BUFFER_BIT, GL_NEAREST);

        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glDeleteFramebuffers(copyFbo);
    }

    public int getColorCopyTex() { return colorCopyTex; }
    public int getDepthCopyTex() { return depthCopyTex; }
}