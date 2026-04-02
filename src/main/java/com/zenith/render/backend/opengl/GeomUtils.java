package com.zenith.render.backend.opengl;

import com.zenith.render.Mesh;
import com.zenith.render.VertexAttribute;
import com.zenith.render.VertexLayout;

import java.util.ArrayList;
import java.util.List;

public class GeomUtils {

    /**
     * 将多个 Mesh 拍平成适合 SSBO 传输的 float 数组
     * 目标格式 (每顶点 12 个 float):
     * Pos(4) [x,y,z,1] + Normal(4) [x,y,z,0] + UV(4) [u,v,0,0]
     */
    public static float[] flatten(List<Mesh> meshes) {
        List<Float> combinedData = new ArrayList<>();

        for (Mesh mesh : meshes) {
            if (!(mesh instanceof GLMesh glMesh)) continue;

            float[] vertices = glMesh.getVertices();
            int[] indices = glMesh.getIndices();
            VertexLayout layout = glMesh.getLayout();

            if (vertices == null) continue;

            // 1. 获取属性在原有布局中的偏移量
            int stride = layout.getStride() / 4; // float步长
            int posOff = getOffset(layout, "aPos");
            int normOff = getOffset(layout, "aNormal");
            int uvOff = getOffset(layout, "aTexCoord");

            // 2. 如果有索引，根据索引重新组装成三角形汤
            if (indices != null && indices.length > 0) {
                for (int index : indices) {
                    processVertex(combinedData, vertices, index, stride, posOff, normOff, uvOff);
                }
            } else {
                // 如果没有索引，按顺序处理
                int vertexCount = vertices.length / stride;
                for (int i = 0; i < vertexCount; i++) {
                    processVertex(combinedData, vertices, i, stride, posOff, normOff, uvOff);
                }
            }
        }

        // 转换为原生数组
        float[] result = new float[combinedData.size()];
        for (int i = 0; i < combinedData.size(); i++) {
            result[i] = combinedData.get(i);
        }
        return result;
    }

    private static void processVertex(List<Float> out, float[] src, int vIdx, int stride, int pOff, int nOff, int tOff) {
        int base = vIdx * stride;

        // Pos (4 floats - 保持 vec4 对齐)
        out.add(pOff != -1 ? src[base + pOff] : 0f);
        out.add(pOff != -1 ? src[base + pOff + 1] : 0f);
        out.add(pOff != -1 ? src[base + pOff + 2] : 0f);
        out.add(1.0f); // w

        // Normal (4 floats)
        out.add(nOff != -1 ? src[base + nOff] : 0f);
        out.add(nOff != -1 ? src[base + nOff + 1] : 0f);
        out.add(nOff != -1 ? src[base + nOff + 2] : 0f);
        out.add(0.0f); // pad

        // UV (4 floats)
        out.add(tOff != -1 ? src[base + tOff] : 0f);
        out.add(tOff != -1 ? src[base + tOff + 1] : 0f);
        out.add(0.0f); // pad
        out.add(0.0f); // pad
    }

    private static int getOffset(VertexLayout layout, String name) {
        int offset = 0;
        for (VertexAttribute attr : layout.getAttributes()) {
            if (attr.name.equals(name)) return offset / 4;
            offset += attr.count * 4; // 这里假设都是 float 类型
        }
        return -1;
    }
}