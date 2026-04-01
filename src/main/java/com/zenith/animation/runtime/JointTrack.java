package com.zenith.animation.runtime;

import com.zenith.animation.runtime.Keyframes.*;

public class JointTrack {
    // 对应 Skeleton 中的 jointIndex
    public int jointIndex;

    // 这里的数组必须按 time 从小到大排序，方便二分查找
    public TranslationKey[] translations;
    public RotationKey[] rotations;
    public ScaleKey[] scales;

    public JointTrack(int jointIndex) {
        this.jointIndex = jointIndex;
    }
}