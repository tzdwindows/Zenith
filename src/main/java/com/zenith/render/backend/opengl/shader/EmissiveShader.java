package com.zenith.render.backend.opengl.shader;

import com.zenith.asset.AssetIdentifier;
import com.zenith.asset.AssetResource;
import org.joml.Matrix4f;
import com.zenith.common.math.Color;
import org.joml.Vector3f;

/**
 * 有关太阳的着色器
 */
public class EmissiveShader extends GLShader {

    private static final String VERTEX_SRC =
            "#version 330 core\n" +
                    "layout (location = 0) in vec3 aPos;\n" +
                    "layout (location = 1) in vec3 aNormal;\n" +
                    "uniform mat4 u_ViewProjection;\n" +
                    "uniform mat4 u_Model;\n" +
                    "uniform float u_Time;\n" +
                    "out vec3 vNormal;\n" +
                    "out vec3 vWorldPos;\n" +
                    "out vec3 vLocalPos;\n" +
                    "\n" +
                    "#include \"psrdnoise3.glsl\"\n" +
                    "\n" +
                    "void main() {\n" +
                    "    vLocalPos = aPos;\n" +
                    "    vec3 g;\n" +
                    "    // 模拟星球表面的低频脉动/膨胀\n" +
                    "    float wobble = psrdnoise(aPos * 0.8, vec3(0.0), u_Time * 0.2, g) * 0.05;\n" +
                    "    vec3 displacedPos = aPos + aNormal * wobble;\n" +
                    "    \n" +
                    "    vec4 worldPos = u_Model * vec4(displacedPos, 1.0);\n" +
                    "    vWorldPos = worldPos.xyz;\n" +
                    "    vNormal = mat3(u_Model) * aNormal;\n" +
                    "    gl_Position = u_ViewProjection * worldPos;\n" +
                    "}";

