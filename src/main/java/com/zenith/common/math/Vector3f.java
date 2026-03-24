package com.zenith.common.math;

import org.joml.Vector3fc;

public class Vector3f {
    public float x, y, z;

    public Vector3f() {}
    public Vector3f(float x, float y, float z) {
        this.x = x; this.y = y; this.z = z;
    }

    public Vector3f(Vector3fc jomlVec) {
        this.x = jomlVec.x();
        this.y = jomlVec.y();
        this.z = jomlVec.z();
    }

    public org.joml.Vector3f toJOML() {
        return new org.joml.Vector3f(x, y, z);
    }

    public Vector3f set(float x, float y, float z) {
        this.x = x; this.y = y; this.z = z;
        return this;
    }

    // 常用数学运算略 (add, sub, mul, normalize 等)
}