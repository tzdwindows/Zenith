package com.zenith.common.math;

import org.joml.Vector4fc;

public class Vector4f {
    public float x, y, z, w;

    public Vector4f() { this.w = 1.0f; }
    public Vector4f(float x, float y, float z, float w) {
        this.x = x; this.y = y; this.z = z; this.w = w;
    }

    public Vector4f(Vector4fc jomlVec) {
        this.x = jomlVec.x();
        this.y = jomlVec.y();
        this.z = jomlVec.z();
        this.w = jomlVec.w();
    }

    public org.joml.Vector4f toJOML() {
        return new org.joml.Vector4f(x, y, z, w);
    }

    public Vector4f set(float x, float y, float z, float w) {
        this.x = x; this.y = y; this.z = z; this.w = w;
        return this;
    }
}