package com.zenith.render.backend.opengl;

import com.zenith.common.math.Transform;
import com.zenith.render.Material;
import com.zenith.render.Mesh;
import com.zenith.render.Renderer;
import com.zenith.render.Renderable;
import com.zenith.render.backend.opengl.shader.StandardShader;
import com.zenith.render.backend.opengl.shader.WaterShader;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * GLRenderer 负责管理 OpenGL 渲染管线的提交与执行。
 */
public class GLRenderer extends Renderer {

    private final List<RenderCommand> queue = new ArrayList<>();
    private final Matrix4f viewProjectionMatrix = new Matrix4f().identity();
    private Vector3f lightPos = new Vector3f(10.0f, 10.0f, 10.0f);
    private Vector3f viewPos = new Vector3f(0.0f, 5.0f, 10.0f);
    private Vector3f lightDir = new Vector3f(0.5f, -1.0f, 0.3f).normalize();
    private String lastShaderName = "None";
    @Override
    public void setViewProjection(Matrix4f matrix) {
        if (matrix != null) {
            this.viewProjectionMatrix.set(matrix);
        }
    }

    /**
     * 设置渲染器当前使用的视角位置（用于计算水面反射/高光）
     */
    public void setViewPos(Vector3f pos) {
        this.viewPos.set(pos);
    }

    /**
     * 支持直接提交实现 Renderable 接口的对象
     */
    public void submit(Renderable renderable) {
        if (renderable != null) {
            submit(renderable.getMesh(), renderable.getMaterial(), renderable.getTransform());
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
            cmd.material.apply();
            var shader = cmd.material.getShader();
            this.lastShaderName = shader.getClass().getSimpleName();
            shader.setUniform("u_ViewProjection", viewProjectionMatrix);
            if(shader.hasUniform("u_Model")) {
                shader.setUniform("u_Model", cmd.transform.getModelMatrix());
            }

            if (shader instanceof StandardShader standardShader) {
                standardShader.applyLights(lightPos);
            }
            else if (shader instanceof WaterShader waterShader) {
                waterShader.setUniform("u_ViewPos", viewPos);
                waterShader.setUniform("u_LightDir", lightDir);
            }

            if (cmd.mesh != null) {
                cmd.mesh.render();
                this.drawCalls++;
                int vCount = cmd.mesh.getVertexCount();
                this.vertexCount += vCount;
                this.triangleCount += vCount / 3;
            }
        }
        queue.clear();
    }

    public String getLastShaderName() {
        return lastShaderName;
    }

    /**
     * 内部记录类，用于暂存渲染指令
     */
    private record RenderCommand(Mesh mesh, Material material, Transform transform) {}
}