    private static final String FRAGMENT_TEMPLATE =
            "#version 330 core\n" +
                    "in vec3 vNormal;\n" +
                    "in vec3 vWorldPos;\n" +
                    "in vec3 vLocalPos;\n" +
                    "out vec4 FragColor;\n" +
                    "\n" +
                    "uniform vec3 u_ViewPos;\n" +
                    "uniform vec4 u_Color;\n" +
                    "uniform float u_Intensity;\n" +
                    "uniform float u_Time;\n" +
                    "\n" +
                    "#include \"psrdnoise3.glsl\"\n" +
                    "\n" +
                    "// --- 分形布朗运动 (FBM) 生成米粒组织和湍流 ---\n" +
                    "float fbm_turbulent(vec3 p) {\n" +
                    "    float f = 0.0;\n" +
                    "    float amp = 0.5;\n" +
                    "    vec3 g;\n" +
                    "    for(int i=0; i<5; i++) {\n" +
                    "        // 使用 abs 生成尖锐的脊线，反转后得到像岩浆裂缝一样的网格结构\n" +
                    "        float n = psrdnoise(p, vec3(0.0), u_Time * 0.15, g);\n" +
                    "        f += amp * (1.0 - abs(n)); \n" +
                    "        p *= 2.1;\n" +
                    "        amp *= 0.5;\n" +
                    "    }\n" +
                    "    return f;\n" +
                    "}\n" +
                    "\n" +
                    "void main() {\n" +
                    "    vec3 N = normalize(vNormal);\n" +
                    "    vec3 V = normalize(u_ViewPos - vWorldPos);\n" +
                    "    float dotNV = max(dot(N, V), 0.0);\n" +
                    "    float fresnel = 1.0 - dotNV;\n" + // 边缘为1，中心为0
                    "    \n" +
                    "    // --- 1. 表面流体与纹理 ---\n" +
                    "    vec3 p = vLocalPos * 1.5;\n" +
                    "    \n" +
                    "    // 大范围的扭曲，模拟表面等离子体风暴\n" +
                    "    vec3 g;\n" +
                    "    float stormDistortion = psrdnoise(p * 0.3, vec3(0.0), u_Time * 0.05, g);\n" +
                    "    p += stormDistortion * 1.2;\n" +
                    "    \n" +
                    "    // 计算高频米粒组织的温度场 (0.0 - 1.0+)\n" +
                    "    float temperature = fbm_turbulent(p);\n" +
                    "    \n" +
                    "    // 叠加太阳黑子 (低频暗区)\n" +
                    "    float sunspot = psrdnoise(vLocalPos * 0.8, vec3(0.0), u_Time * 0.02, g);\n" +
                    "    sunspot = smoothstep(0.3, 0.8, abs(sunspot)); // 提取极值作为黑子\n" +
                    "    temperature = mix(temperature, 0.1, sunspot * 0.85); // 压暗黑子区域\n" +
                    "    \n" +
                    "    // --- 2. 热力学颜色映射 ---\n" +
                    "    // 定义温度色板：深邃暗红 -> 狂暴火橙 -> 耀眼亮黄 -> 核心白炽\n" +
                    "    vec3 colorDark = vec3(0.15, 0.02, 0.0);   // 黑子颜色\n" +
                    "    vec3 colorRed  = vec3(0.80, 0.15, 0.0);   // 低温区\n" +
                    "    vec3 colorOrg  = u_Color.rgb;             // 中温区 (使用传入的火橙色)\n" +
                    "    vec3 colorYel  = vec3(1.00, 0.85, 0.3);   // 高温区\n" +
                    "    vec3 colorWht  = vec3(1.00, 1.00, 0.9);   // 极高温\n" +
                    "    \n" +
                    "    vec3 surfaceColor;\n" +
                    "    if (temperature < 0.3) {\n" +
                    "        surfaceColor = mix(colorDark, colorRed, smoothstep(0.0, 0.3, temperature));\n" +
                    "    } else if (temperature < 0.6) {\n" +
                    "        surfaceColor = mix(colorRed, colorOrg, smoothstep(0.3, 0.6, temperature));\n" +
                    "    } else if (temperature < 0.85) {\n" +
                    "        surfaceColor = mix(colorOrg, colorYel, smoothstep(0.6, 0.85, temperature));\n" +
                    "    } else {\n" +
                    "        surfaceColor = mix(colorYel, colorWht, smoothstep(0.85, 1.2, temperature));\n" +
                    "    }\n" +
                    "    \n" +
                    "    // --- 3. 日冕耀斑 (Corona / Flares) ---\n" +
                    "    // 在边缘使用噪波生成火焰撕裂感\n" +
                    "    float flareNoise = psrdnoise(vLocalPos * 3.0, vec3(0.0), u_Time * 0.3, g);\n" +
                    "    // Fresnel 控制边缘发光，结合噪波打破完美的圆形\n" +
                    "    float coronaIntensity = pow(fresnel, 3.5) * (flareNoise * 0.5 + 0.8) * 3.0;\n" +
                    "    vec3 coronaColor = mix(colorOrg, colorYel, flareNoise * 0.5 + 0.5) * coronaIntensity;\n" +
                    "    \n" +
                    "    // 中心高光，让球体有体积感，不仅是个平面贴图\n" +
                    "    float centerGlow = pow(dotNV, 4.0) * 0.6;\n" +
                    "    \n" +
                    "    vec3 finalColor = surfaceColor + coronaColor + (colorWht * centerGlow);\n" +
                    "    finalColor *= u_Intensity;\n" +
                    "    \n" +
                    "    // --- 4. 边缘柔化 Alpha ---\n" +
                    "    // 让恒星边缘像气体一样渐渐消散，而不是硬朗的多边形切边\n" +
                    "    float alpha = smoothstep(0.0, 0.15, dotNV + flareNoise * 0.05);\n" +
                    "    \n" +
                    "    // --- 5. ACES 色调映射 (电影级质感) ---\n" +
                    "    // ACES 能在处理极亮颜色(火焰)时，保持颜色不泛白，保留深红和橙色的饱和度\n" +
                    "    float a = 2.51;\n" +
                    "    float b = 0.03;\n" +
                    "    float c = 2.43;\n" +
                    "    float d = 0.59;\n" +
                    "    float e = 0.14;\n" +
                    "    vec3 mapped = clamp((finalColor * (a * finalColor + b)) / (finalColor * (c * finalColor + d) + e), 0.0, 1.0);\n" +
                    "    \n" +
                    "    // Gamma 矫正\n" +
                    "    mapped = pow(mapped, vec3(1.0 / 2.2));\n" +
                    "    \n" +
                    "    FragColor = vec4(mapped, alpha);\n" +
                    "}";

    public EmissiveShader() {
        super("EmissiveShader",
                VERTEX_SRC,
                FRAGMENT_TEMPLATE);

    }

    public void setup(Matrix4f viewProj, Matrix4f model, Vector3f viewPos, Color color, float intensity, float time) {
        this.bind();
        this.setUniform("u_ViewProjection", viewProj);
        this.setUniform("u_Model", model);
        this.setUniform("u_ViewPos", viewPos);
        this.setUniform("u_Color", color);
        this.setUniform("u_Intensity", intensity);
        this.setUniform("u_Time", time);
    }
}