package com.zenith.render;

import com.zenith.common.math.Color;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * TextRenderer 负责将字符串转化为顶点数据并提交给 GPU。
 */
public abstract class TextRenderer {

    /**
     * 在屏幕指定位置绘制文字
     * @param text  文本内容
     * @param x     X 坐标
     * @param y     Y 坐标
     * @param font  使用的字体资源
     * @param color 文字颜色
     */
    public abstract void drawString(String text, float x, float y, Font font, Color color);

    public abstract void drawString3D(String text, Vector3f position, Quaternionf rotation, Camera camera, Font font, Color color, float thickness);

    /**
     * 在 3D 空间中的某个位置绘制文字
     * @param text  文本内容
     * @param position  世界空间中的 3D 坐标
     * @param camera  当前观察场景的相机
     * @param font  使用的字体资源
     * @param color 颜色
     * @param thickness  线宽
     */
    public abstract void drawString3D(String text, Vector3f position, Camera camera, Font font, Color color, float thickness);

    /**
     * 开启文字渲染批处理（在 RenderLoop 中调用）
     */
    public abstract void begin();

    /**
     * 提交所有待绘制的文字到 GPU
     */
    public abstract void end();
}