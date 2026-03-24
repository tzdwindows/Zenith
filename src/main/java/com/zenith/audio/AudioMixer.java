package com.zenith.audio;

import java.util.EnumMap;
import java.util.Map;

public class AudioMixer {
    // 存储每个分组的原始音量设置 (0.0 - 1.0)
    private final Map<AudioGroup, Float> groupVolumes = new EnumMap<>(AudioGroup.class);

    public AudioMixer() {
        // 初始化所有分路音量为满额
        for (AudioGroup group : AudioGroup.values()) {
            groupVolumes.put(group, 1.0f);
        }
    }

    public void setVolume(AudioGroup group, float volume) {
        groupVolumes.put(group, Math.max(0, Math.min(1, volume)));
    }

    public float getVolume(AudioGroup group) {
        return groupVolumes.getOrDefault(group, 1.0f);
    }

    /**
     * 计算特定分组的实际输出音量：分组音量 * Master音量
     */
    public float getOutputGain(AudioGroup group) {
        if (group == AudioGroup.MASTER) {
            return getVolume(AudioGroup.MASTER);
        }
        return getVolume(group) * getVolume(AudioGroup.MASTER);
    }
}