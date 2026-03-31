package com.zenith.render.backend.opengl;

import com.zenith.common.math.Color;
import com.zenith.render.Camera;
import com.zenith.render.Light;
import org.joml.Vector3f;

/**
 * 高级灯光类：已修复兼容性，支持旧版构造函数和新版 PBR 特性
 */
public class GLLight extends Light {

    // 定义光源类型常量
    public static final int TYPE_DIRECTIONAL = 0;
    public static final int TYPE_POINT = 1;
    public static final int TYPE_SPOT = 2;

    private int type = TYPE_POINT;
    private final Vector3f position = new Vector3f(0);
    private final Vector3f direction = new Vector3f(0, -1, 0);

    // 物理参数
    private float range = 100.0f;          // 默认范围拉大，防止旧代码的光见不到
    private float innerCutOff = (float) Math.cos(Math.toRadians(12.5f));
    private float outerCutOff = (float) Math.cos(Math.toRadians(17.5f));
    private float ambientStrength = 0.1f;

    // 兼容性标记
    private boolean isAmbient = false;
    private Camera attachedCamera;
    private boolean followCamera = false;

    /**
     * 默认构造函数 (兼容旧版)
     */
    public GLLight() {
        super(Color.WHITE, 1.0f);
        this.type = TYPE_POINT;
    }

    /**
     * [核心修复] 重新添加旧版构造函数，以兼容 TestLightSystem
     * 将其默认映射为点光源 (TYPE_POINT)
     */
    public GLLight(Vector3f position, Color color, float intensity) {
        super(color, intensity);
        this.position.set(position);
        this.type = TYPE_POINT;
    }

    /**
     * 静态工厂：快速创建平行光 (太阳/月亮)
     */
    public static GLLight createDirectional(Vector3f direction, Color color, float intensity) {
        GLLight light = new GLLight();
        light.setType(TYPE_DIRECTIONAL);
        light.setDirection(direction);
        light.setColor(color);
        light.setIntensity(intensity);
        return light;
    }

    @Override
    public void update() {
        if (!isAmbient && followCamera && attachedCamera != null) {
            this.position.set(attachedCamera.getTransform().getPosition());
            if (this.type == TYPE_SPOT) {
                this.direction.set(attachedCamera.getForward());
            }
        }
    }

    public void attachToCamera(Camera camera) {
        if (isAmbient) return;
        this.attachedCamera = camera;
        this.followCamera = true;
    }

    /* ---- 兼容性 Setter / Getter ---- */

    public boolean isAmbient() { return isAmbient; }

    /**
     * 兼容旧版 setAmbient。
     * 如果设置为环境光，我们将其类型转为平行光(Directional)，并调高环境贡献度。
     */
    public void setAmbient(boolean ambient) {
        this.isAmbient = ambient;
        if (ambient) {
            this.type = TYPE_DIRECTIONAL;
            this.ambientStrength = 0.5f; // 增强环境光贡献
        } else {
            this.type = TYPE_POINT;
        }
    }

    /* ---- 核心参数控制 ---- */

    public int getType() { return type; }
    public void setType(int type) { this.type = type; }

    public Vector3f getPosition() { return position; }
    public void setPosition(Vector3f pos) { this.position.set(pos); }

    public Vector3f getDirection() { return direction; }
    public void setDirection(Vector3f dir) { this.direction.set(dir).normalize(); }

    public float getRange() { return range; }
    public void setRange(float range) { this.range = range; }

    public void setSpotAngle(float innerDegrees, float outerDegrees) {
        this.innerCutOff = (float) Math.cos(Math.toRadians(innerDegrees));
        this.outerCutOff = (float) Math.cos(Math.toRadians(outerDegrees));
    }

    public float getInnerCutOff() { return innerCutOff; }
    public float getOuterCutOff() { return outerCutOff; }

    public float getAmbientStrength() { return ambientStrength; }
    public void setAmbientStrength(float strength) { this.ambientStrength = strength; }

    // 显式实现以确保 TestLightSystem 中的 get/set 正常
    public void setIntensity(float intensity) { this.intensity = intensity; }
    public float getIntensity() { return this.intensity; }

    public Color getColor() { return this.color; }
    public void setColor(Color color) { this.color = color; }
}