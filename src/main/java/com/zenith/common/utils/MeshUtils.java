package com.zenith.common.utils;

import com.zenith.render.VertexLayout;

public class MeshUtils {
    /**
     * 生成球体数据
     * @param radius 半径
     * @param sectors 经度分段 (纵向切片)
     * @param stacks 纬度分段 (横向切片)
     * @return 符合 20-float 结构的顶点数组
     */
    public static float[] generateSphereData(float radius, int sectors, int stacks) {
        // 1. 计算临时顶点信息
        int vertexCount = (stacks + 1) * (sectors + 1);
        float[] tempVertices = new float[vertexCount * 20];

        float x, y, z, xy;                              // 位置
        float nx, ny, nz, lengthInv = 1.0f / radius;    // 法线
        float s, t;                                     // UV

        float sectorStep = 2 * (float)Math.PI / sectors;
        float stackStep = (float)Math.PI / stacks;
        float sectorAngle, stackAngle;

        int k = 0;
        for(int i = 0; i <= stacks; ++i) {
            stackAngle = (float)Math.PI / 2 - i * stackStep;        // 从 pi/2 到 -pi/2
            xy = radius * (float)Math.cos(stackAngle);              // r * cos(u)
            z = radius * (float)Math.sin(stackAngle);               // r * sin(u)

            for(int j = 0; j <= sectors; ++j) {
                sectorAngle = j * sectorStep;                       // 从 0 到 2pi

                // 位置 (Pos)
                x = xy * (float)Math.cos(sectorAngle);              // x = r * cos(u) * cos(v)
                y = xy * (float)Math.sin(sectorAngle);              // y = r * cos(u) * sin(v)

                // 你的 layout 是 (x, y, z)，注意轴向匹配
                tempVertices[k] = x;
                tempVertices[k + 1] = y;
                tempVertices[k + 2] = z;

                // 法线 (Normal) - 对于球体，法线就是归一化的位置
                tempVertices[k + 3] = x * lengthInv;
                tempVertices[k + 4] = y * lengthInv;
                tempVertices[k + 5] = z * lengthInv;

                // 纹理坐标 (UV)
                s = (float)j / sectors;
                t = (float)i / stacks;
                tempVertices[k + 6] = s;
                tempVertices[k + 7] = t;

                // 颜色 (Color) - 默认白色
                tempVertices[k + 8] = 1.0f;
                tempVertices[k + 9] = 1.0f;
                tempVertices[k + 10] = 1.0f;
                tempVertices[k + 11] = 1.0f;

                // 骨骼 ID 和权重 (BoneIds, Weights) - 填充 0
                for(int m = 12; m < 20; m++) tempVertices[k + m] = 0.0f;

                k += 20;
            }
        }

        // 2. 生成索引并展开为三角形数组
        int[] indices = new int[stacks * sectors * 6];
        int p = 0;
        for(int i = 0; i < stacks; ++i) {
            int k1 = i * (sectors + 1);     // 当前层起始索引
            int k2 = k1 + sectors + 1;      // 下一层起始索引

            for(int j = 0; j < sectors; ++j, ++k1, ++k2) {
                // 每格两个三角形
                if(i != 0) {
                    indices[p++] = k1;
                    indices[p++] = k2;
                    indices[p++] = k1 + 1;
                }
                if(i != (stacks - 1)) {
                    indices[p++] = k1 + 1;
                    indices[p++] = k2;
                    indices[p++] = k2 + 1;
                }
            }
        }

        // 3. 按照索引重组数据
        float[] res = new float[indices.length * 20];
        for (int i = 0; i < indices.length; i++) {
            System.arraycopy(tempVertices, indices[i] * 20, res, i * 20, 20);
        }
        return res;
    }

    /**
     * 创建标准顶点布局
     */
    public static VertexLayout createStandardLayout() {
        VertexLayout layout = new VertexLayout();
        layout.pushFloat("aPos", 3);
        layout.pushFloat("aNormal", 3);
        layout.pushFloat("aTexCoord", 2);
        layout.pushFloat("aColor", 4);
        layout.pushFloat("aBoneIds", 4);
        layout.pushFloat("aWeights", 4);
        return layout;
    }

    /**
     * 生成平面数据 (搬运自原 Test.java)
     */
    public static float[] generatePlaneData(float w, float d, int xn, int zn) {
        float[] vertices = new float[(xn + 1) * (zn + 1) * 20];
        for (int z = 0; z <= zn; z++) {
            for (int x = 0; x <= xn; x++) {
                int i = (z * (xn + 1) + x) * 20;
                vertices[i] = (float) x / xn * w - w / 2f;
                vertices[i + 1] = 0;
                vertices[i + 2] = (float) z / zn * d - d / 2f;
                vertices[i + 3] = 0; vertices[i + 4] = 1.0f; vertices[i + 5] = 0;
                vertices[i + 6] = (float) x / xn; vertices[i + 7] = (float) z / zn;
            }
        }
        int[] indices = new int[xn * zn * 6];
        int p = 0;
        for (int z = 0; z < zn; z++) {
            for (int x = 0; x < xn; x++) {
                int s = z * (xn + 1) + x;
                indices[p++] = s; indices[p++] = s + xn + 1; indices[p++] = s + 1;
                indices[p++] = s + 1; indices[p++] = s + xn + 1; indices[p++] = s + xn + 2;
            }
        }
        float[] res = new float[indices.length * 20];
        for (int i = 0; i < indices.length; i++) System.arraycopy(vertices, indices[i] * 20, res, i * 20, 20);
        return res;
    }

    /**
     * 生成天空盒数据 (搬运自原 Test.java)
     */
    public static float[] generateSkyBoxData() {
        float[] c = {-1,1,-1, -1,-1,-1, 1,-1,-1, 1,-1,-1, 1,1,-1, -1,1,-1, -1,-1,1, -1,-1,-1, -1,1,-1, -1,1,-1, -1,1,1, -1,-1,1, 1,-1,-1, 1,-1,1, 1,1,1, 1,1,1, 1,1,-1, 1,-1,-1, -1,-1,1, -1,1,1, 1,1,1, 1,1,1, 1,-1,1, -1,-1,1, -1,1,-1, 1,1,-1, 1,1,1, 1,1,1, -1,1,1, -1,1,-1, -1,-1,-1, -1,-1,1, 1,-1,-1, 1,-1,-1, -1,-1,1, 1,-1,1};
        float[] d = new float[c.length / 3 * 20];
        for (int i = 0; i < c.length / 3; i++) {
            d[i*20] = c[i*3] * 900f;
            d[i*20+1] = c[i*3+1] * 900f;
            d[i*20+2] = c[i*3+2] * 900f;
        }
        return d;
    }
}