package com.zenith.animation.runtime;

import com.zenith.common.math.Transform;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class SamplingJob {

    // 临时变量，避免在循环中重复创建
    private final Vector3f v0 = new Vector3f();
    private final Vector3f v1 = new Vector3f();
    private final Quaternionf q0 = new Quaternionf();
    private final Quaternionf q1 = new Quaternionf();

    /**
     * 执行采样
     * @param skeleton 骨架
     * @param clip 动画剪辑
     * @param time 当前播放时间（秒）
     * @param outPose 输出的局部变换数组
     */
    public void execute(Skeleton skeleton, AnimationClip clip, float time, Transform[] outPose) {
        float duration = clip.getDuration();
        float t = duration > 0 ? time % duration : 0;
        if (t < 0) t += duration;

        for (int i = 0; i < skeleton.numJoints(); i++) {
            int trackIdx = clip.getTrackIndex(i);
            Transform local = outPose[i];
            if (trackIdx == -1) {
                local.identity();
                continue;
            }
            sampleVec3(clip.getTranslationTrack(trackIdx), t, local.getPosition(), 4);
            sampleQuat(clip.getRotationTrack(trackIdx), t, local.getRotation());
            sampleVec3(clip.getScaleTrack(trackIdx), t, local.getScale(), 4);
            local.setDirty();
        }
    }

    private void sampleVec3(float[] track, float time, Vector3f dest, int stride) {
        if (track == null || track.length == 0) return;

        // 单帧处理
        if (track.length == stride) {
            dest.set(track[1], track[2], track[3]);
            return;
        }

        int count = track.length / stride;
        int i1 = findKeyframeIndex(track, time, stride, count);
        int i0 = i1 - 1;

        float t0 = track[i0 * stride];
        float t1 = track[i1 * stride];
        float alpha = (time - t0) / (t1 - t0);

        // Lerp 插值
        float x = track[i0 * stride + 1] + alpha * (track[i1 * stride + 1] - track[i0 * stride + 1]);
        float y = track[i0 * stride + 2] + alpha * (track[i1 * stride + 2] - track[i0 * stride + 2]);
        float z = track[i0 * stride + 3] + alpha * (track[i1 * stride + 3] - track[i0 * stride + 3]);
        dest.set(x, y, z);
    }

    private void sampleQuat(float[] track, float time, Quaternionf dest) {
        if (track == null || track.length == 0) return;
        int stride = 5;

        if (track.length == stride) {
            dest.set(track[1], track[2], track[3], track[4]);
            return;
        }

        int count = track.length / stride;
        int i1 = findKeyframeIndex(track, time, stride, count);
        int i0 = i1 - 1;

        float t0 = track[i0 * stride];
        float t1 = track[i1 * stride];
        float alpha = (time - t0) / (t1 - t0);

        q0.set(track[i0 * stride + 1], track[i0 * stride + 2], track[i0 * stride + 3], track[i0 * stride + 4]);
        q1.set(track[i1 * stride + 1], track[i1 * stride + 2], track[i1 * stride + 3], track[i1 * stride + 4]);

        // 使用 JOML 的 Slerp
        q0.slerp(q1, alpha, dest);
    }

    /**
     * 高效二分查找：在平铺数组中寻找第一个时间戳大于给定时间的 Key
     */
    private int findKeyframeIndex(float[] track, float time, int stride, int count) {
        // 边界快速返回
        if (time <= track[0]) return 1;
        if (time >= track[(count - 1) * stride]) return count - 1;

        int low = 0;
        int high = count - 1;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            float midTime = track[mid * stride];

            if (midTime < time) {
                low = mid + 1;
            } else if (midTime > time) {
                high = mid - 1;
            } else {
                return mid + 1; // 正好相等
            }
        }
        return low;
    }
}