package com.zenith.render;

import com.zenith.common.math.Transform;

/**
 * 任何可以被渲染到屏幕上的物体都必须实现此接口。
 */
public interface Renderable {

    /** 返回物体的几何网格数据 */
    Mesh getMesh();

    /** 返回物体所使用的材质 */
    Material getMaterial();

    /** 返回物体在世界空间中的变换信息（位置、旋转、缩放） */
    Transform getTransform();
}