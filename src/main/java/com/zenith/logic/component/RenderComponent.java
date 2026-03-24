package com.zenith.logic.component;

import org.joml.Matrix4f;

/**
 * 所有可视化组件的抽象基类
 * 该组件充当逻辑实体（Entity）与渲染管线（Renderer）之间的桥梁。
 * 任何需要在屏幕上呈现视觉效果的组件（如 Mesh, Sprite, Particle）均应继承此类。
 */
public abstract class RenderComponent extends Component {

    /** 组件自身的显示状态开关 **/
    private boolean visible = true;

    /**
     * 核心渲染接口，由外部渲染系统每帧触发
     * 实现类应在此方法中完成着色器绑定、Uniform 变量上传以及 Draw Call 提交。
     * @param viewMatrix 摄像机的视图矩阵，控制观察视角
     * @param projectionMatrix 摄像机的投影矩阵，控制空间裁剪与透视
     */
    public abstract void render(Matrix4f viewMatrix, Matrix4f projectionMatrix);

    /**
     * 综合判定组件当前是否应当被渲染
     * 只有当组件被显式设为可见（visible）且组件处于启用状态（enabled）时才返回 true。
     * @return 最终的渲染许可状态
     */
    public boolean isVisible() {
        return visible && isEnabled();
    }

    /**
     * 设置组件的可见性
     * 注意：即使设为 true，若组件所在实体或组件自身被禁用（Disabled），渲染依然不会执行。
     * @param visible 是否允许渲染该组件
     */
    public void setVisible(boolean visible) {
        this.visible = visible;
    }
}