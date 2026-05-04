package com.zenith.render.backend.opengl;

import com.zenith.asset.AssetResource;
import com.zenith.core.RayTracingProvider;
import com.zenith.render.Camera;
import com.zenith.render.Mesh;
import com.zenith.render.VertexAttribute;
import com.zenith.render.VertexLayout;
import com.zenith.render.backend.opengl.buffer.GLSSBO;
import com.zenith.render.backend.opengl.shader.GLComputeShader;
import org.joml.Vector3f;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.opengl.GL43.*;

/**
 * Software path tracer (GPU compute shader) with CPU-side BVH build.
 *
 * Notes:
 * 1) This is used as "software ray tracing" in Zenith: geometry + BVH are built on CPU, tracing runs in compute.
 * 2) We pack extra per-vertex data (color + material id) to enable more realistic test scenes (water/model).
 */
public class SoftwarePathTracerProvider implements RayTracingProvider {

    private GLComputeShader computeShader;
    private GLSSBO vertexBuffer;
    private GLSSBO bvhBuffer;

    private int sampleCount = 0;
    private int vertexCount = 0;
    private int bvhNodeCount = 0;

    // RT vertex packing: Pos4 + Normal4 + TexCoord4 + Color4
    private static final int RT_STRIDE = 16;

    // Optional material/texture plumbing for more realistic path tracing tests.
    private final Map<Mesh, Integer> meshMaterialIds = new IdentityHashMap<>();
    private int albedoTextureId = 0;

    @Override
    public void init(int width, int height) {
        try {
            String source = AssetResource
                    .loadFromResources("shaders/rt/pathtrace.comp")
                    .readAsString();

            Map<String, String> defines = new HashMap<>();
            defines.put("SCREEN_WIDTH", String.valueOf(width));
            defines.put("SCREEN_HEIGHT", String.valueOf(height));
            String safeMode = System.getProperty("zenith.rt.safeMode", "0");
            if ("1".equals(safeMode) || "true".equalsIgnoreCase(safeMode)) {
                defines.put("ZENITH_RT_SAFE_MODE", "1");
            } else {
                defines.put("ZENITH_RT_SAFE_MODE", "0");
            }

            computeShader = new GLComputeShader("SoftwarePathTracer", source, defines);
            vertexBuffer = new GLSSBO(2);
            bvhBuffer = new GLSSBO(3);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void buildAccelerationStructures(List<Mesh> meshes) {
        float[] rtVertices = flattenRtVertices(meshes);
        BVHBuilder builder = new BVHBuilder(rtVertices, RT_STRIDE);
        float[] bvhData = builder.build();
        bvhNodeCount = bvhData.length / 8;

        // Important: BVHBuilder permutes triangle order via triIndices. The traversal shader
        // assumes triangles are stored linearly in the same order as the BVH leaf ranges.
        // So we must reorder the vertex array to match the final triIndices ordering.
        int[] triOrder = builder.getTriIndices();
        float[] reordered = new float[triOrder.length * 3 * RT_STRIDE];
        for (int i = 0; i < triOrder.length; i++) {
            int triIdx = triOrder[i];
            int srcTriBase = triIdx * 3 * RT_STRIDE;
            int dstTriBase = i * 3 * RT_STRIDE;
            System.arraycopy(rtVertices, srcTriBase, reordered, dstTriBase, 3 * RT_STRIDE);
        }

        vertexCount = reordered.length / RT_STRIDE;
        vertexBuffer.setData(reordered);
        bvhBuffer.setData(bvhData);
        resetAccumulation();
    }

    @Override
    public void trace(SceneFramebuffer fbo, Camera camera) {
        if (computeShader == null || vertexCount <= 0 || bvhNodeCount <= 0) {
            return;
        }
        sampleCount++;
        computeShader.bind();
        vertexBuffer.bind();
        bvhBuffer.bind();

        // Raster base-color buffer (hybrid mode uses it; full mode can still reuse it for debug).
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, fbo.getSceneTextureID());
        computeShader.setUniform("u_BaseColor", 1);

        // Optional albedo texture for full path tracing mode (material test).
        if (albedoTextureId != 0) {
            glActiveTexture(GL_TEXTURE4);
            glBindTexture(GL_TEXTURE_2D, albedoTextureId);
            computeShader.setUniform("u_AlbedoTex", 4);
            computeShader.setUniform("u_HasAlbedoTex", 1);
        } else {
            computeShader.setUniform("u_HasAlbedoTex", 0);
        }

        // We only write the output image in the path tracer.
        glBindImageTexture(0, fbo.getRayTraceTargetID(), 0, false, 0, GL_WRITE_ONLY, GL_RGBA16F);

        org.joml.Matrix4f invVP = new org.joml.Matrix4f(camera.getProjection().getMatrix())
                .mul(camera.getViewMatrix())
                .invert();
        computeShader.setUniform("u_InvViewProj", invVP);
        computeShader.setUniform("u_CamPos", camera.getTransform().getPosition());

        computeShader.setUniform("u_CamForward", camera.getForward());
        computeShader.setUniform("u_CamRight", camera.getRight());
        computeShader.setUniform("u_CamUp", camera.getUp());

        computeShader.setUniform("u_MaxBounces", 3);
        computeShader.setUniform("u_Aperture", 0.00f);
        computeShader.setUniform("u_FocalDist", 60.0f);

        org.joml.Vector3f sunDir = new org.joml.Vector3f(0.5f, 0.6f, -0.5f).normalize();
        computeShader.setUniform("u_SunDirection", sunDir);
        computeShader.setUniform("u_SunColor", new org.joml.Vector3f(15.0f, 13.0f, 10.0f));
        computeShader.setUniform("u_SunRadius", 0.03f);
        computeShader.setUniform("u_RTMode", com.zenith.common.config.RayTracingConfig.RT_MODE);

        computeShader.setUniform("u_VertexCount", vertexCount);
        computeShader.setUniform("u_BvhNodeCount", bvhNodeCount);
        computeShader.setUniform("u_SampleCount", sampleCount);

        glDispatchCompute((int) Math.ceil(fbo.getWidth() / 8.0), (int) Math.ceil(fbo.getHeight() / 8.0), 1);
        glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);
    }

