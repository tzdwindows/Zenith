package com.zenith.render.backend.opengl;

import com.zenith.asset.AssetIdentifier;
import com.zenith.asset.AssetResource;
import com.zenith.common.math.Color;
import com.zenith.render.Material;
import com.zenith.render.Shader;
import com.zenith.render.Texture;
import com.zenith.render.backend.opengl.shader.GLShaderRegistry;
import com.zenith.render.backend.opengl.shader.ImageShader;
import com.zenith.render.backend.opengl.texture.GLTexture;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;

public class GLMaterial extends Material {

    public GLMaterial(Shader shader) {
        super(shader);
        AssetIdentifier id = new AssetIdentifier("zenith", "font/HarmonyOS_Sans_SC_Regular.ttf");
        try {
            font = new GLFont(Objects.requireNonNull(AssetResource.loadFromResources(id)), 32);
            textRenderer = new GLTextRenderer(GLShaderRegistry.get(GLShaderRegistry.UI_TEXT));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 将材质属性提交给 GPU。
     * 逻辑：绑定 Shader -> 激活并绑定所有纹理 -> 设置基础颜色 Uniform
     */
    @Override
    public void apply() {
        if (shader == null) return;
        if (shader instanceof ImageShader imageShader) {
            imageShader.bind();
            imageShader.setUniform("u_Color", diffuseColor);
            int unit = 0;
            for (Map.Entry<String, Texture> entry : textures.entrySet()) {
                Texture tex = entry.getValue();
                if (tex != null) {
                    tex.bind(unit);
                    imageShader.setUniform("u_Texture", unit);
                    unit++;
                }
            }
            return;
        }
        shader.bind();

        int unit = 0;
        boolean hasTexture = false;
        for (Map.Entry<String, Texture> entry : textures.entrySet()) {
            String uniformName = entry.getKey();
            Texture texture = entry.getValue();
            if (texture != null) {
                texture.bind(unit);
                shader.setUniform(uniformName, unit);
                unit++;
                hasTexture = true;
            }
        }
        if (!(shader instanceof com.zenith.render.backend.opengl.shader.WaterShader)) {
            shader.setUniform("u_UseTexture", hasTexture ? 1.0f : 0.0f);
            shader.setUniform("u_TextColor", diffuseColor);
        }
    }

    @Override
    public void dispose() {
        textures.clear();
        shader = null;
        com.zenith.common.utils.InternalLogger.debug("GLMaterial: References cleared.");
    }
}