package com.zenith.render;

import com.zenith.common.utils.InternalLogger;

public abstract class Mesh {
    protected int vertexCount;

    protected Mesh(int vertexCount) {
        this.vertexCount = vertexCount;
        InternalLogger.info("Creating Mesh with " + vertexCount + " vertices.");
    }

    /** 执行绘制指令 */
    public abstract void render();

    public abstract void dispose();

    public int getVertexCount() { return vertexCount; }
}