package com.zenith.render.backend.opengl.shader;

import com.zenith.common.utils.InternalLogger;

/**
 * 专门用于渲染游戏实体的标准着色器
 * 支持平滑的 MVP 变换、纹理映射以及基础颜色混合。
 */
public class EntityShader extends GLShader {

    private static final String VERTEX_SOURCE =
            "#version 330 core\n" +
                    "layout (location = 0) in vec3 a_Position;\n" +
                    "layout (location = 1) in vec2 a_TexCoord;\n" +
                    "layout (location = 2) in vec3 a_Normal;\n" +
                    "\n" +
                    "uniform mat4 u_model;\n" +
                    "uniform mat4 u_view;\n" +
                    "uniform mat4 u_projection;\n" +
                    "\n" +
                    "out vec2 v_TexCoord;\n" +
                    "out vec3 v_Normal;\n" +
                    "\n" +
                    "void main() {\n" +
                    "    v_TexCoord = a_TexCoord;\n" +
                    "    v_Normal = mat3(transpose(inverse(u_model))) * a_Normal;\n" +
                    "    gl_Position = u_projection * u_view * u_model * vec4(a_Position, 1.0);\n" +
                    "}";

    private static final String FRAGMENT_SOURCE =
            "#version 330 core\n" +
                    "out vec4 FragColor;\n" +
                    "\n" +
                    "in vec2 v_TexCoord;\n" +
                    "in vec3 v_Normal;\n" +
                    "\n" +
                    "uniform sampler2D u_diffuse;   // 对应 GLMaterial 中的纹理名称\n" +
                    "uniform vec4 u_TextColor;      // 对应 GLMaterial 中的 diffuseColor\n" +
                    "uniform float u_UseTexture;    // 0.0=纯色, 1.0=纹理混合\n" +
                    "\n" +
                    "void main() {\n" +
                    "    vec4 texColor = texture(u_diffuse, v_TexCoord);\n" +
                    "    if (u_UseTexture > 0.5) {\n" +
                    "        FragColor = texColor * u_TextColor;\n" +
                    "    } else {\n" +
                    "        FragColor = u_TextColor;\n" +
                    "    }\n" +
                    "    \n" +
                    "    // 简单的 Alpha 测试，防止透明部分遮挡背景\n" +
                    "    if (FragColor.a < 0.1) discard;\n" +
                    "}";

    /**
     * 创建一个新的实体着色器实例
     */
    public EntityShader() {
        super("EntityShader", VERTEX_SOURCE, FRAGMENT_SOURCE);
        InternalLogger.info("EntityShader: 核心渲染渲染器已就绪");
    }

    /**
     * 快捷配置方法，用于在非组件环境下快速设置渲染状态
     */
    public void setupBaseUniforms() {
        this.bind();
        this.setUniform("u_UseTexture", 0.0f);
        this.setUniform("u_TextColor", new com.zenith.common.math.Color(1, 1, 1, 1));
    }
}