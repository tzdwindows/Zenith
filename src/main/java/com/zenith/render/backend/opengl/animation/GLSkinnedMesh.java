package com.zenith.render.backend.opengl.animation;

import com.zenith.common.utils.InternalLogger;
import com.zenith.render.VertexAttribute;
import com.zenith.render.VertexLayout;
import com.zenith.render.backend.opengl.GLMesh;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * 工业级 SkinnedMesh 实现。
 * 核心区别：对 BoneIDs 使用 glVertexAttribIPointer (Integer专用)，
 * 对 Weights 和其他属性使用 glVertexAttribPointer (Float专用)。
 */
public class GLSkinnedMesh extends GLMesh {

    public GLSkinnedMesh(int initialVertices, VertexLayout layout) {
        // 调用父类，但父类构造函数会执行一次错误的通用配置（全部按 float 处理）
        super(initialVertices, layout);

        // 我们必须立即重新绑定并覆盖属性配置，确保 BoneIDs 类型正确
        reconfigureAttributes(layout);

        InternalLogger.info("GLSkinnedMesh [" + name + "] initialized with dual-type attribute pointer logic.");
    }

    /**
     * 重新配置 VAO 属性。
     * 这是工业级实现的关键：根据 VertexAttribute 的类型位（Type）决定调用哪个 GL 函数。
     */
    private void reconfigureAttributes(VertexLayout layout) {
        this.bind();
        // 现在 getVboID() 已经存在于基类中了
        glBindBuffer(GL_ARRAY_BUFFER, getVboID());

        for (int i = 0; i < layout.getAttributes().size(); i++) {
            VertexAttribute attr = layout.getAttributes().get(i);
            glEnableVertexAttribArray(i);

            if (attr.type == GL_INT || attr.type == GL_UNSIGNED_INT || attr.type == GL_SHORT) {
                glVertexAttribIPointer(i, attr.count, attr.type, layout.getStride(), (long) attr.offset);
            } else {
                glVertexAttribPointer(i, attr.count, attr.type, attr.normalized, layout.getStride(), (long) attr.offset);
            }
        }
        this.unbind();
    }


    /**
     * 工业级数据上传：
     * 因为动画数据通常包含浮点（Pos/Weight）和整数（ID），
     * 如果你使用 float[] 数组传输，需要确保 BoneID 的比特位被正确保持。
     */
    @Override
    public void updateVertices(float[] data) {
        super.updateVertices(data);
    }
}