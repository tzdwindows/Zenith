package com.zenith.render;

import org.joml.Matrix4f;

/**
 * Renderer 增强版：支持渲染统计数据采集
 */
public abstract class Renderer {

    // --- 统计数据 ---
    protected int drawCalls = 0;
    protected int vertexCount = 0;
    protected int triangleCount = 0;

    public abstract void setViewProjection(Matrix4f matrix);

    /** 提交一个 Mesh 进行绘制，并应用指定的材质和变换 */
    public abstract void submit(Mesh mesh, Material material, com.zenith.common.math.Transform transform);

    /** * 刷新渲染队列，真正执行 GPU 指令。
     * 注意：在 flush 结束后，通常需要手动调用 resetStats() 为下一帧做准备。
     */
    public abstract void flush();
    public void resetStats() {
        drawCalls = 0;
        vertexCount = 0;
        triangleCount = 0;
    }

    public int getDrawCalls() { return drawCalls; }
    public int getVertexCount() { return vertexCount; }
    public int getTriangleCount() { return triangleCount; }
}