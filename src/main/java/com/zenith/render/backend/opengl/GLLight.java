package com.zenith.render.backend.opengl;

import com.zenith.common.math.Color;
import com.zenith.render.Camera;
import com.zenith.render.Light;
import com.zenith.render.backend.opengl.shader.GLShader;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public class GLLight extends Light {

    private Vector3f position;
    private Vector3f direction;
    private boolean isDirectional;
    private boolean isAmbient = false;

    private Camera attachedCamera;
    private boolean followCamera = false;

    /**
     * 默认构造：白色环境光
     */
    public GLLight() {
        super(Color.WHITE, 0.2f);
        this.isAmbient = true;
        this.position = new Vector3f(0);
        this.direction = new Vector3f(0);
    }

    /**
     * 点光源构造
     */
    public GLLight(Vector3f position, Color color, float intensity) {
        super(color, intensity);
        this.position = new Vector3f(position);
        this.direction = new Vector3f(0, -1, 0);
        this.isDirectional = false;
        this.isAmbient = false;
    }

    @Override
    public void update() {
        if (!isAmbient && followCamera && attachedCamera != null) {
            this.position.set(attachedCamera.getTransform().getPosition());
        }
    }

    public void attachToCamera(Camera camera) {
        if (isAmbient) return;
        this.attachedCamera = camera;
        this.followCamera = true;
    }

    /* ---- Getters & Setters ---- */

    public Vector3f getPosition() { return position; }
    public void setPosition(Vector3f pos) { this.position.set(pos); }

    public boolean isAmbient() { return isAmbient; }
    public void setAmbient(boolean ambient) { this.isAmbient = ambient; }

    // 修复报错的关键方法
    public void setIntensity(float intensity) { this.intensity = intensity; }
    public float getIntensity() { return this.intensity; }

    public Color getColor() { return this.color; }
    public void setColor(Color color) { this.color = color; }
}