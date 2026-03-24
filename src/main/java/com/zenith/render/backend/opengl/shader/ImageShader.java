package com.zenith.render.backend.opengl.shader;

import com.zenith.common.math.Color;
import org.joml.Matrix4f;

public class ImageShader extends GLShader {

    private static final String VERTEX_SRC =
            "#version 330 core\n" +
                    "layout (location = 0) in vec3 aPos;\n" +
                    "layout (location = 1) in vec2 aTexCoord;\n" +
                    "uniform mat4 u_ViewProjection;\n" +
                    "uniform mat4 u_Model;\n" +
                    "out vec2 vTexCoord;\n" +
                    "void main() {\n" +
                    "    vTexCoord = aTexCoord;\n" +
                    "    gl_Position = u_ViewProjection * u_Model * vec4(aPos, 1.0);\n" +
                    "}";

    private static final String FRAGMENT_SRC =
            "#version 330 core\n" +
                    "in vec2 vTexCoord;\n" +
                    "out vec4 FragColor;\n" +
                    "uniform sampler2D u_Texture;\n" +
                    "uniform vec4 u_Color;\n" +
                    "void main() {\n" +
                    "    // 直接采样 RGBA，不再只读 .r 通道\n" +
                    "    vec4 texColor = texture(u_Texture, vTexCoord);\n" +
                    "    FragColor = texColor * u_Color;\n" +
                    "}";

    public ImageShader() {
        super("ImageShader", VERTEX_SRC, FRAGMENT_SRC);
    }

    public void setup(Matrix4f viewProj, Matrix4f model, Color color) {
        this.bind();
        this.setUniform("u_ViewProjection", viewProj);
        this.setUniform("u_Model", model);
        this.setUniform("u_Color", color);
        this.setUniform("u_Texture", 0);
    }
}