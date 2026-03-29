package com.zenith.render.backend.opengl.buffer;

import com.zenith.asset.AssetResource;
import com.zenith.render.VertexLayout;
import com.zenith.render.backend.opengl.texture.GLTexture;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class GLBufferBuilder {
    private ByteBuffer buffer;
    private int capacity;
    private int vertices;
    private VertexLayout layout;
    private boolean building;
    private GLTexture currentTexture;

    public GLBufferBuilder(int initialCapacity) {
        this.capacity = initialCapacity;
        this.buffer = MemoryUtil.memAlloc(initialCapacity);
        this.buffer.order(ByteOrder.nativeOrder());
    }

    /**
     * 允许直接传入 AssetResource。
     * 内部会自动将其转换为 GLTexture 并关联到当前的绘制批次。
     */
    public GLBufferBuilder withTexture(AssetResource resource) throws IOException {
        this.currentTexture = new GLTexture(resource);
        return this;
    }

    /**
     * 也可以直接关联已有的 GLTexture 对象
     */
    public GLBufferBuilder withTexture(GLTexture texture) {
        this.currentTexture = texture;
        return this;
    }

    public void begin(VertexLayout layout) {
        if (building) throw new IllegalStateException("Already building!");
        this.building = true;
        this.layout = layout;
        this.vertices = 0;
        this.currentTexture = null; // 每次开始重置纹理引用

        this.buffer.clear();
        this.buffer.order(ByteOrder.nativeOrder());
    }

    public GLTexture getCurrentTexture() {
        return currentTexture;
    }

    private void ensureCapacity(int additionalBytes) {
        if (buffer.position() + additionalBytes > capacity) {
            int currentPos = buffer.position();
            int newSize = capacity * 2 + additionalBytes;
            this.buffer = MemoryUtil.memRealloc(buffer, newSize);
            this.capacity = newSize;

            this.buffer.position(currentPos);
            this.buffer.order(ByteOrder.nativeOrder());
        }
    }

    public GLBufferBuilder putFloat(float value) {
        ensureCapacity(4);
        buffer.putFloat(value);
        return this;
    }

    public void endVertex() {
        this.vertices++;
    }

    public RenderedBuffer end() {
        if (!building) throw new IllegalStateException("Not building!");
        this.building = false;

        this.buffer.flip();
        // 将当前的纹理信息也封装进 RenderedBuffer
        return new RenderedBuffer(
                buffer.slice().order(ByteOrder.nativeOrder()),
                layout,
                vertices,
                currentTexture
        );
    }

    public void dispose() {
        if (buffer != null) {
            MemoryUtil.memFree(buffer);
            buffer = null;
        }
    }

    /**
     * 获取当前是否处于构建状态
     */
    public boolean isBuilding() {
        return building;
    }

    /**
     * 获取当前批次已经填充的顶点数量
     */
    public int getVertexCount() {
        return vertices;
    }

    /**
     * 获取当前缓冲区已写入的字节数
     */
    public int getUsedBytes() {
        return buffer != null ? buffer.position() : 0;
    }

    /**
     * 获取当前缓冲区的总容量（字节）
     */
    public int getCapacity() {
        return capacity;
    }

    /**
     * [修改后的 Record] 增加了 texture 字段
     */
    public static record RenderedBuffer(
            ByteBuffer data,
            VertexLayout layout,
            int vertexCount,
            GLTexture texture
    ) {}
}