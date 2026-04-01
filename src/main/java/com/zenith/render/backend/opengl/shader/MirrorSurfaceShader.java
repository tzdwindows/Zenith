package com.zenith.render.backend.opengl.shader;

import com.zenith.common.math.Color;
import com.zenith.render.backend.opengl.LightManager;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

public class MirrorSurfaceShader extends GLShader {

    private static final String VERTEX_SRC =
            "#version 330 core\n" +
                    "layout (location = 0) in vec3 aPos;\n" +
                    "layout (location = 1) in vec3 aNormal;\n" +
                    "layout (location = 2) in vec2 aTexCoord;\n" +
                    "\n" +
                    "uniform mat4 u_ViewProjection;\n" +
                    "uniform mat4 u_View;\n" +
                    "uniform mat4 u_Model;\n" +
                    "\n" +
                    "out vec3 vNormalView;\n" +
                    "out vec3 vPosView;\n" +
                    "out vec3 vWorldPos;\n" +
                    "out vec3 vWorldNormal;\n" +
                    "\n" +
                    "void main() {\n" +
                    "    vec4 worldPos = u_Model * vec4(aPos, 1.0);\n" +
                    "    vWorldPos = worldPos.xyz;\n" +
                    "\n" +
                    "    vec4 viewPos = u_View * worldPos;\n" +
                    "    vPosView = viewPos.xyz;\n" +
                    "\n" +
                    "    // 正确法线变换：inverse-transpose\n" +
                    "    mat3 normalMatrixWorld = transpose(inverse(mat3(u_Model)));\n" +
                    "    mat3 normalMatrixView  = transpose(inverse(mat3(u_View * u_Model)));\n" +
                    "\n" +
                    "    vWorldNormal = normalize(normalMatrixWorld * aNormal);\n" +
                    "    vNormalView  = normalize(normalMatrixView * aNormal);\n" +
                    "\n" +
                    "    gl_Position = u_ViewProjection * worldPos;\n" +
                    "}";

    private static final String FRAGMENT_SRC =
            "#version 330 core\n" +
                    "\n" +
                    "struct PBRParams {\n" +
                    "    vec3  diffuseColor;\n" +
                    "    vec3  f0;\n" +
                    "    float roughness;\n" +
                    "    float metallic;\n" +
                    "};\n" +
                    "\n" +
                    "#include \"common_math.glsl\"\n" +
                    "#include \"brdf.glsl\"\n" +
                    "#include \"surface_shading.glsl\"\n" +
                    "#include \"lighting.glsl\"\n" +
                    "#include \"psrdnoise3.glsl\"\n" +
                    "\n" +
                    "in vec3 vNormalView;\n" +
                    "in vec3 vPosView;\n" +
                    "in vec3 vWorldPos;\n" +
                    "in vec3 vWorldNormal;\n" +
                    "\n" +
                    "out vec4 FragColor;\n" +
                    "\n" +
                    "uniform sampler2D u_SceneColor;\n" +
                    "uniform vec4  u_BaseColor;\n" +
                    "uniform vec3  u_ViewPos;\n" +
                    "uniform vec2  u_Resolution;\n" +
                    "uniform float u_Time;\n" +
                    "uniform float u_Roughness;\n" +
                    "uniform float u_Metallic;\n" +
                    "\n" +
                    "float screenEdgeFade(vec2 uv) {\n" +
                    "    vec2 edge = min(uv, 1.0 - uv);\n" +
                    "    float fadeX = clamp(edge.x / 0.05, 0.0, 1.0);\n" +
                    "    float fadeY = clamp(edge.y / 0.05, 0.0, 1.0);\n" +
                    "    return fadeX * fadeY;\n" +
                    "}\n" +
                    "\n" +
                    "vec3 sampleSceneReflection(vec2 uv, float roughness) {\n" +
                    "    vec2 texel = 1.0 / u_Resolution;\n" +
                    "    float radius = mix(0.0, 8.0, roughness * roughness);\n" +
                    "\n" +
                    "    vec2 dx = vec2(texel.x * radius, 0.0);\n" +
                    "    vec2 dy = vec2(0.0, texel.y * radius);\n" +
                    "\n" +
                    "    vec3 c = vec3(0.0);\n" +
                    "    c += texture(u_SceneColor, uv).rgb * 0.40;\n" +
                    "    c += texture(u_SceneColor, uv + dx).rgb * 0.15;\n" +
                    "    c += texture(u_SceneColor, uv - dx).rgb * 0.15;\n" +
                    "    c += texture(u_SceneColor, uv + dy).rgb * 0.15;\n" +
                    "    c += texture(u_SceneColor, uv - dy).rgb * 0.15;\n" +
                    "    return c;\n" +
                    "}\n" +
                    "\n" +
                    "void main() {\n" +
                    "    // ----- 1. 基础向量 -----\n" +
                    "    vec3 N_view  = normalize(vNormalView);\n" +
                    "    vec3 V_view  = normalize(-vPosView);\n" +
                    "    vec3 N_world = normalize(vWorldNormal);\n" +
                    "    vec3 V_world = normalize(u_ViewPos - vWorldPos);\n" +
                    "\n" +
                    "    // ----- 2. PBR 参数 -----\n" +
                    "    PBRParams pbr;\n" +
                    "    pbr.diffuseColor = u_BaseColor.rgb * (1.0 - u_Metallic);\n" +
                    "    pbr.f0 = mix(vec3(0.04), u_BaseColor.rgb, u_Metallic);\n" +
                    "    pbr.roughness = clamp(u_Roughness, 0.02, 1.0);\n" +
                    "    pbr.metallic = clamp(u_Metallic, 0.0, 1.0);\n" +
                    "\n" +
                    "    // ----- 3. 直接光照（物理更合理）-----\n" +
                    "    vec3 directLighting = evaluateLights(pbr, N_world, V_world, vWorldPos);\n" +
                    "\n" +
                    "    // ----- 4. 屏幕空间镜面反射（近似）-----\n" +
                    "    vec2 screenUV = gl_FragCoord.xy / u_Resolution;\n" +
                    "    vec3 R_view = normalize(reflect(-V_view, N_view));\n" +
                    "\n" +
                    "    vec3 grad;\n" +
                    "    float noise = psrdnoise(vWorldPos * 0.5 + vec3(u_Time * 0.2), vec3(0.0), 0.0, grad);\n" +
                    "\n" +
                    "    // 远处物体反射偏移应更小，否则会飘\n" +
                    "    float perspectiveScale = 0.35 / max(abs(vPosView.z), 1.0);\n" +
                    "    vec2 reflectOffset = R_view.xy * perspectiveScale * (1.0 - 0.35 * pbr.roughness);\n" +
                    "    reflectOffset += noise * 0.02 * (1.0 - pbr.roughness);\n" +
                    "\n" +
                    "    vec2 rawReflectUV = screenUV + reflectOffset;\n" +
                    "    float reflectionFade = screenEdgeFade(rawReflectUV);\n" +
                    "    vec2 reflectUV = clamp(rawReflectUV, 0.001, 0.999);\n" +
                    "\n" +
                    "    vec3 specularReflection = sampleSceneReflection(reflectUV, pbr.roughness);\n" +
                    "    specularReflection *= reflectionFade;\n" +
                    "\n" +
                    "    // ----- 5. 菲涅耳 -----\n" +
                    "    float NoV = saturate(dot(N_view, V_view));\n" +
                    "    vec3 F = F_Schlick(pbr.f0, NoV);\n" +
                    "\n" +
                    "    // 间接镜面反射\n" +
                    "    vec3 indirectSpecular = specularReflection * F;\n" +
                    "\n" +
                    "    // ----- 6. 最终颜色 -----\n" +
                    "    vec3 finalColor = directLighting + indirectSpecular;\n" +
                    "\n" +
                    "    FragColor = vec4(finalizeColor(finalColor), 1.0);\n" +
                    "}";

