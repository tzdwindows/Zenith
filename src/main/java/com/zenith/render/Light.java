package com.zenith.render;

import com.zenith.common.math.Color;
import org.joml.Vector3f;

public abstract class Light {
    protected Color color;
    protected float intensity;

    protected Light(Color color, float intensity) {
        this.color = color;
        this.intensity = intensity;
    }

    public Color getColor() { return color; }
    public float getIntensity() { return intensity; }

    /** 每种灯光对渲染的影响不同（如点光源有衰减，平行光只有方向） */
    public abstract void update();
}