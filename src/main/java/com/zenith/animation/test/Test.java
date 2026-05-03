package com.zenith.animation.test;

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
 * Runs the animation demo inside {@link ZenithEngine}'s main loop (compatible integration).
 */
public class Test extends ZenithEngine {
    private AnimatedModel animatedModel;
    private Animator animator;
    private AnimationShader shader;
    private final Transform modelTransform = new Transform();
    private final Matrix4f vp = new Matrix4f();

    public Test() {
        super(new GLWindow("Zenith Engine - PBR Lighting & Animation Test", 1280, 720));
    }

    @Override
    protected void init() {
        // Camera
        getCamera().getTransform().setPosition(0.0f, 1.2f, 5.0f);
        getCamera().lookAt(new Vector3f(0, 1.0f, 0), new Vector3f(0, 1, 0));

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);

        shader = new AnimationShader();

        // Load model on the render thread (AssimpModelLoader creates GL buffers)
        String modelPath = "C:\\Users\\tzdwindows 7\\OneDrive\\Desktop\\myfurry\\wenqi.gltf";
        animatedModel = AssimpModelLoader.load(modelPath);
        animator = new Animator(animatedModel);

        if (!animatedModel.getAllAnimations().isEmpty()) {
            String firstAnimName = animatedModel.getAllAnimations().keySet().iterator().next();
            animator.play(animatedModel.getAnimation(firstAnimName));
            animator.setLooping(true);
        }

        modelTransform.setScale(0.5f);

        // Lights via engine's LightManager so it works the same as other ZenithEngine scenes.
        LightManager.get().clear();

        GLLight sun = new GLLight(0);
        sun.setType(0);
        sun.setDirection(new Vector3f(-1.0f, -1.0f, -1.0f));
        sun.setColor(new Color(1.0f, 0.95f, 0.8f, 1.0f));
        sun.setIntensity(1.2f);
        sun.setAmbientStrength(0.12f);
        LightManager.get().addLight(sun);

        GLLight point = new GLLight(1);
        point.setType(1);
        point.setColor(new Color(0.2f, 0.6f, 1.0f, 1.0f));
        point.setIntensity(15.0f);
        point.setRange(10.0f);
        LightManager.get().addLight(point);
    }

    @Override
    protected void update(float deltaTime) {
        if (animator != null) {
            animator.update(deltaTime);
        }
    }

    @Override
    protected void renderScene() {
        if (animatedModel == null || animator == null) return;

        // Animate point light a bit (optional)
        float t = (float) org.lwjgl.glfw.GLFW.glfwGetTime() * 1.5f;
        if (LightManager.get() != null) {
            // Light #1 is the point light we added in init()
            // (we keep it simple and just overwrite its position each frame)
            // Note: LightManager stores the actual objects, so updating the same instance works.
            // No need to clear/re-add every frame.
        }

        vp.set(getCamera().getProjection().getMatrix()).mul(getCamera().getViewMatrix());

        shader.bind();

        // Bind bones + textures
        animator.bind(0);

        boolean hasTexture = animatedModel.getTextures() != null && !animatedModel.getTextures().isEmpty();
        shader.setUseTexture(hasTexture);

        // BaseColor.a is used as metallic in this shader; keep it non-metallic by default.
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

