package com.zenith.ui.render.shader;

import com.zenith.render.backend.opengl.shader.GLShader;
import org.joml.Matrix4f;

public class UIShader extends GLShader {
    private static final String SHADER_NAME = "UI_Shader";

    private static final String VERTEX_SOURCE =
            "#version 330 core\n" +
                    "layout (location = 0) in vec2 aPos;\n" +
                    "layout (location = 1) in vec2 aTexCoords;\n" +
                    "layout (location = 2) in vec4 aColor;\n" +
                    "\n" +
                    "out vec2 TexCoords;\n" +
                    "out vec4 Color;\n" +
                    "\n" +
                    "uniform mat4 u_Projection;\n" + // 建议加上 u_ 前缀区分 Uniform
                    "\n" +
                    "void main() {\n" +
                    "    TexCoords = aTexCoords;\n" +
                    "    Color = aColor;\n" +
                    "    gl_Position = u_Projection * vec4(aPos, 0.0, 1.0);\n" +
                    "}";

    private static final String FRAGMENT_SOURCE =
            "#version 330 core\n" +
                    "in vec2 TexCoords;\n" +
                    "in vec4 Color;\n" +
                    "out vec4 fragColor;\n" +
                    "\n" +
                    "uniform sampler2D u_Texture;\n" +
                    "\n" +
                    "void main() {\n" +
                    "    fragColor = Color * texture(u_Texture, TexCoords);\n" +
                    "}";

    public UIShader() {
        super(SHADER_NAME, VERTEX_SOURCE, FRAGMENT_SOURCE);
    }

    protected UIShader(String name, String vertexSource, String fragmentSource) {
        super(name, vertexSource, fragmentSource);
    }

    /**
     * 设置 UI 正交投影矩阵
     * 现在直接使用 JOML 的 Matrix4f，匹配 GLShader 的 setUniform(String, Matrix4f)
     */
    public void setProjection(Matrix4f projection) {
        this.setUniform("u_Projection", projection);
    }

    /**
     * 设置纹理槽位（通常 UI 默认使用 0 号槽位）
     */
    public void setTextureSlot(int slot) {
        this.setUniform("u_Texture", (float) slot);
    }
}