    @Override
    public void dispose() {
        if (computeShader != null) computeShader.dispose();
        if (vertexBuffer != null) vertexBuffer.dispose();
        if (bvhBuffer != null) bvhBuffer.dispose();
    }

    public void resetAccumulation() { sampleCount = 0; }
    public int getSampleCount() { return sampleCount; }

    public void setMeshMaterialId(Mesh mesh, int materialId) {
        if (mesh != null) meshMaterialIds.put(mesh, materialId);
    }

    public void setAlbedoTextureId(int textureId) {
        this.albedoTextureId = textureId;
    }

    private float[] flattenRtVertices(List<Mesh> meshes) {
        if (meshes == null || meshes.isEmpty()) return new float[0];

        int totalVerts = 0;
        for (Mesh m : meshes) {
            if (m instanceof GLMesh gm) {
                int[] idx = gm.getIndices();
                totalVerts += (idx != null && idx.length > 0) ? idx.length : m.getVertexCount();
            } else {
                totalVerts += m.getVertexCount();
            }
        }

        float[] out = new float[totalVerts * RT_STRIDE];
        int o = 0;

        for (Mesh m : meshes) {
            if (!(m instanceof GLMesh mesh)) continue;
            float[] src = mesh.getVertices();
            if (src == null || src.length == 0) continue;

            int[] indices = mesh.getIndices();
            VertexLayout layout = mesh.getLayout();
            int stride = layout.getStride() / 4;

            int pOff = getOffset(layout, "aPos");
            int nOff = getOffset(layout, "aNormal");
            int tOff = getOffset(layout, "aTexCoord");
            int cOff = getOffset(layout, "aColor");

            float matId = meshMaterialIds.getOrDefault(m, 0);

            if (indices != null && indices.length > 0) {
                for (int idx : indices) {
                    int base = idx * stride;
                    o = writeVertex(out, o, src, base, pOff, nOff, tOff, cOff, matId);
                }
            } else {
                int vCount = m.getVertexCount();
                for (int v = 0; v < vCount; v++) {
                    int base = v * stride;
                    o = writeVertex(out, o, src, base, pOff, nOff, tOff, cOff, matId);
                }
            }
        }

        return out;
    }

    private static int writeVertex(float[] out, int o, float[] src, int base, int pOff, int nOff, int tOff, int cOff, float matId) {
        // position vec4
        out[o++] = (pOff != -1) ? src[base + pOff] : 0;
        out[o++] = (pOff != -1) ? src[base + pOff + 1] : 0;
        out[o++] = (pOff != -1) ? src[base + pOff + 2] : 0;
        out[o++] = 1.0f;

        // normal vec4
        out[o++] = (nOff != -1) ? src[base + nOff] : 0;
        out[o++] = (nOff != -1) ? src[base + nOff + 1] : 1.0f;
        out[o++] = (nOff != -1) ? src[base + nOff + 2] : 0;
        out[o++] = 0.0f;

        // texCoord vec4 (z carries material id)
        out[o++] = (tOff != -1) ? src[base + tOff] : 0;
        out[o++] = (tOff != -1) ? src[base + tOff + 1] : 0;
        out[o++] = matId;
        out[o++] = 0.0f;

        // color vec4 (fallback white)
        out[o++] = (cOff != -1) ? src[base + cOff] : 1.0f;
        out[o++] = (cOff != -1) ? src[base + cOff + 1] : 1.0f;
        out[o++] = (cOff != -1) ? src[base + cOff + 2] : 1.0f;
        out[o++] = (cOff != -1) ? src[base + cOff + 3] : 1.0f;
        return o;
    }