    public MirrorSurfaceShader() {
        super("MirrorSurfaceShader", VERTEX_SRC, FRAGMENT_SRC);
    }

    public void applyLights(LightManager lm, Vector3f viewPos) {
        this.bind();
        this.setUniform("u_ViewPos", viewPos);
        lm.apply(this, viewPos);
    }

    /**
     * 兼容旧调用。
     * 注意：这只是默认值，真实项目里最好调用下面那个完整 setup，
     * 把实际分辨率 / roughness / metallic / time 都传进来。
     */
    public void setup(Matrix4f viewProj, Matrix4f view, Matrix4f model, Color color) {
        setup(viewProj, view, model, color, 1920, 1080, 0.05f, 1.0f, 0.0f);
    }

    public void setup(Matrix4f viewProj,
                      Matrix4f view,
                      Matrix4f model,
                      Color color,
                      int viewportWidth,
                      int viewportHeight,
                      float roughness,
                      float metallic,
                      float time) {
        this.bind();
        this.setUniform("u_ViewProjection", viewProj);
        this.setUniform("u_View", view);
        this.setUniform("u_Model", model);
        this.setUniform("u_BaseColor", new Vector4f(color.r, color.g, color.b, color.a));
        this.setUniform("u_SceneColor", 0);
        this.setUniform("u_Resolution", new Vector2f(viewportWidth, viewportHeight));
        this.setUniform("u_Roughness", clamp01Range(roughness, 0.02f, 1.0f));
        this.setUniform("u_Metallic", clamp01Range(metallic, 0.0f, 1.0f));
        this.setUniform("u_Time", time);
    }

    public void setTime(float time) {
        this.bind();
        this.setUniform("u_Time", time);
    }

    public void setMaterial(float roughness, float metallic) {
        this.bind();
        this.setUniform("u_Roughness", clamp01Range(roughness, 0.02f, 1.0f));
        this.setUniform("u_Metallic", clamp01Range(metallic, 0.0f, 1.0f));
    }

    public void setResolution(int viewportWidth, int viewportHeight) {
        this.bind();
        this.setUniform("u_Resolution", new Vector2f(viewportWidth, viewportHeight));
    }

    private static float clamp01Range(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}