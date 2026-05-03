package com.zenith.core.test;

import com.zenith.animation.data.AnimatedModel;
import com.zenith.animation.io.AssimpModelLoader;
import com.zenith.animation.runtime.Animator;
import com.zenith.common.math.Color;
import com.zenith.common.math.Transform;
import com.zenith.core.ZenithEngine;
import com.zenith.render.backend.opengl.GLLight;
import com.zenith.render.backend.opengl.GLWindow;
import com.zenith.render.backend.opengl.LightManager;
import com.zenith.render.backend.opengl.shader.AnimationShader;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import static org.lwjgl.opengl.GL11.*;

/**
 * Core engine test: render the model at:
 * C:\Users\tzdwindows 7\OneDrive\Desktop\myfurry\wenqi.gltf
 */
public class Test extends ZenithEngine {
    private AnimatedModel animatedModel;
    private Animator animator;
    private AnimationShader shader;
    private final Transform modelTransform = new Transform();
    private final Matrix4f vp = new Matrix4f();

    public Test() {
        super(new GLWindow("Zenith Engine - glTF Test", 1280, 720));
    }

    @Override
    protected void init() {
        getCamera().getTransform().setPosition(0.0f, 1.2f, 5.0f);
        getCamera().lookAt(new Vector3f(0, 1.0f, 0), new Vector3f(0, 1, 0));

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
        glDisable(GL_BLEND);

        shader = new AnimationShader();

        // Must be loaded on the render thread (creates GL buffers).
        String modelPath = "C:\\Users\\tzdwindows 7\\OneDrive\\Desktop\\myfurry\\wenqi.gltf";
        animatedModel = AssimpModelLoader.load(modelPath);
        animator = new Animator(animatedModel);

        if (!animatedModel.getAllAnimations().isEmpty()) {
            String firstAnimName = animatedModel.getAllAnimations().keySet().iterator().next();
            animator.play(animatedModel.getAnimation(firstAnimName));
            animator.setLooping(true);
        }

        modelTransform.setScale(0.5f);

        LightManager.get().clear();
        GLLight sun = new GLLight(0);
        sun.setType(0);
        sun.setDirection(new Vector3f(-1.0f, -1.0f, -1.0f));
        sun.setColor(new Color(1.0f, 0.95f, 0.8f, 1.0f));
        sun.setIntensity(1.2f);
        sun.setAmbientStrength(0.12f);
        LightManager.get().addLight(sun);
    }

    @Override
    protected void update(float deltaTime) {
        if (animator != null) animator.update(deltaTime);
    }

    @Override
    protected void renderScene() {
        if (animatedModel == null || animator == null) return;

        vp.set(getCamera().getProjection().getMatrix()).mul(getCamera().getViewMatrix());

        shader.bind();
        animator.bind(0);

        boolean hasTexture = animatedModel.getTextures() != null && !animatedModel.getTextures().isEmpty();
        shader.setUseTexture(hasTexture);

        // BaseColor.a is treated as metallic in AnimationShader; keep it non-metallic by default.
        shader.setup(vp, modelTransform.getModelMatrix(), new Color(1.0f, 1.0f, 1.0f, 0.0f));
        LightManager.get().apply(shader, getCamera().getTransform().getPosition());
        shader.setEmissive(false, new Vector3f(0), 0.0f);

        animatedModel.getMesh().render();
    }

    @Override
    protected void renderAfterOpaqueScene() {
        // no-op
    }

    public static void main(String[] args) {
        new Test().start();
    }
}

