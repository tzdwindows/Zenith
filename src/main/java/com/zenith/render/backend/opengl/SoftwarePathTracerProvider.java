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
    private GLSSBO bvhBuffer; // 新增：存放 BVH 节点的 SSBO

    private int sampleCount = 0;
    private int vertexCount = 0;

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

            // 绑定点 2 对应着色器中的 VertexBuffer
            vertexBuffer = new GLSSBO(2);
            // 绑定点 3 对应着色器中的 BVHBuffer
            bvhBuffer = new GLSSBO(3);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void buildAccelerationStructures(List<Mesh> meshes) {
        float[] originalVertices = GeomUtils.flatten(meshes);
        BVHBuilder builder = new BVHBuilder(originalVertices);

        // 1. 构建 BVH (这个方法现在会修改内部的 triIndices 顺序)
        float[] bvhData = builder.build();

        // 2. 根据 BVH 排序后的索引，重排顶点数据
        float[] reorderedVertices = new float[originalVertices.length];
        int[] finalIndices = builder.getTriIndices();
        for (int i = 0; i < finalIndices.length; i++) {
            int oldIdx = finalIndices[i];
            // 每个三角形 3 个顶点，每个顶点 12 个 float
            System.arraycopy(originalVertices, oldIdx * 3 * 12, reorderedVertices, i * 3 * 12, 3 * 12);
        }

        // 3. 上传数据
        vertexCount = reorderedVertices.length / 12;
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
        glBindTexture(GL_TEXTURE_2D, fbo.getSceneTextureID()); // 绑定光栅化生成的颜色贴图作为 BaseColor
        computeShader.setUniform("u_BaseColor", 1);

        glBindImageTexture(0, fbo.getRayTraceTargetID(), 0, false, 0, GL_READ_WRITE, GL_RGBA16F);

        // 修正 Uniform 路径 (结构体成员必须带 .)
        computeShader.setUniform("camera.position", camera.getTransform().getPosition());
        computeShader.setUniform("camera.forward", camera.getForward());
        computeShader.setUniform("camera.up", camera.getUp());
        computeShader.setUniform("camera.right", camera.getRight());
        computeShader.setUniform("camera.fov", camera.getProjection().getFov());

        computeShader.setUniform("u_VertexCount", vertexCount);
        computeShader.setUniform("u_SampleCount", sampleCount);

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

    // --- BVH 构建核心逻辑 ---
    private static class BVHBuilder {
        private final float[] vertices;
        private final List<BVHNode> nodes = new ArrayList<>();
        private final int[] triIndices;

        public BVHBuilder(float[] vertices) {
            this.vertices = vertices;
            int triCount = (vertices.length / 12) / 3;
            this.triIndices = new int[triCount];
            for (int i = 0; i < triCount; i++) triIndices[i] = i;
        }

        public float[] build() {
            // 预分配根节点，保证递归逻辑整洁
            buildRecursive(0, triIndices.length);

            float[] data = new float[nodes.size() * 8];
            for (int i = 0; i < nodes.size(); i++) {
                BVHNode n = nodes.get(i);
                int offset = i * 8;
                data[offset]     = n.min.x;
                data[offset + 1] = n.min.y;
                data[offset + 2] = n.min.z;
                data[offset + 3] = n.leftFirst; // 内部节点指向左子节点索引，叶子指向三角形起始位置
                data[offset + 4] = n.max.x;
                data[offset + 5] = n.max.y;
                data[offset + 6] = n.max.z;
                data[offset + 7] = n.triCount;  // 0 为内部节点，>0 为叶子节点
            }
            return data;
        }

        private int buildRecursive(int start, int count) {
            BVHNode node = new BVHNode();
            int nodeIdx = nodes.size();
            nodes.add(node);

            // 1. 计算当前范围内所有三角形的 AABB
            updateNodeBounds(node, start, count);

            // 2. 终止条件：三角形数量少于阈值
            if (count <= 2) {
                node.leftFirst = start;
                node.triCount = count;
            } else {
                // 3. 内部节点逻辑
                node.triCount = 0;
                int axis = getLongestAxis(node);

                // 计算划分平面：取当前包围盒最长轴的中点
                float splitPos = getCentroid(node, axis);

                // 4. 进行划分 (Partition)
                // 将 triIndices 重新排列，使得左侧的三角形重心都在 splitPos 左边
                int i = start;
                int j = start + count - 1;
                while (i <= j) {
                    float centroid = getTriCentroid(triIndices[i], axis);
                    if (centroid < splitPos) {
                        i++;
                    } else {
                        swap(i, j);
                        j--;
                    }
                }

                // 5. 如果划分失败（比如所有三角形重心都在同一位置），强制平分
                int leftCount = i - start;
                if (leftCount == 0 || leftCount == count) {
                    leftCount = count / 2;
                }

                // 6. 关键修改：按顺序创建子节点，确保它们索引连续
                // 先记录当前节点将要指向的第一个子节点位置
                int firstChildIdx = nodes.size();
                node.leftFirst = firstChildIdx;

                // 递归构建左子树和右子树
                // 注意：这里必须紧接着调用，中间不能插入其他 nodes.add 操作
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
                    int base = (triIdx * 3 + v) * 12;
                    node.min.min(new Vector3f(vertices[base], vertices[base+1], vertices[base+2]));
                    node.max.max(new Vector3f(vertices[base], vertices[base+1], vertices[base+2]));
                }
            }
        }

        private float getTriCentroid(int triIdx, int axis) {
            float c = 0;
            for (int v = 0; v < 3; v++) {
                int base = (triIdx * 3 + v) * 12;
                if (axis == 0) c += vertices[base];
                else if (axis == 1) c += vertices[base+1];
                else c += vertices[base+2];
            }
            return c / 3.0f;
        }

        private float getCentroid(BVHNode n, int axis) {
            if (axis == 0) return (n.min.x + n.max.x) * 0.5f;
            if (axis == 1) return (n.min.y + n.max.y) * 0.5f;
            return (n.min.z + n.max.z) * 0.5f;
        }

        private void swap(int i, int j) {
            int temp = triIndices[i];
            triIndices[i] = triIndices[j];
            triIndices[j] = temp;
        }

        private int getLongestAxis(BVHNode n) {
            float x = n.max.x - n.min.x;
            float y = n.max.y - n.min.y;
            float z = n.max.z - n.min.z;
            if (x > y && x > z) return 0;
            return (y > z) ? 1 : 2;
        }

        public int[] getTriIndices() { return triIndices; }
    }

    private static class BVHNode {
        Vector3f min = new Vector3f();
        Vector3f max = new Vector3f();
        int leftFirst; // 内部节点指向子节点索引，叶子节点指向三角形索引
        int triCount;  // 0 表示内部节点
    }
}