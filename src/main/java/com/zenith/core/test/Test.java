package com.zenith.core.test;

import com.zenith.core.ZenithEngine;
import com.zenith.render.VertexLayout;
import com.zenith.render.backend.opengl.GLWindow;
import com.zenith.render.backend.opengl.GLMaterial;
import com.zenith.render.backend.opengl.GLMesh;
import com.zenith.render.backend.opengl.shader.GLShaderRegistry;
import com.zenith.render.backend.opengl.shader.SkyShader;
import com.zenith.render.backend.opengl.shader.WaterShader;
import com.zenith.common.math.Color;
import org.joml.Vector3f;
import org.joml.Matrix4f;

import static org.lwjgl.opengl.GL11.*;

/**
 * 测试类
 */
public class Test extends ZenithEngine {
    private WaterEntity waterEntity;
    private GLMesh skyBoxMesh;
    private SkyShader skyShader;
    private WaterShader waterShader;

    private float time = 0.0f;
    private boolean sceneInitialized = false;
    private final Matrix4f modelMatrix = new Matrix4f();
    private final Matrix4f viewMatrix = new Matrix4f();
    private final Matrix4f projMatrix = new Matrix4f();
    private final Matrix4f skyViewMatrix = new Matrix4f();
    private final Matrix4f combinedMatrix = new Matrix4f();

    private final Vector3f sunDir = new Vector3f(0.4f, 0.8f, -0.4f).normalize();
    private final Vector3f sunColor = new Vector3f(2.4f, 2.2f, 1.9f);
    private final Vector3f camPosCached = new Vector3f();

    private final Vector3f waterMin = new Vector3f(-600.0f, -2.0f, -600.0f);
    private final Vector3f waterMax = new Vector3f(600.0f, 2.0f, 600.0f);

    private final Color deepColor = new Color(0.01f, 0.15f, 0.35f, 1.0f);
    private final Color shallowColor = new Color(0.1f, 0.5f, 0.7f, 0.8f);

    public Test() {
        super(new GLWindow("Zenith Water System - Optimized", 1280, 720));
    }

    private void initScene() {
        this.getCamera().getTransform().getPosition().set(0, 15, 60);
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        skyShader = GLShaderRegistry.get(GLShaderRegistry.SKY);
        float[] skyData = generateSkyBoxData();
        VertexLayout layout = createStandardLayout();
        skyBoxMesh = new GLMesh(skyData.length / 20, layout);
        skyBoxMesh.updateVertices(skyData);
        waterShader = GLShaderRegistry.get(GLShaderRegistry.WATER);
        GLMaterial waterMaterial = new GLMaterial(waterShader);
        float[] waterData = generatePlaneData(1200.0f, 1200.0f, 200, 200);
        GLMesh waterMesh = new GLMesh(waterData.length / 20, layout);
        waterMesh.updateVertices(waterData);

        this.waterEntity = new WaterEntity(waterMesh, waterMaterial);

        this.sceneInitialized = true;
    }

    @Override
    protected void update(float deltaTime) {
        if (!sceneInitialized) {
            initScene();
        }
        time += deltaTime;
    }

    @Override
    protected void render() {
        if (!sceneInitialized) return;
        glClearColor(0.45f, 0.65f, 0.85f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        viewMatrix.set(getCamera().getViewMatrix());
        projMatrix.set(getCamera().getProjection().getMatrix());
        camPosCached.set(getCamera().getTransform().getPosition());
        glDepthFunc(GL_LEQUAL);
        glDepthMask(false);
        skyShader.bind();
        skyViewMatrix.set(viewMatrix).setTranslation(0, 0, 0);
        combinedMatrix.set(projMatrix).mul(skyViewMatrix);
        skyShader.setUniform("u_ViewProjection", combinedMatrix);
        skyShader.setUniform("u_SunDir", sunDir);
        skyShader.setUniform("u_Time", time);
        skyShader.setUniform("u_CloudCoverage", 0.15f);
        skyShader.setUniform("u_CloudSpeed", 0.005f);
        skyBoxMesh.render();
        glDepthMask(true);
        glDepthFunc(GL_LESS);
        if (waterEntity != null) {
            if (isInsideFrustum(waterMin, waterMax)) {
                waterShader.bind();
                waterShader.setUniform("u_ViewProjection", projMatrix.mul(viewMatrix, combinedMatrix));
                waterShader.setUniform("u_Model", modelMatrix.identity());
                waterShader.updateUniforms(
                        camPosCached,
                        sunDir,
                        sunColor,
                        deepColor,
                        shallowColor,
                        time,
                        0.0f
                );
                waterEntity.getMesh().render();
            }
        }
    }

    private VertexLayout createStandardLayout() {
        VertexLayout layout = new VertexLayout();
        layout.pushFloat("aPos", 3);
        layout.pushFloat("aNormal", 3);
        layout.pushFloat("aTexCoord", 2);
        layout.pushFloat("aColor", 4);
        layout.pushFloat("aBoneIds", 4);
        layout.pushFloat("aWeights", 4);
        return layout;
    }

    private float[] generatePlaneData(float w, float d, int xn, int zn) {
        float[] vertices = new float[(xn + 1) * (zn + 1) * 20];
        for (int z = 0; z <= zn; z++) {
            for (int x = 0; x <= xn; x++) {
                int i = (z * (xn + 1) + x) * 20;
                vertices[i] = (float) x / xn * w - w / 2f;
                vertices[i + 1] = 0;
                vertices[i + 2] = (float) z / zn * d - d / 2f;
                vertices[i + 3] = 0; vertices[i + 4] = 1.0f; vertices[i + 5] = 0; // Normal
                vertices[i + 6] = (float) x / xn; vertices[i + 7] = (float) z / zn; // UV
            }
        }
        int[] indices = new int[xn * zn * 6];
        int p = 0;
        for (int z = 0; z < zn; z++) {
            for (int x = 0; x < xn; x++) {
                int s = z * (xn + 1) + x;
                indices[p++] = s; indices[p++] = s + xn + 1; indices[p++] = s + 1;
                indices[p++] = s + 1; indices[p++] = s + xn + 1; indices[p++] = s + xn + 2;
            }
        }
        float[] res = new float[indices.length * 20];
        for (int i = 0; i < indices.length; i++) System.arraycopy(vertices, indices[i] * 20, res, i * 20, 20);
        return res;
    }

    private float[] generateSkyBoxData() {
        float[] c = {-1,1,-1, -1,-1,-1, 1,-1,-1, 1,-1,-1, 1,1,-1, -1,1,-1, -1,-1,1, -1,-1,-1, -1,1,-1, -1,1,-1, -1,1,1, -1,-1,1, 1,-1,-1, 1,-1,1, 1,1,1, 1,1,1, 1,1,-1, 1,-1,-1, -1,-1,1, -1,1,1, 1,1,1, 1,1,1, 1,-1,1, -1,-1,1, -1,1,-1, 1,1,-1, 1,1,1, 1,1,1, -1,1,1, -1,1,-1, -1,-1,-1, -1,-1,1, 1,-1,-1, 1,-1,-1, -1,-1,1, 1,-1,1};
        float[] d = new float[c.length / 3 * 20];
        for (int i = 0; i < c.length / 3; i++) {
            d[i*20] = c[i*3] * 800f;
            d[i*20+1] = c[i*3+1] * 800f;
            d[i*20+2] = c[i*3+2] * 800f;
        }
        return d;
    }

    public static void main(String[] args) {
        new Test().start();
    }
}