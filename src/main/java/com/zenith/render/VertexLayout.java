package com.zenith.render;

import com.zenith.common.utils.InternalLogger;
import java.util.ArrayList;
import java.util.List;

/**
 * VertexLayout 定义了完整顶点的内存结构。
 * 它计算 Stride（每个顶点的总字节数）和每个属性的 Offset。
 */
public class VertexLayout {

    private final List<VertexAttribute> attributes = new ArrayList<>();
    private int stride = 0;

    public VertexLayout() {
        // 构造函数
    }

    /**
     * 向布局中添加一个浮点属性。
     * @param name   属性名
     * @param count  组件数 (如 Vector3f 传入 3)
     */
    public void pushFloat(String name, int count) {
        pushFloat(name, count, false);
    }

    public void pushFloat(String name, int count, boolean normalized) {
        // 0x1406 是 OpenGL 中 GL_FLOAT 的标准常量值
        VertexAttribute attr = new VertexAttribute(name, count, 0x1406, normalized);
        attr.offset = stride;

        attributes.add(attr);
        stride += attr.getSizeInBytes();

        InternalLogger.debug(String.format("VertexLayout: Added '%s' (count: %d, offset: %d)",
                name, count, attr.offset));
    }

    public List<VertexAttribute> getAttributes() {
        return attributes;
    }

    /** 获取一个顶点占用的总字节数 */
    public int getStride() {
        return stride;
    }

    public void clear() {
        attributes.clear();
        stride = 0;
    }
}