package com.zenith.render.backend.opengl;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import static org.lwjgl.opengl.GL13.*;

public class GLPBRRenderer {
    private int brdfLUT;
    private int prefilterMap;
    private Vector3f[] sh;

    public void setEnvironment(int lut, int env, Vector3f[] shData) {
        this.brdfLUT = lut;
        this.prefilterMap = env;
        this.sh = shData;
    }

    /**
     * 适配 Zenith 架构的渲染函数
     */
    public void render(GLMesh mesh, GLCamera camera, GLPBRMaterial material, Matrix4f modelMatrix) {
        material.getShader().bind();
        material.getShader().setUniform("u_Model", modelMatrix);
        material.getShader().setUniform("u_View", camera.getViewMatrix());
        material.getShader().setUniform("u_Projection", camera.getProjectionMatrix());
        material.getShader().setUniform("u_CameraPos", camera.getTransform().getPosition());
        material.apply();
        bindIBL(material.getShader());
        mesh.render();
        material.getShader().unbind();
    }

    private void bindIBL(com.zenith.render.Shader shader) {
        glActiveTexture(GL_TEXTURE8);
        glBindTexture(GL_TEXTURE_2D, brdfLUT);
        shader.setUniform("u_brdfLUT", 8);

        glActiveTexture(GL_TEXTURE9);
        glBindTexture(GL_TEXTURE_CUBE_MAP, prefilterMap);
        shader.setUniform("u_iblSpecular", 9);

        for (int i = 0; i < 9; i++) {
            shader.setUniform("u_iblSH[" + i + "]", sh[i]);
        }
    }
}