package com.zenith.render.backend.opengl;

import com.zenith.asset.AssetResource;
import com.zenith.core.RayTracingProvider;
import com.zenith.render.Camera;
import com.zenith.render.Mesh;
import com.zenith.render.backend.opengl.shader.GLComputeShader;
import org.joml.Matrix4f;

import java.io.IOException;
import java.util.List;

import static org.lwjgl.opengl.GL43.*;

/**
 * 方案 B：屏幕空间混合光追 (Screen Space Ray Tracing / SSR / SSS)
 * 利用光栅化 G-Buffer (颜色与深度) 作为光线步进的加速结构。
 */
public class HardwareHybridRTProvider implements RayTracingProvider {
    private GLComputeShader rtShader;
    private int sampleCount = 0;

    @Override
    public void init(int width, int height) {
        String source = null;
        try {
            source = AssetResource.loadFromResources("shaders/rt/hybrid_rt.comp").readAsString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        this.rtShader = new GLComputeShader("HardwareHybridRT", source, null);
    }

    @Override
    public void buildAccelerationStructures(List<Mesh> meshes) {
        // SSR 不需要构建 CPU BVH 树！每次场景变动时重置累积即可
        resetAccumulation();
    }

    @Override
    public void trace(SceneFramebuffer fbo, Camera camera) {
        sampleCount++;
        rtShader.bind();

        // 1. 绑定底层光栅化颜色 (GBuffer Albedo)
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, fbo.getSceneTextureID());
        rtShader.setUniform("u_BaseColor", 1);

        // 2. 绑定底层光栅化深度 (Z-Buffer)
        glActiveTexture(GL_TEXTURE2);
        glBindTexture(GL_TEXTURE_2D, fbo.getDepthTextureID());
        rtShader.setUniform("u_DepthMap", 2);

        // 3. 绑定输出图像
        glBindImageTexture(0, fbo.getRayTraceTargetID(), 0, false, 0, GL_READ_WRITE, GL_RGBA16F);

        // 4. 精准的矩阵计算，用于从像素深度反推世界坐标
        Matrix4f viewProj = new Matrix4f(camera.getProjection().getMatrix()).mul(camera.getViewMatrix());
        Matrix4f invViewProj = new Matrix4f(viewProj).invert();

        rtShader.setUniform("u_ViewProj", viewProj);
        rtShader.setUniform("u_InvViewProj", invViewProj);
        rtShader.setUniform("u_CamPos", camera.getTransform().getPosition());

        // 5. 光照与采样参数
        org.joml.Vector3f sunDir = new org.joml.Vector3f(0.5f, 0.6f, -0.5f).normalize();
        rtShader.setUniform("u_SunDirection", sunDir);
        rtShader.setUniform("u_SampleCount", sampleCount);

        // 6. 派发计算
        glDispatchCompute((int)Math.ceil(fbo.getWidth()/8.0), (int)Math.ceil(fbo.getHeight()/8.0), 1);
        glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);

        rtShader.unbind();
    }

    @Override
    public void dispose() {
        if (rtShader != null) rtShader.dispose();
    }

    public void resetAccumulation() { sampleCount = 0; }
    public int getSampleCount() { return sampleCount; }
}