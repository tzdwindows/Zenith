package com.zenith.render.backend.opengl.shader;

import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;

/**
 * 增强版 RainShader: 包含物理受光响应与运动模糊模拟
 */
public class RainShader extends GLShader {

    private static final String VERTEX_SRC =
            "#version 330 core\n" +
                    "layout (location = 0) in vec3 aPos;\n" +
                    "layout (location = 2) in vec2 aTexCoord;\n" +
                    "layout (location = 3) in vec4 aColor;\n" + // xyz: 初始位置, w: 下落速度

                    "uniform mat4 u_ViewProjection;\n" +
                    "uniform vec3 u_ViewPos;\n" +
                    "uniform float u_Time;\n" +
                    "uniform vec2 u_Wind;\n" +

                    "out vec2 vTexCoord;\n" +
                    "out float vAlpha;\n" +
                    "out vec3 vWorldPos;\n" +
                    "out vec3 vNormal;\n" +

                    "void main() {\n" +
                    "    vTexCoord = aTexCoord;\n" +
                    "    vec3 center = aColor.xyz;\n" +
                    "    float speed = aColor.w;\n" +

                    "    // 1. 物理循环下落\n" +
                    "    float fallDist = u_Time * (speed + 20.0); // 增加基础重力速度\n" +
                    "    center.y = 40.0 - mod(fallDist + center.y, 40.0);\n" +

                    "    // 2. 空间无缝跟随相机\n" +
                    "    float boxSize = 80.0;\n" +
                    "    float halfBox = 40.0;\n" +
                    "    center.x = u_ViewPos.x - halfBox + mod(center.x - u_ViewPos.x + halfBox, boxSize);\n" +
                    "    center.z = u_ViewPos.z - halfBox + mod(center.z - u_ViewPos.z + halfBox, boxSize);\n" +

                    "    // 3. 风力影响\n" +
                    "    center.xz += (40.0 - center.y) * u_Wind;\n" +

                    "    // 4. 计算面片法线与广告牌转向\n" +
                    "    vec3 look = normalize(u_ViewPos - center);\n" +
                    "    look.y = 0.0;\n" +
                    "    vec3 right = cross(vec3(0.0, 1.0, 0.0), look);\n" +
                    "    vNormal = look; // 雨丝正面面向相机\n" +

                    "    // 5. 动态拉伸 (模拟运动模糊)\n" +
                    "    float stretch = 1.0 + (speed * 0.05);\n" +
                    "    vec3 finalPos = center + right * aPos.x + vec3(0.0, aPos.y * stretch, 0.0);\n" +
                    "    \n" +
                    "    vWorldPos = finalPos;\n" +
                    "    vAlpha = clamp((halfBox - length(center.xz - u_ViewPos.xz)) / 10.0, 0.0, 1.0);\n" +
                    "    gl_Position = u_ViewProjection * vec4(finalPos, 1.0);\n" +
                    "}";

    private static final String FRAGMENT_SRC =
            "#version 330 core\n" +
                    "// 引入 ZenithEngine 核心数学库，用于 finalizeColor 等 HDR 处理\n" +
                    "#include \"common_math.glsl\"\n" +
                    "\n" +
                    "in vec2 vTexCoord;\n" +
                    "in float vAlpha;\n" +
                    "in vec3 vWorldPos;\n" +
                    "in vec3 vNormal;\n" +
                    "\n" +
                    "out vec4 FragColor;\n" +
                    "\n" +
                    "uniform vec3 u_SunDir;\n" +
                    "uniform vec3 u_SunIntensity;\n" +
                    "uniform vec3 u_AmbientSkyColor;\n" +
                    "uniform vec3 u_ViewPos;\n" +
                    "\n" +
                    "void main() {\n" +
                    "    // 1. 形状定义 (雨丝中间亮，两边淡，底部尖)\n" +
                    "    float mask = (1.0 - abs(vTexCoord.x - 0.5) * 2.0);\n" +
                    "    mask = pow(mask, 3.0) * vTexCoord.y;\n" +
                    "    \n" +
                    "    // 2. 光学响应：逆光增强 (Backlighting)\n" +
                    "    // 当雨滴处于太阳和相机之间时，由于折射，它会非常亮\n" +
                    "    vec3 V = normalize(u_ViewPos - vWorldPos);\n" +
                    "    vec3 L = normalize(u_SunDir);\n" +
                    "    float becklight = pow(max(0.0, dot(-L, V)), 16.0) * 5.0;\n" +
                    "    \n" +
                    "    // 3. 漫反射与天光响应\n" +
                    "    // 雨水是透明介质，受环境天光影响极大\n" +
                    "    float diff = max(0.2, dot(vNormal, L)); \n" +
                    "    vec3 ambient = u_AmbientSkyColor * 0.8;\n" +
                    "    \n" +
                    "    // 4. 颜色合成\n" +
                    "    // 雨滴颜色 = (基础散射 + 太阳直射 + 强逆光折射) * 环境遮蔽\n" +
                    "    vec3 rainColor = (ambient + u_SunIntensity * diff + u_SunIntensity * becklight);\n" +
                    "    \n" +
                    "    float finalAlpha = mask * vAlpha * 0.5;\n" +
                    "    if (finalAlpha < 0.01) discard;\n" +
                    "\n" +
                    "    // 使用引擎内置函数处理 HDR 和 Gamma\n" +
                    "    FragColor = vec4(finalizeColor(rainColor), finalAlpha);\n" +
                    "}";

    public RainShader() {
        super("RainShader", VERTEX_SRC, FRAGMENT_SRC);
    }

    /**
     * 更新雨水光照参数
     */
    public void updateLighting(Vector3f sunDir, Vector3f sunIntensity, Vector3f ambientColor) {
        this.bind();
        this.setUniform("u_SunDir", sunDir);
        this.setUniform("u_SunIntensity", sunIntensity);
        this.setUniform("u_AmbientSkyColor", ambientColor);
    }
}