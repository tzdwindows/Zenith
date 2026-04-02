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
        // 预估大小：每个顶点 12 个 float (Pos4, Norm4, UV4)
        int totalVertices = 0;
        for (Mesh m : meshes) totalVertices += (m instanceof GLMesh gm && gm.getIndices() != null) ? gm.getIndices().length : m.getVertexCount();

        float[] result = new float[totalVertices * 12];
        int offset = 0;

        for (Mesh mesh : meshes) {
            if (!(mesh instanceof GLMesh glMesh)) continue;
            float[] src = glMesh.getVertices();
            int[] indices = glMesh.getIndices();
            VertexLayout layout = glMesh.getLayout();
            int stride = layout.getStride() / 4;

            int pOff = getOffset(layout, "aPos");
            int nOff = getOffset(layout, "aNormal");
            int tOff = getOffset(layout, "aTexCoord");

            if (indices != null) {
                // 👉 Indexed mesh
                for (int idx : indices) {
                    int base = idx * stride;

                    // Position
                    result[offset++] = src[base + pOff];
                    result[offset++] = src[base + pOff + 1];
                    result[offset++] = src[base + pOff + 2];
                    result[offset++] = 1.0f;

                    // Normal
                    result[offset++] = (nOff != -1) ? src[base + nOff] : 0;
                    result[offset++] = (nOff != -1) ? src[base + nOff + 1] : 0;
                    result[offset++] = (nOff != -1) ? src[base + nOff + 2] : 0;
                    result[offset++] = 0.0f;

                    // UV
                    result[offset++] = (tOff != -1) ? src[base + tOff] : 0;
                    result[offset++] = (tOff != -1) ? src[base + tOff + 1] : 0;
                    result[offset++] = 0.0f;
                    result[offset++] = 0.0f;
                }
            } else {
                // 👉 非 indexed mesh（🔥 你缺的就是这个）
                int vertexCount = mesh.getVertexCount();

                for (int v = 0; v < vertexCount; v++) {
                    int base = v * stride;

                    // Position
                    result[offset++] = src[base + pOff];
                    result[offset++] = src[base + pOff + 1];
                    result[offset++] = src[base + pOff + 2];
                    result[offset++] = 1.0f;

                    // Normal
                    result[offset++] = (nOff != -1) ? src[base + nOff] : 0;
                    result[offset++] = (nOff != -1) ? src[base + nOff + 1] : 0;
                    result[offset++] = (nOff != -1) ? src[base + nOff + 2] : 0;
                    result[offset++] = 0.0f;

                    // UV
                    result[offset++] = (tOff != -1) ? src[base + tOff] : 0;
                    result[offset++] = (tOff != -1) ? src[base + tOff + 1] : 0;
                    result[offset++] = 0.0f;
                    result[offset++] = 0.0f;
                }
            }
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
            offset += attr.count * 4;
        }
        return -1;
    }
}