    private static int getOffset(VertexLayout layout, String name) {
        int offset = 0;
        for (VertexAttribute attr : layout.getAttributes()) {
            if (attr.name.equals(name)) return offset / 4;
            offset += attr.count * 4;
        }
        return -1;
    }

    // --- BVH build (simple median split; deterministic and fast enough for test scenes) ---
    private static class BVHBuilder {
        private final float[] vertices;
        private final List<BVHNode> nodes = new ArrayList<>();
        private final int[] triIndices;
        private final int stride;

        public BVHBuilder(float[] vertices, int stride) {
            this.vertices = vertices;
            this.stride = stride;
            int triCount = (vertices.length / stride) / 3;
            this.triIndices = new int[triCount];
            for (int i = 0; i < triCount; i++) triIndices[i] = i;
        }

        public float[] build() {
            // 1. 初始化根节点
            nodes.add(new BVHNode());
            buildRecursive(0, 0, triIndices.length);

            float[] data = new float[nodes.size() * 8];
            for (int i = 0; i < nodes.size(); i++) {
                BVHNode n = nodes.get(i);
                int offset = i * 8;
                data[offset] = n.min.x;
                data[offset + 1] = n.min.y;
                data[offset + 2] = n.min.z;
                data[offset + 3] = n.leftFirst;
                data[offset + 4] = n.max.x;
                data[offset + 5] = n.max.y;
                data[offset + 6] = n.max.z;
                data[offset + 7] = n.triCount;
            }
            return data;
        }

        public int[] getTriIndices() { return triIndices; }

        private void buildRecursive(int nodeIdx, int start, int count) {
            BVHNode node = nodes.get(nodeIdx);
            updateNodeBounds(node, start, count);

            if (count <= 2) {
                node.leftFirst = start; // 叶子节点：leftFirst 表示三角形起始索引
                node.triCount = count;
            } else {
                node.triCount = 0;
                int axis = getLongestAxis(node);
                float splitPos = getCentroid(node, axis);
                int i = start, j = start + count - 1;
                while (i <= j) {
                    if (getTriCentroid(triIndices[i], axis) < splitPos) i++;
                    else { swap(i, j); j--; }
                }
                int leftCount = (i - start == 0 || i - start == count) ? count / 2 : i - start;

                // 【核心修复】：先连续分配两个子节点，保证 right_child == left_child + 1
                int leftFirst = nodes.size();
                nodes.add(new BVHNode()); // 左子节点位于 leftFirst
                nodes.add(new BVHNode()); // 右子节点位于 leftFirst + 1
                node.leftFirst = leftFirst;

                // 递归构建内容
                buildRecursive(leftFirst, start, leftCount);
                buildRecursive(leftFirst + 1, start + leftCount, count - leftCount);
            }
        }

        private void updateNodeBounds(BVHNode node, int start, int count) {
            node.min.set(Float.MAX_VALUE);
            node.max.set(-Float.MAX_VALUE);
            for (int i = 0; i < count; i++) {
                int triIdx = triIndices[start + i];
                for (int v = 0; v < 3; v++) {
                    int base = (triIdx * 3 + v) * stride;
                    node.min.min(new Vector3f(vertices[base], vertices[base + 1], vertices[base + 2]));
                    node.max.max(new Vector3f(vertices[base], vertices[base + 1], vertices[base + 2]));
                }
            }
        }

        private float getTriCentroid(int triIdx, int axis) {
            float c = 0;
            for (int v = 0; v < 3; v++) {
                int base = (triIdx * 3 + v) * stride;
                c += (axis == 0) ? vertices[base] : (axis == 1) ? vertices[base + 1] : vertices[base + 2];
            }
            return c / 3.0f;
        }

        private float getCentroid(BVHNode n, int axis) {
            if (axis == 0) return (n.min.x + n.max.x) * 0.5f;
            if (axis == 1) return (n.min.y + n.max.y) * 0.5f;
            return (n.min.z + n.max.z) * 0.5f;
        }

        private void swap(int i, int j) {
            int t = triIndices[i]; triIndices[i] = triIndices[j]; triIndices[j] = t;
        }

        private int getLongestAxis(BVHNode n) {
            float x = n.max.x - n.min.x, y = n.max.y - n.min.y, z = n.max.z - n.min.z;
            return (x > y && x > z) ? 0 : (y > z) ? 1 : 2;
        }
    }

    private static class BVHNode {
        Vector3f min = new Vector3f(), max = new Vector3f();
        int leftFirst, triCount;
    }
}
