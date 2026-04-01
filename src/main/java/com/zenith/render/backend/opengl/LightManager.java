package com.zenith.render.backend.opengl;

import com.zenith.render.backend.opengl.shader.GLShader;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public class LightManager {
    private static final LightManager lightManager = new LightManager();
    public static final int MAX_LIGHTS = 8;

    private final List<GLLight> lights = new ArrayList<>();

    public static LightManager get() {
        return lightManager;
    }

    public void clear() {
        lights.clear();
    }

    public void addLight(GLLight light) {
        if (lights.size() < MAX_LIGHTS) {
            lights.add(light);
        }
    }

    public void apply(GLShader shader, Vector3f viewPos) {
        shader.setUniform("u_LightCount", lights.size());

        for (int i = 0; i < lights.size(); i++) {
            GLLight l = lights.get(i);
            String prefix = "u_Lights[" + i + "].";

            shader.setUniform(prefix + "type", l.getType());

            // 1. 保护 position 和 direction 不为 null
            shader.setUniform(prefix + "position", l.getPosition() != null ? l.getPosition() : new Vector3f(0,0,0));
            shader.setUniform(prefix + "direction", l.getDirection() != null ? l.getDirection() : new Vector3f(0,-1,0));

            // 2. 致命关键：把 Color 对象强转为 Shader 认识的 Vector4f
            if (l.getColor() != null) {
                shader.setUniform(prefix + "color", new org.joml.Vector4f(l.getColor().r, l.getColor().g, l.getColor().b, l.getColor().a));
            } else {
                shader.setUniform(prefix + "color", new org.joml.Vector4f(1,1,1,1));
            }

            shader.setUniform(prefix + "intensity", l.getIntensity());
            shader.setUniform(prefix + "range", l.getRange() <= 0 ? 1000f : l.getRange()); // 防止 Range 为 0 导致除以 0
            shader.setUniform(prefix + "innerCutOff", l.getInnerCutOff());
            shader.setUniform(prefix + "outerCutOff", l.getOuterCutOff());
            shader.setUniform(prefix + "ambientStrength", l.getAmbientStrength());
        }

        shader.setUniform("u_ViewPos", viewPos != null ? viewPos : new Vector3f(0,0,0));
    }
}