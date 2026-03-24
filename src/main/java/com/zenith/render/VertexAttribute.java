package com.zenith.render;

public class VertexAttribute {
    public String name;
    public int count;      // 组件数量 (如: 3 代表 Vector3)
    public int type;       // 这里对应 OpenGL 的常量，如 5126 (GL_FLOAT)
    public boolean normalized;
    public int offset;

    public VertexAttribute(String name, int count, int type, boolean normalized) {
        this.name = name;
        this.count = count;
        this.type = type;
        this.normalized = normalized;
    }

    /** 对应之前报错的 getSize()，计算该属性的总字节数 */
    public int getSizeInBytes() {
        // 简单处理：假设 type 是 GL_FLOAT (4字节) 或 GL_UNSIGNED_BYTE (1字节)
        // 5126 是 GL_FLOAT, 5121 是 GL_UNSIGNED_BYTE
        int componentSize = (type == 5121) ? 1 : 4;
        return count * componentSize;
    }
}