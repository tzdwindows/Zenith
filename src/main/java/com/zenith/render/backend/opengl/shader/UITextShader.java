package com.zenith.render.backend.opengl.shader;

import com.zenith.common.math.Color;
import org.joml.Matrix4f;

public class UITextShader extends GLShader {

    private static final String VERTEX_SRC =
            "#version 330 core\n" +
                    "layout (location = 0) in vec3 aPos;\n" +      // 配合 GLTextRenderer：必须是 vec3
                    "layout (location = 1) in vec3 aNormal;\n" +   // 配合 GLTextRenderer：法线占位
                    "layout (location = 2) in vec2 aTexCoords;\n" +// 配合 GLTextRenderer：纹理坐标
                    "layout (location = 3) in vec4 aColor;\n" +    // 配合 GLTextRenderer：顶点颜色
                    "\n" +
                    "out vec2 vTexCoord;\n" +  // 【修复】：变量名必须与 Fragment Shader 一致
                    "out vec4 vColor;\n" +
                    "\n" +
                    "uniform mat4 u_Projection;\n" +
                    "\n" +
                    "void main() {\n" +
                    "    vTexCoord = aTexCoords;\n" +
                    "    vColor = aColor;\n" +
                    "    gl_Position = u_Projection * vec4(aPos, 1.0);\n" +
                    "}";

    private static final String FRAGMENT_SRC =
            "#version 330 core\n" +
                    "in vec2 vTexCoord;\n" +
                    "in vec4 vColor;\n" +
                    "out vec4 FragColor;\n" +
                    "\n" +
                    "uniform sampler2D u_Texture;\n" +
                    "uniform vec4 u_TextColor;\n" +
                    "uniform float u_UseTexture;\n" +
                    "\n" +
                    "void main() {\n" +
                    "    vec4 texColor = vec4(1.0);\n" +
                    "    if (u_UseTexture > 0.5) {\n" +
                    "        texColor = texture(u_Texture, vTexCoord);\n" +
                    "    }\n" +
                    "\n" +
                    "    FragColor = vColor * u_TextColor * vec4(1.0, 1.0, 1.0, texColor.a);\n" +
                    "    \n" +
                    "    if (FragColor.a < 0.001) discard;\n" +
                    "}";

    public UITextShader() {
        super("UITextShader", VERTEX_SRC, FRAGMENT_SRC);
    }

    public void setUseTexture(boolean use) {
        this.bind();
        this.setUniform("u_UseTexture", use ? 1.0f : 0.0f);
    }

    // 修复为实际存在的 u_Projection 变量
    public void setup(Matrix4f projection, Color color) {
        this.bind();
        this.setUniform("u_Projection", projection);
        this.setUniform("u_TextColor", color);
        this.setUniform("u_Texture", 0);
    }
}