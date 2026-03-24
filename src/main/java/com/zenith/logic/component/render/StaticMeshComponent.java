package com.zenith.logic.component.render;

import com.zenith.logic.component.RenderComponent;
import com.zenith.render.backend.opengl.GLMesh;
import com.zenith.render.backend.opengl.shader.GLShader;
import org.joml.Matrix4f;

/**
 * 静态网格渲染组件
 * 用于渲染不包含骨骼动画的 3D 模型。
 */
public class StaticMeshComponent extends RenderComponent {

    /** 顶点缓冲区引用 **/
    private final GLMesh mesh;

    /** 着色器程序引用 **/
    private final GLShader shader;

    /** 矩阵缓存，避免堆分配 **/
    private final Matrix4f modelMatrix = new Matrix4f();

    /**
     * 构造网格渲染器
     * @param mesh 目标网格
     * @param shader 渲染使用的着色器
     */
    public StaticMeshComponent(GLMesh mesh, GLShader shader) {
        this.mesh = mesh;
        this.shader = shader;
    }

    /**
     * 执行实际的渲染提交
     * 会尝试从宿主实体中获取 MaterialComponent 以应用材质属性
     */
    @Override
    public void render(Matrix4f viewMatrix, Matrix4f projectionMatrix) {
        if (mesh == null || shader == null) return;

        shader.bind();

        MaterialComponent matComp = owner.getComponent(MaterialComponent.class);
        if (matComp != null) {
            matComp.getMaterial().apply();
        }

        owner.getTransform().getTransformationMatrix(modelMatrix);
        shader.setUniform("u_model", modelMatrix);
        shader.setUniform("u_view", viewMatrix);
        shader.setUniform("u_projection", projectionMatrix);

        mesh.render();

        shader.unbind();
    }

    @Override public void onCreate() {}
    @Override public void onUpdate(float deltaTime) {}
    @Override public void onDestroy() {}
}