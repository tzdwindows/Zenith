package com.zenith.render.backend.opengl.shader;

import com.zenith.common.math.Color;
import com.zenith.common.utils.InternalLogger;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import java.nio.FloatBuffer;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL31.GL_INVALID_INDEX;
import static org.lwjgl.opengl.GL31.glGetUniformBlockIndex;
import static org.lwjgl.opengl.GL31.glUniformBlockBinding;

public class AnimationShader extends GLShader {

    public static final boolean FORCE_DEBUG_VISUALS = false;
    private static final int MAX_BONES = 100;

    private static String getVertexSource() {
        return "#version 330 core\n" +
                "layout (location = 0) in vec3 aPos;\n" +
                "layout (location = 1) in vec2 aTexCoord;\n" +
                "layout (location = 2) in vec3 aNormal;\n" +
                "layout (location = 3) in ivec4 aBoneIDs;\n" +
                "layout (location = 4) in vec4 aWeights;\n" +
                "\n" +
                "uniform mat4 u_ViewProjection;\n" +
                "uniform mat4 u_Model;\n" +
                "\n" +
                "// 【修复】匹配 GLBoneBuffer 的 UBO 定义，而不是使用 uniform 数组\n" +
                "layout (std140) uniform BoneBlock {\n" +
                "    mat4 u_JointMatrices[100];\n" +
                "};\n" +
                "\n" +
                "out vec2 vTexCoord;\n" +
                "out vec3 vNormal;\n" +
                "out vec4 vDebugColor;\n" +
                "\n" +
                "void main() {\n" +
                "    mat4 skinMat = mat4(0.0);\n" +
                "    float totalWeight = aWeights.x + aWeights.y + aWeights.z + aWeights.w;\n" +
                "\n" +
                "    for(int i = 0; i < 4; i++) {\n" +
                "        int id = aBoneIDs[i];\n" +
                "        if(id >= 0 && id < 100) {\n" +
                "            skinMat += u_JointMatrices[id] * aWeights[i];\n" +
                "        }\n" +
                "    }\n" +
                "\n" +
                "    if (totalWeight < 0.01) {\n" +
                "        vDebugColor = vec4(1.0, 0.0, 0.0, 1.0);\n" +
                "        skinMat = mat4(1.0);\n" +
                "    } else {\n" +
                "        float hue = float(aBoneIDs.x) * 0.15;\n" +
                "        vDebugColor = vec4(abs(sin(hue)), abs(sin(hue + 2.0)), abs(sin(hue + 4.0)), 1.0);\n" +
                "    }\n" +
                "\n" +
                "    vec4 worldPos = u_Model * skinMat * vec4(aPos, 1.0);\n" +
                "    vTexCoord = aTexCoord;\n" +
                "    vNormal = mat3(u_Model * skinMat) * aNormal;\n" +
                "    gl_Position = u_ViewProjection * worldPos;\n" +
                "}\n";
    }

    private static String getFragmentSource() {
        return "#version 330 core\n" +
                "in vec2 vTexCoord;\n" +
                "in vec3 vNormal;\n" +
                "in vec4 vDebugColor;\n" +
                "out vec4 FragColor;\n" +
                "uniform sampler2D u_DiffuseMap;\n" +
                "uniform vec4 u_BaseColor;\n" + // 【修复】接收 setup 传入的模型基础色
                "\n" +
                "void main() {\n" +
                "    if (" + FORCE_DEBUG_VISUALS + ") {\n" +
                "        vec3 light = vec3(normalize(vec3(1,1,1)));\n" +
                "        float d = max(dot(normalize(vNormal), light), 0.5);\n" +
                "        FragColor = vec4(vDebugColor.rgb * d, 1.0);\n" +
                "    } else {\n" +
                "        vec4 texColor = texture(u_DiffuseMap, vTexCoord);\n" +
                "        \n" +
                "        // 【修复】如果贴图采样器没有绑定贴图（返回0），给一个白色的兜底，防止黑屏\n" +
                "        if (texColor.a < 0.05 && length(texColor.rgb) < 0.05) {\n" +
                "            texColor = vec4(1.0);\n" +
                "        }\n" +
                "\n" +
                "        // 【修复】添加最基础的漫反射光照与环境光底色，防止背光面纯黑\n" +
                "        vec3 lightDir = normalize(vec3(0.5, 1.0, 0.8));\n" +
                "        float diffuse = max(dot(normalize(vNormal), lightDir), 0.3);\n" +
                "\n" +
                "        FragColor = texColor * u_BaseColor * vec4(vec3(diffuse), 1.0);\n" +
                "    }\n" +
                "}";
    }

    public AnimationShader() {
        super("AnimationShader", getVertexSource(), getFragmentSource());
    }

    public void setup(Matrix4f viewProj, Matrix4f model, Color color) {
        this.bind();
        this.setUniform("u_ViewProjection", viewProj);
        this.setUniform("u_Model", model);
        this.setUniform("u_DiffuseMap", 0);

        // 传递基础颜色
        int colorLoc = glGetUniformLocation(getRendererID_Internal(), "u_BaseColor");
        if (colorLoc != -1) {
            glUniform4f(colorLoc, color.r, color.g, color.b, color.a);
        }

        // 【修复】将 UBO Block (BoneBlock) 绑定到槽位 0，与 GLBoneBuffer 对齐
        int blockIndex = glGetUniformBlockIndex(getRendererID_Internal(), "BoneBlock");
        if (blockIndex != GL_INVALID_INDEX) {
            glUniformBlockBinding(getRendererID_Internal(), blockIndex, 0);
        }
    }

    // 注意：因为已经改为真正的 UBO (BoneBlock)，下面的 setBoneMatrices 实际上已被废弃，
    // 骨骼更新将完全由 GLBoneBuffer 自动接管！为了兼容旧代码你可以保留它们。
    @Deprecated
    public void setBoneMatrices(FloatBuffer buffer) {}
}