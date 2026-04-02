package com.zenith.render.backend.opengl;

import com.zenith.asset.AssetResource;
import com.zenith.core.RayTracingProvider;
import com.zenith.render.Camera;
import com.zenith.render.Mesh;
import com.zenith.render.backend.opengl.shader.GLComputeShader;

import java.io.IOException;
import java.util.List;

import static org.lwjgl.opengl.GL43.*;

/**
 * 方案 B：硬件辅助混合光追 (利用深度缓冲加速)
 */
public class HardwareHybridRTProvider implements RayTracingProvider {
    private GLComputeShader rtShader;

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
    }

    @Override
    public void trace(SceneFramebuffer fbo, Camera camera) {
        rtShader.bind();

        // 关键：将光栅化生成的深度图绑定为输入 (利用硬件生成的 Z-Buffer)
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, fbo.getDepthTextureID());
        rtShader.setUniform("u_DepthMap", 1);

        // 绑定输出图像
        rtShader.bindImage(0, fbo.getRayTraceTargetID(), GL_WRITE_ONLY);

        rtShader.dispatch(fbo.getWidth(), fbo.getHeight());
        rtShader.unbind();
    }

    @Override public void dispose() { rtShader.dispose(); }
}