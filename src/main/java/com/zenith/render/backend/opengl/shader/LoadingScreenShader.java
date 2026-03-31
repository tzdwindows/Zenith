package com.zenith.render.backend.opengl.shader;

import org.joml.Vector2f;

/**
 * Logo 专用加载动画：
 * - 增强光晕、渐变和高光
 * - 动态阴影与反射效果
 * - 调整比例确保 logo 不变形
 */
public class LoadingScreenShader extends GLShader {

    private static final String VERTEX_SRC = """
        #version 330 core
        layout (location = 0) in vec2 aPos;
        out vec2 TexCoords;

        void main() {
            TexCoords = aPos * 0.5 + 0.5;
            gl_Position = vec4(aPos, 0.0, 1.0);
        }
    """;

    private static final String FRAG_SRC = """
        #version 330 core
        out vec4 FragColor;
        in vec2 TexCoords;

        uniform float uTime;
        uniform vec2 uResolution;
        uniform float uProgress;

        float hash12(vec2 p) {
            vec3 p3 = fract(vec3(p.xyx) * 0.1031);
            p3 += dot(p3, p3.yzx + 33.33);
            return fract((p3.x + p3.y) * p3.z);
        }

        float sdSegment(vec2 p, vec2 a, vec2 b) {
            vec2 pa = p - a;
            vec2 ba = b - a;
            float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0);
            return length(pa - ba * h);
        }

        float sdLogoA(vec2 p) {
            const vec2 pts[8] = vec2[8](
                vec2(0.240, 0.780),
                vec2(0.320, 0.780),
                vec2(0.570, 0.410),
                vec2(0.420, 0.430),
                vec2(0.430, 0.480),
                vec2(0.260, 0.480),
                vec2(0.210, 0.550),
                vec2(0.420, 0.550)
            );

            float d = 1e9;
            bool inside = false;

            for (int i = 0, j = 7; i < 8; j = i, ++i) {
                vec2 a = pts[j];
                vec2 b = pts[i];
                d = min(d, sdSegment(p, a, b));

                bool intersect = ((b.y > p.y) != (a.y > p.y)) &&
                    (p.x < (a.x - b.x) * (p.y - b.y) / (a.y - b.y + 1e-6) + b.x);

                if (intersect) inside = !inside;
            }

            return inside ? -d : d;
        }

        float sdLogoB(vec2 p) {
            const vec2 pts[9] = vec2[9](
                vec2(0.350, 0.780),
                vec2(0.620, 0.780),
                vec2(0.700, 0.660),
                vec2(0.520, 0.660),
                vec2(0.650, 0.460),
                vec2(0.750, 0.360),
                vec2(0.650, 0.260),
                vec2(0.550, 0.360),
                vec2(0.600, 0.420)
            );

            float d = 1e9;
            bool inside = false;

            for (int i = 0, j = 8; i < 9; j = i, ++i) {
                vec2 a = pts[j];
                vec2 b = pts[i];
                d = min(d, sdSegment(p, a, b));

                bool intersect = ((b.y > p.y) != (a.y > p.y)) &&
                    (p.x < (a.x - b.x) * (p.y - b.y) / (a.y - b.y + 1e-6) + b.x);

                if (intersect) inside = !inside;
            }

            return inside ? -d : d;
        }

        float logoDistance(vec2 p) {
            return min(sdLogoA(p), sdLogoB(p));
        }

        vec3 palette(float t) {
            vec3 a = vec3(0.06, 0.18, 0.40);
            vec3 b = vec3(0.05, 0.55, 0.82);
            vec3 c = vec3(0.00, 0.88, 0.92);
            vec3 d = vec3(0.25, 0.98, 1.00);
            vec3 x = mix(a, b, smoothstep(0.00, 0.40, t));
            vec3 y = mix(c, d, smoothstep(0.40, 1.00, t));
            return mix(x, y, smoothstep(0.45, 0.95, t));
        }

        void main() {
            vec2 uv = TexCoords;

            // SVG 是 y 向下，这里翻转一次对齐路径
            vec2 p = vec2(uv.x, 1.0 - uv.y);

            // 轻微呼吸放大，让 logo 更有“活性”
            float breath = 0.985 + 0.018 * sin(uTime * 1.7);
            p = (p - 0.5) / breath + 0.5;

            // 让 logo 更居中、更突出一点
            p = mix(p, vec2(0.5) + (p - vec2(0.5)) * 1.03, 0.35);

            float d = logoDistance(p);

            // 基础遮罩：内部填充
            float fill = 1.0 - smoothstep(0.0, 0.008, d);

            // 描边与外发光
            float edge = 1.0 - smoothstep(0.0, 0.020, abs(d));
            float glow = exp(-abs(d) * 42.0);
            float outerGlow = exp(-max(d, 0.0) * 14.0);

            // 统一的颜色基调：深蓝 -> 青蓝 -> 亮青
            float gradT = clamp(uv.x * 0.55 + (1.0 - uv.y) * 0.45 + 0.10 * sin(uTime * 0.6), 0.0, 1.0);
            vec3 baseColor = palette(gradT);

            // 背景：压暗，给主体腾出对比度
            float vignette = smoothstep(1.10, 0.25, length((uv - 0.5) * vec2(uResolution.x / uResolution.y, 1.0)));
            float bgNoise = hash12(gl_FragCoord.xy + uTime * 60.0) * 0.015;

            vec3 bg = vec3(0.012, 0.020, 0.045);
            bg += vec3(0.000, 0.020, 0.035) * vignette;
            bg += bgNoise;

            // 让 logo 周围有一圈能量场
            float halo = exp(-length(p - vec2(0.50, 0.50)) * 4.4);
            vec3 haloColor = vec3(0.00, 0.35, 0.70) * halo * 0.25;

            // 扫光：沿对角线移动的亮带
            vec2 sweepDir = normalize(vec2(1.0, 0.58));
            float sweepPos = fract(uTime * 0.16) * 1.65 - 0.35;
            float sweepCoord = dot(p, sweepDir);
            float sweep = smoothstep(0.060, 0.0, abs(sweepCoord - sweepPos));

            // 条纹高光：让表面更“金属/能量感”
            float stripes = 0.5 + 0.5 * sin((p.x * 18.0 - p.y * 8.0) * 3.14159 + uTime * 5.0);
            stripes = smoothstep(0.72, 1.0, stripes);

            // progress 让加载越靠后，主体越亮
            float prog = clamp(uProgress, 0.0, 1.0);
            float intensity = 0.75 + 0.85 * prog;

            // 轮廓阴影：进一步拉开主体
            float shadow = exp(-max(d, 0.0) * 26.0);
            vec3 shadowColor = vec3(0.0, 0.08, 0.16) * shadow * 0.55;

            // 主体填充
            vec3 logoFill = baseColor * fill * intensity;

            // 内部更亮的核心
            float core = 1.0 - smoothstep(0.0, 0.20, length(p - vec2(0.50, 0.50)));
            vec3 coreColor = mix(vec3(0.10, 0.75, 1.00), vec3(0.35, 1.00, 0.82), prog);
            coreColor *= core * fill * (0.25 + 0.85 * prog);

            // 描边发光
            vec3 rimColor = vec3(0.00, 0.78, 1.00) * edge * 0.65;
            vec3 glowColor = vec3(0.00, 0.94, 1.00) * glow * (0.55 + 0.75 * prog);
            vec3 outerGlowColor = vec3(0.05, 0.30, 0.95) * outerGlow * 0.30;

            // 扫光叠加
            vec3 sweepColor = vec3(1.0) * sweep * fill * 0.55;
            vec3 stripeColor = vec3(0.45, 0.98, 1.00) * stripes * fill * 0.18;

            // 最终合成
            vec3 col = bg;
            col += shadowColor;
            col += haloColor;
            col += outerGlowColor;
            col += glowColor;
            col += logoFill;
            col += rimColor;
            col += coreColor;
            col += sweepColor;
            col += stripeColor;

            // 轻微 HDR 压缩
            col = col / (col + vec3(1.0));
            col = pow(col, vec3(0.92));

            FragColor = vec4(col, 1.0);
        }
    """;

    private final Vector2f tempResolution = new Vector2f();

    public LoadingScreenShader() {
        super("LoadingScreenShader", VERTEX_SRC, FRAG_SRC);
    }

    public void setTime(float time) {
        setUniform("uTime", time);
    }

    public void setResolution(float width, float height) {
        tempResolution.set(width, height);
        setUniform("uResolution", tempResolution);
    }

    public void setProgress(float progress) {
        setUniform("uProgress", progress);
    }
}