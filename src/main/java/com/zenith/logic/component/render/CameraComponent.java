package com.zenith.logic.component.render;

import com.zenith.logic.component.Component;
import com.zenith.render.backend.opengl.GLCamera;
import org.joml.Matrix4f;

/**
 * 摄像机组件
 * 将实体的 Transform 与渲染层的 GLCamera 绑定。
 * 挂载此组件的实体位置即为观察者在世界中的视角中心。
 */
public class CameraComponent extends Component {

    /** 关联的底层渲染摄像机对象 **/
    private final GLCamera glCamera;

    /** * 构造摄像机组件
     * 修复了原先 GLCamera 构造函数不接受参数的问题
     */
    public CameraComponent() {
        this.glCamera = new GLCamera();
    }

    /**
     * 在每帧更新时，将实体的 Transform 状态同步给底层摄像机
     * 由于 GLCamera 内部持有 Transform，我们直接进行数值同步
     */
    @Override
    public void onUpdate(float deltaTime) {
        if (owner == null) return;
        /** * 核心修复：GLCamera 没有 setPosition，但它有 transform
         * 我们将实体的坐标和旋转同步到相机的 Transform 中
         */
        glCamera.getTransform().getPosition().set(owner.getTransform().getPosition());
        glCamera.getTransform().getRotation().set(owner.getTransform().getRotation());

        /** * 注意：你的 GLCamera 不需要显式调用 update() 或 dispose()
         * 因为矩阵是在 getViewMatrix() 调用时动态计算并求逆的
         */
    }

    /**
     * 获取当前摄像机的观察矩阵
     * @return 4x4 观察矩阵
     */
    public Matrix4f getViewMatrix() {
        return glCamera.getViewMatrix();
    }

    /**
     * 获取投影矩阵
     * 注意：如果基类 Camera 中有此方法，请确保其已正确初始化
     * @return 4x4 投影矩阵
     */
    public Matrix4f getProjectionMatrix() {
        return glCamera.getProjectionMatrix();
    }

    @Override
    public void onCreate() {}

    @Override
    public void onDestroy() {}
}