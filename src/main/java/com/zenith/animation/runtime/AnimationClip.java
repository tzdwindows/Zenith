package com.zenith.animation.runtime;

import org.joml.Vector3f;

import java.util.Arrays;
import java.util.Objects;

/**
 * 工业级动画剪辑：采用扁平化数组存储，最大化 CPU 缓存命中率并减少内存碎片。
 */
public class AnimationClip {
    private final String name;
    private final float duration;

    // 每一个索引对应 Skeleton 中的 jointIndex
    // 如果该骨骼没有动画，对应项为 -1
    private final int[] jointToTrackMap;

    // 核心数据：使用一维数组平铺存储，避免 Keyframe 对象
    // 结构：[time0, x0, y0, z0, time1, x1, y1, z1, ...]
    private final float[][] translationTracks; // 每条轨道一个 float 数组
    private final float[][] rotationTracks;    // 每条轨道存储四元数 (time, x, y, z, w)
    private final float[][] scaleTracks;

    public AnimationClip(String name, float duration, int numSkeletonJoints, JointData[] rawData) {
        this.name = Objects.requireNonNull(name);
        this.duration = duration;

        // 初始化查找表，大小与 Skeleton 一致，实现 O(1) 查找
        this.jointToTrackMap = new int[numSkeletonJoints];
        Arrays.fill(jointToTrackMap, -1);

        this.translationTracks = new float[rawData.length][];
        this.rotationTracks = new float[rawData.length][];
        this.scaleTracks = new float[rawData.length][];

        for (int i = 0; i < rawData.length; i++) {
            JointData data = rawData[i];
            jointToTrackMap[data.jointIndex] = i;

            // 将对象数据平铺进 float 数组
            this.translationTracks[i] = flattenVec3(data.translations);
            this.rotationTracks[i] = flattenQuat(data.rotations);
            this.scaleTracks[i] = flattenScale(data.scales);
        }
    }

    /**
     * O(1) 时间复杂度获取指定骨骼的轨道 ID
     */
    public int getTrackIndex(int jointIndex) {
        if (jointIndex < 0 || jointIndex >= jointToTrackMap.length) return -1;
        return jointToTrackMap[jointIndex];
    }

    // --- 内部辅助方法：用于将加载时的对象转为运行时的高效数组 ---

    /**
     * 通用的 Vec3 平铺方法，支持 Translation 和 Scale
     */
    private float[] flattenVec3(float time0, Vector3f value0, Keyframes.TranslationKey[] keys) {
        // 如果没有关键帧，返回空数组，采样时将使用默认值
        if (keys == null || keys.length == 0) return new float[0];

        float[] flat = new float[keys.length * 4]; // [time, x, y, z]
        for (int i = 0; i < keys.length; i++) {
            int offset = i * 4;
            flat[offset] = keys[i].time;
            flat[offset + 1] = keys[i].value.x;
            flat[offset + 2] = keys[i].value.y;
            flat[offset + 3] = keys[i].value.z;
        }
        return flat;
    }
    private float[] flattenVec3(Keyframes.TranslationKey[] keys) {
        if (keys == null || keys.length == 0) return new float[0];
        float[] flat = new float[keys.length * 4]; // time + x + y + z
        for (int i = 0; i < keys.length; i++) {
            int offset = i * 4;
            flat[offset] = keys[i].time;
            flat[offset + 1] = keys[i].value.x;
            flat[offset + 2] = keys[i].value.y;
            flat[offset + 3] = keys[i].value.z;
        }
        return flat;
    }

    /**
     * 为 ScaleKey 提供重载
     */
    private float[] flattenScale(Keyframes.ScaleKey[] keys) {
        if (keys == null || keys.length == 0) return new float[0];

        float[] flat = new float[keys.length * 4];
        for (int i = 0; i < keys.length; i++) {
            int offset = i * 4;
            flat[offset] = keys[i].time;
            flat[offset + 1] = keys[i].value.x;
            flat[offset + 2] = keys[i].value.y;
            flat[offset + 3] = keys[i].value.z;
        }
        return flat;
    }

    private float[] flattenQuat(Keyframes.RotationKey[] keys) {
        if (keys == null || keys.length == 0) return new float[0];
        float[] flat = new float[keys.length * 5]; // time + x + y + z + w
        for (int i = 0; i < keys.length; i++) {
            int offset = i * 5;
            flat[offset] = keys[i].time;
            flat[offset + 1] = keys[i].value.x;
            flat[offset + 2] = keys[i].value.y;
            flat[offset + 3] = keys[i].value.z;
            flat[offset + 4] = keys[i].value.w;
        }
        return flat;
    }

    // --- Getters ---

    public float[] getTranslationTrack(int trackIndex) { return translationTracks[trackIndex]; }
    public float[] getRotationTrack(int trackIndex) { return rotationTracks[trackIndex]; }
    public float[] getScaleTrack(int trackIndex) { return scaleTracks[trackIndex]; }
    public float getDuration() { return duration; }

    /**
     * 临时内部类，仅用于构造时传递数据
     */
    public static class JointData {
        public int jointIndex;
        public Keyframes.TranslationKey[] translations;
        public Keyframes.RotationKey[] rotations;
        public Keyframes.ScaleKey[] scales;
    }
}