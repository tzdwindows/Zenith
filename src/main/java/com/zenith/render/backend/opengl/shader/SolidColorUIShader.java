package com.zenith.ui.render.shader;

public class SolidColorUIShader extends UIShader {
    private static final String SHADER_NAME = "SolidColorUI_Shader";

    private static final String VERTEX_SOURCE =
            "#version 330 core\n" +
                    "layout (location = 0) in vec2 aPos;\n" +
                    "layout (location = 1) in vec2 aTexCoords;\n" +
                    "layout (location = 2) in vec4 aColor;\n" +
                    "\n" +
                    "out vec4 Color;\n" +
                    "\n" +
                    "uniform mat4 u_Projection;\n" +
                    "\n" +
                    "void main() {\n" +
                    "    Color = aColor;\n" +
                    "    gl_Position = u_Projection * vec4(aPos, 0.0, 1.0);\n" +
                    "}";

    private static final String FRAGMENT_SOURCE =
            "#version 330 core\n" +
                    "in vec4 Color;\n" +
                    "out vec4 fragColor;\n" +
                    "\n" +
                    "void main() {\n" +
                    "    fragColor = Color;\n" +
                    "}";

    public SolidColorUIShader() {
        super(SHADER_NAME, VERTEX_SOURCE, FRAGMENT_SOURCE);
    }

    @Override
    public void setTextureSlot(int slot) {

    }
}