package com.zenith.animation.runtime;

import org.joml.Quaternionf;
import org.joml.Vector3f;

public class Keyframes {
    // 位移关键帧
    public static class TranslationKey {
        public float time;
        public final Vector3f value = new Vector3f();
    }

    // 旋转关键帧（使用四元数）
    public static class RotationKey {
        public float time;
        public final Quaternionf value = new Quaternionf();
    }

    // 缩放关键帧
    public static class ScaleKey {
        public float time;
        public final Vector3f value = new Vector3f(1, 1, 1);
    }
}