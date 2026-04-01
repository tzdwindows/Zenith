package com.zenith.animation.io;

import java.util.List;
import java.util.Map;

public class JsonModelData {
    public String modelName;

    // Mesh 数据
    public float[] vertices; // 交织后的数据
    public int[] indices;

    // Skeleton 数据
    public SkeletonData skeleton;

    // Animation 数据
    public List<AnimationData> animations;

    public static class SkeletonData {
        public int[] parentIndices;
        public String[] jointNames;
        public float[][] inverseBindPoses; // 存储为 16位 float 数组的数组
    }

    public static class AnimationData {
        public String name;
        public float duration;
        public List<JointTrackData> tracks;
    }

    public static class JointTrackData {
        public int jointIndex;
        public float[] translations; // [time, x, y, z, ...]
        public float[] rotations;    // [time, x, y, z, w, ...]
        public float[] scales;
    }
}