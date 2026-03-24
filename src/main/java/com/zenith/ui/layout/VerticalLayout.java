package com.zenith.ui.layout;

import com.zenith.ui.component.UIComponent;
import com.zenith.ui.component.UIContainer;
import com.zenith.common.math.Rectf;

import java.util.List;

/**
 * 垂直布局管理器：将子组件从上到下排列。
 */
public class VerticalLayout {

    private float spacing = 5.0f;    // 组件间的间距
    private float paddingTop = 10.0f;
    private float paddingBottom = 10.0f;
    private float paddingLeft = 10.0f;
    private float paddingRight = 10.0f;

    private HorizontalAlignment alignment = HorizontalAlignment.FILL;

    public VerticalLayout() {}

    /**
     * 执行布局计算
     * @param container 需要排列子组件的容器
     */
    public void updateLayout(UIContainer container) {
        List<UIComponent> children = container.getChildren();
        if (children.isEmpty()) return;

        Rectf parentBounds = container.getBounds();

        // 1. 计算可用宽度
        float availableWidth = parentBounds.width - paddingLeft - paddingRight;

        // 2. 初始 Y 轴位置从顶部内边距开始
        float currentY = paddingTop;

        for (UIComponent child : children) {
            if (!child.isVisible()) continue;

            Rectf childBounds = child.getBounds();

            // 3. 处理水平对齐逻辑
            switch (alignment) {
                case FILL:
                    childBounds.x = paddingLeft;
                    childBounds.width = availableWidth;
                    break;
                case LEFT:
                    childBounds.x = paddingLeft;
                    break;
                case CENTER:
                    childBounds.x = paddingLeft + (availableWidth - childBounds.width) / 2f;
                    break;
                case RIGHT:
                    childBounds.x = parentBounds.width - paddingRight - childBounds.width;
                    break;
            }

            // 4. 设置 Y 坐标
            childBounds.y = currentY;

            // 5. 更新下一个组件的起始 Y 位置 (当前高度 + 间距)
            currentY += childBounds.height + spacing;
        }
    }

    /* --- 一系列链式 Setter，方便配置 --- */

    public VerticalLayout setSpacing(float spacing) {
        this.spacing = spacing;
        return this;
    }

    public VerticalLayout setPadding(float all) {
        this.paddingTop = this.paddingBottom = this.paddingLeft = this.paddingRight = all;
        return this;
    }

    public VerticalLayout setAlignment(HorizontalAlignment alignment) {
        this.alignment = alignment;
        return this;
    }
}