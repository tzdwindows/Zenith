package com.zenith.render.backend.opengl;

import com.zenith.render.Shader;
import org.joml.Vector3f;

public class GLPBRMaterial extends GLMaterial {
    private Vector3f albedo = new Vector3f(1.0f, 1.0f, 1.0f);
    private float roughness = 0.5f;
    private float metallic = 0.0f;
    private float ao = 1.0f;

    public GLPBRMaterial(Shader shader) {
        super(shader);
    }

    @Override
    public void apply() {
        super.apply();

        shader.setUniform("u_Albedo", albedo);
        shader.setUniform("u_Roughness", roughness);
        shader.setUniform("u_Metallic", metallic);
        shader.setUniform("u_AO", ao);
    }
    public void setPBR(Vector3f albedo, float r, float m) {
        this.albedo.set(albedo);
        this.roughness = r;
        this.metallic = m;
    }
}