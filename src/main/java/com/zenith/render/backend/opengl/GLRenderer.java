package com.zenith.render;

import com.zenith.common.math.Transform;
import com.zenith.render.backend.opengl.shader.StandardShader;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public class GLRenderer extends Renderer {

    private final List<RenderCommand> queue = new ArrayList<>();
    private final Matrix4f viewProjectionMatrix = new Matrix4f().identity();

    public void setViewProjection(Matrix4f matrix) {
        if (matrix != null) {
            this.viewProjectionMatrix.set(matrix);
        }
    }

    @Override
    public void submit(Mesh mesh, Material material, Transform transform) {
        queue.add(new RenderCommand(mesh, material, transform));
    }

    @Override
    public void flush() {
        if (queue.isEmpty()) return;

        for (RenderCommand cmd : queue) {
            // 1. 绑定 Shader 并设置材质基础属性 (u_TextColor, u_Texture 等)
            cmd.material.apply();

            // 2. 获取 Shader 实例
            var shader = cmd.material.getShader();

            // --- 核心修改：光照同步 ---
            // 如果是 StandardShader，我们需要把在 Test1 中 addLight 进去的数据应用到 GPU
            if (shader instanceof StandardShader standardShader) {
                // 假设观察者在 Z=500 的位置，或者你可以通过 Renderer 传入摄像机位置
                // 如果不调用 applyLights，u_LightCount 永远是 0
                standardShader.applyLights(new Vector3f(640, 360, 500));
            }

            // 3. 提交变换矩阵
            shader.setUniform("u_ViewProjection", viewProjectionMatrix);
            shader.setUniform("u_Model", cmd.transform.getModelMatrix());

            // 4. 执行绘制
            cmd.mesh.render();
        }

        queue.clear();
    }

    private record RenderCommand(Mesh mesh, Material material, Transform transform) {}
}