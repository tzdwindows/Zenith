package com.zenith.ui.layout;

import com.zenith.ui.component.UIContainer;

/**
 * 布局管理器基类
 */
public abstract class UILayout {

    protected float spacing = 5.0f;
    protected float padding = 10.0f;

    /**
     * 执行布局计算，调整 container 中所有子组件的 bounds
     */
    public abstract void updateLayout(UIContainer container);

    public void setSpacing(float spacing) { this.spacing = spacing; }
    public void setPadding(float padding) { this.padding = padding; }
}