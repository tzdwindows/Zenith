package com.zenith.render.backend.opengl;

import com.zenith.asset.AssetResource;
import com.zenith.core.RayTracingProvider;
import com.zenith.render.Camera;
import com.zenith.render.Mesh;
import com.zenith.render.backend.opengl.buffer.GLSSBO;
import com.zenith.render.backend.opengl.shader.GLComputeShader;
import org.joml.Vector3f;

import java.io.IOException;
import java.util.*;

import static org.lwjgl.opengl.GL43.*;

public class SoftwarePathTracerProvider implements RayTracingProvider {

    private GLComputeShader computeShader;
    private GLSSBO vertexBuffer;
    private GLSSBO bvhBuffer;

    private int sampleCount = 0;
    private int vertexCount = 0;
    private final int vertexStride = 12; // 必须是 12 (Pos+Norm+Tex+Col)

    @Override
    public void init(int width, int height) {
        try {
            String source = AssetResource
                    .loadFromResources("shaders/rt/pathtrace.comp")
                    .readAsString();

            Map<String, String> defines = new HashMap<>();
            defines.put("SCREEN_WIDTH", String.valueOf(width));
            defines.put("SCREEN_HEIGHT", String.valueOf(height));

            computeShader = new GLComputeShader("SoftwarePathTracer", source, defines);
            vertexBuffer = new GLSSBO(2);
            bvhBuffer = new GLSSBO(3);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void buildAccelerationStructures(List<Mesh> meshes) {
        float[] originalVertices = GeomUtils.flatten(meshes);
        BVHBuilder builder = new BVHBuilder(originalVertices, vertexStride);

        // 构建 BVH 数据
        float[] bvhData = builder.build();

        // 根据 BVH 排序后的索引重排顶点，确保缓存局部性
        float[] reorderedVertices = new float[originalVertices.length];
        int[] finalIndices = builder.getTriIndices();
        for (int i = 0; i < finalIndices.length; i++) {
            int oldIdx = finalIndices[i];
            // 每一个三角形 index 对应 3 个顶点，每个顶点偏移 vertexStride
            System.arraycopy(originalVertices, oldIdx * 3 * vertexStride,
                    reorderedVertices, i * 3 * vertexStride, 3 * vertexStride);
        }
        vertexCount = reorderedVertices.length / vertexStride;

        vertexBuffer.setData(reorderedVertices);
        bvhBuffer.setData(bvhData);

        resetAccumulation();
    }

    @Override
    public void trace(SceneFramebuffer fbo, Camera camera) {
        sampleCount++;
        computeShader.bind();
        vertexBuffer.bind();
        bvhBuffer.bind();

        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, fbo.getSceneTextureID());
        computeShader.setUniform("u_BaseColor", 1);

        glBindImageTexture(0, fbo.getRayTraceTargetID(), 0, false, 0, GL_READ_WRITE, GL_RGBA16F);

        // 设置相机 Uniforms
        computeShader.setUniform("camera.position", camera.getTransform().getPosition());
        computeShader.setUniform("camera.forward", camera.getForward());
        computeShader.setUniform("camera.up", camera.getUp());
        computeShader.setUniform("camera.right", camera.getRight());
        computeShader.setUniform("camera.fov", camera.getProjection().getFov());
        // 如果你的相机没有设置这些，给默认值防止 NaN
        computeShader.setUniform("camera.focalDist", 1.0f);
        computeShader.setUniform("camera.aperture", 0.0f);

        computeShader.setUniform("u_VertexCount", vertexCount);
        computeShader.setUniform("u_SampleCount", sampleCount);
        computeShader.setUniform("u_VertexStride", vertexStride);
        computeShader.setUniform("numOfLights", 0); // 暂时没有灯光

        glDispatchCompute((int)Math.ceil(fbo.getWidth()/8.0), (int)Math.ceil(fbo.getHeight()/8.0), 1);
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

    // --- BVH 构建逻辑 ---
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
            buildRecursive(0, triIndices.length);
            float[] data = new float[nodes.size() * 8];
            for (int i = 0; i < nodes.size(); i++) {
                BVHNode n = nodes.get(i);
                int offset = i * 8;
                data[offset]     = n.min.x;
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

        private int buildRecursive(int start, int count) {
            BVHNode node = new BVHNode();
            int nodeIdx = nodes.size();
            nodes.add(node);
            updateNodeBounds(node, start, count);

            if (count <= 2) {
                node.leftFirst = start;
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
                node.leftFirst = nodes.size();
                buildRecursive(start, leftCount);
                buildRecursive(start + leftCount, count - leftCount);
            }
            return nodeIdx;
        }

        private void updateNodeBounds(BVHNode node, int start, int count) {
            node.min.set(Float.MAX_VALUE);
            node.max.set(-Float.MAX_VALUE);
            for (int i = 0; i < count; i++) {
                int triIdx = triIndices[start + i];
                for (int v = 0; v < 3; v++) {
                    int base = (triIdx * 3 + v) * stride;
                    node.min.min(new Vector3f(vertices[base], vertices[base+1], vertices[base+2]));
                    node.max.max(new Vector3f(vertices[base], vertices[base+1], vertices[base+2]));
                }
            }
        }

        private float getTriCentroid(int triIdx, int axis) {
            float c = 0;
            for (int v = 0; v < 3; v++) {
                int base = (triIdx * 3 + v) * stride;
                c += (axis == 0) ? vertices[base] : (axis == 1) ? vertices[base+1] : vertices[base+2];
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

        public int[] getTriIndices() { return triIndices; }
    }

    private static class BVHNode {
        Vector3f min = new Vector3f(), max = new Vector3f();
        int leftFirst, triCount;
    }
}