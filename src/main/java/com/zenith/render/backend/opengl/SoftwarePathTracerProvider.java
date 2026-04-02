package com.zenith.render.backend.opengl;

import com.zenith.asset.AssetResource;
import com.zenith.core.RayTracingProvider;
import com.zenith.render.Camera;
import com.zenith.render.Mesh;
import com.zenith.render.backend.opengl.buffer.GLSSBO;
import com.zenith.render.backend.opengl.shader.GLComputeShader;
import org.joml.Vector2f;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.lwjgl.opengl.GL43.*;

/**
 * 方案 A：纯 Compute Shader 路径追踪 (你移植的库)
 */
public class SoftwarePathTracerProvider implements RayTracingProvider {
    private GLComputeShader computeShader;
    private GLSSBO vertexBuffer;
    private int sampleCount = 0;

    @Override
    public void init(int width, int height) {
        // 加载你移植的 .comp 源码
        String source = null;
        try {
            source = AssetResource.loadFromResources("shaders/rt/pathtrace.comp").readAsString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        Map<String, String> defines = new HashMap<>();
        defines.put("SCREEN_WIDTH", String.valueOf(width));
        defines.put("SCREEN_HEIGHT", String.valueOf(height));

        this.computeShader = new GLComputeShader("SoftwarePathTracer", source, defines);
        this.vertexBuffer = new GLSSBO(2); // 对应 intersection.glsl 里的 binding=2
    }

    @Override
    public void buildAccelerationStructures(List<Mesh> meshes) {
        // 将所有 Mesh 拍平成 float 数组上传
        // 这里需要实现一个 GeometryPacker 来处理顶点对齐
        float[] flatData = GeomUtils.flatten(meshes);
        vertexBuffer.setData(flatData);
        resetAccumulation();
    }

    @Override
    public void trace(SceneFramebuffer fbo, Camera camera) {
        sampleCount++; // 每一帧递增，用于着色器内的 mix(old, new, 1/sampleCount)

        computeShader.bind();

        // 1. 绑定图像 (必须是 GL_READ_WRITE，因为着色器里有 imageLoad)
        // 确保 fbo.getRayTraceTargetID() 对应的纹理格式是 GL_RGBA16F 或 GL_RGBA32F
        glBindImageTexture(0, fbo.getRayTraceTargetID(), 0, false, 0, GL_READ_WRITE, GL_RGBA16F);

        // 2. 传递相机参数 (假设你的着色器里有一个叫 camera 的 Uniform Block 或一组 Uniforms)
        // 根据你的 .comp 源码，它访问了 camera.position, camera.fov 等
        // 如果这些是普通 Uniform，你需要逐个 set：
        computeShader.setUniform("camera.position", camera.getTransform().getPosition());
        computeShader.setUniform("camera.forward", camera.getForward());
        computeShader.setUniform("camera.up", camera.getUp());
        computeShader.setUniform("camera.right", camera.getRight());
        computeShader.setUniform("camera.fov", (float)Math.toRadians(camera.getProjection().getFov()));
        computeShader.setUniform("camera.aperture", 0.01f); // 光圈
        computeShader.setUniform("camera.focalDist", 10.0f); // 焦距

        // 3. 传递样本计数
        computeShader.setUniform("u_SampleCount", sampleCount);

        // 4. 计算 Dispatch 组数 (针对 local_size = 8)
        int groupX = (fbo.getWidth() + 7) / 8;
        int groupY = (fbo.getHeight() + 7) / 8;
        glDispatchCompute(groupX, groupY, 1);

        // 5. 内存屏障：确保写入完成再进行后续绘制
        glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);

        computeShader.unbind();
    }

    public void resetAccumulation() { this.sampleCount = 0; }
    public int getSampleCount() { return sampleCount; }

    @Override public void dispose() {
        computeShader.dispose();
        vertexBuffer.dispose();
    }
}