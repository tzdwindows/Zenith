package com.zenith.render.backend.opengl.shader;

import com.zenith.common.math.Color;
import com.zenith.common.utils.InternalLogger;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import java.nio.FloatBuffer;
import static org.lwjgl.opengl.GL20.*;

public class AnimationShader extends GLShader {

    // --- 强制调试开关 ---
    // 设置为 true，模型应该变成五颜六色的。
    public static final boolean FORCE_DEBUG_VISUALS = false;
    // ------------------

    private static final int MAX_BONES = 100;
    private static final FloatBuffer matrixArrayBuffer = BufferUtils.createFloatBuffer(MAX_BONES * 16);

    private static String getVertexSource() {
        return "#version 330 core\n" +
                "layout (location = 0) in vec3 aPos;\n" +
                "layout (location = 1) in vec2 aTexCoord;\n" +
                "layout (location = 2) in vec3 aNormal;\n" +
                "layout (location = 3) in ivec4 aBoneIDs;\n" + // 骨骼 ID
                "layout (location = 4) in vec4 aWeights;\n" +  // 骨骼权重
                "\n" +
                "uniform mat4 u_ViewProjection;\n" +
                "uniform mat4 u_Model;\n" +
                "uniform mat4 u_JointMatrices[100];\n" +
                "\n" +
                "out vec2 vTexCoord;\n" +
                "out vec3 vNormal;\n" +
                "out vec4 vDebugColor;\n" +
                "\n" +
                "void main() {\n" +
                "    mat4 skinMat = mat4(0.0);\n" +
                "    float totalWeight = aWeights.x + aWeights.y + aWeights.z + aWeights.w;\n" +
                "\n" +
                "    // 1. 计算蒙皮矩阵\n" +
                "    for(int i = 0; i < 4; i++) {\n" +
                "        int id = aBoneIDs[i];\n" +
                "        if(id >= 0 && id < 100) {\n" +
                "            skinMat += u_JointMatrices[id] * aWeights[i];\n" +
                "        }\n" +
                "    }\n" +
                "\n" +
                "    // 2. 调试颜色逻辑\n" +
                "    if (totalWeight < 0.01) {\n" +
                "        // 如果没有权重数据，设为纯红色（警示）\n" +
                "        vDebugColor = vec4(1.0, 0.0, 0.0, 1.0);\n" +
                "        skinMat = mat4(1.0);\n" +
                "    } else {\n" +
                "        // 根据第一个骨骼 ID 生成彩虹色\n" +
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
                "\n" +
                "void main() {\n" +
                "    if (" + FORCE_DEBUG_VISUALS + ") {\n" +
                "        // 调试模式：只显示鲜艳的调试色，不看贴图\n" +
                "        vec3 light = vec3(normalize(vec3(1,1,1)));\n" +
                "        float d = max(dot(normalize(vNormal), light), 0.5);\n" +
                "        FragColor = vec4(vDebugColor.rgb * d, 1.0);\n" +
                "    } else {\n" +
                "        FragColor = texture(u_DiffuseMap, vTexCoord);\n" +
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
    }
    public void setBoneMatrices(Matrix4f[] matrices) {
        int location = glGetUniformLocation(getRendererID_Internal(), "u_JointMatrices");
        if (location != -1) {
            matrixArrayBuffer.clear();
            int count = Math.min(matrices.length, MAX_BONES);
            for (int i = 0; i < count; i++) {
                if (matrices[i] != null) matrices[i].get(matrixArrayBuffer);
                else new Matrix4f().get(matrixArrayBuffer);
                matrixArrayBuffer.position((i + 1) * 16);
            }
            matrixArrayBuffer.flip();
            glUniformMatrix4fv(location, false, matrixArrayBuffer);
        }
    }

    public void setBoneMatrices(FloatBuffer buffer) {
        int location = glGetUniformLocation(getRendererID_Internal(), "u_JointMatrices");
        if (location != -1) {
            buffer.rewind();
            glUniformMatrix4fv(location, false, buffer);
        } else {
            InternalLogger.error("Uniform u_JointMatrices not found!");
        }
    }

}