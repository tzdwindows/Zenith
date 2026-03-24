package com.zenith.logic.component;

import com.zenith.render.backend.opengl.GLMesh;
import com.zenith.render.backend.opengl.shader.GLShader;
import org.joml.Matrix4f;

/**
 * 网格渲染组件
 * 负责将实体的物理变换信息（Transform）与底层的 OpenGL 资源（Mesh/Shader）关联。
 * 该组件在渲染阶段被调用，通过上传 MVP 矩阵将顶点数据绘制到屏幕上。
 */
public class MeshComponent extends RenderComponent {

    /** 关联的 OpenGL 顶点缓冲区对象 **/
    private final GLMesh mesh;

    /** 用于绘制该网格的着色器程序 **/
    private final GLShader shader;

    /** 预分配的模型矩阵缓存，避免每帧重复创建对象 **/
    private final Matrix4f modelMatrix = new Matrix4f();

    /**
     * 构造一个新的网格渲染组件
     * @param mesh 已经过初始化并上传数据的 GLMesh 实例
     * @param shader 已经过编译并链接的 GLShader 实例
     */
    public MeshComponent(GLMesh mesh, GLShader shader) {
        this.mesh = mesh;
        this.shader = shader;
    }

    /**
     * 执行标准的 OpenGL 渲染流程
     * 该方法从实体的 Transform 中提取模型矩阵，并结合摄像机的观察矩阵与投影矩阵。
     * @param viewMatrix 摄像机的视图矩阵，定义观察者的位置和方向
     * @param projectionMatrix 摄像机的投影矩阵，定义透视或正交裁剪空间
     */
    @Override
    public void render(Matrix4f viewMatrix, Matrix4f projectionMatrix) {
        if (mesh == null || shader == null) {
            return;
        }

        shader.bind();

        /** * 从宿主实体获取世界空间的坐标变换
         * 将逻辑层的位置、旋转、缩放同步到 GPU 侧的模型矩阵中
         */
        owner.getTransform().getTransformationMatrix(modelMatrix);

        /** 上传 Uniform 变量至着色器 **/
        shader.setUniform("u_model", modelMatrix);
        shader.setUniform("u_view", viewMatrix);
        shader.setUniform("u_projection", projectionMatrix);

        mesh.render();

        shader.unbind();
    }

    /**
     * 组件创建时的初始化逻辑
     */
    @Override
    public void onCreate() {}

    /**
     * 每帧更新逻辑（由 Entity.onUpdate 调用）
     * @param deltaTime 帧间隔时间
     */
    @Override
    public void onUpdate(float deltaTime) {}

    /**
     * 组件销毁逻辑，用于清理对渲染资源的引用
     */
    @Override
    public void onDestroy() {
        /** 注意：此处仅清理引用，实际显存资源的销毁应由资源管理器统一处理 **/
    }
}