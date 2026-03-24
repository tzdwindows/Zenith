package com.zenith.audio;

import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.openal.AL10.*;
import static org.lwjgl.openal.EXTEfx.alDeleteAuxiliaryEffectSlots;
import static org.lwjgl.openal.EXTEfx.alDeleteFilters;

/**
 * 声源类，封装了 OpenAL 的 Source 功能。
 * 已集成音频混合器分路管理。
 */
public class AudioSource {

    private final int sourceId;
    private final int bufferId;
    private final List<Integer> filterIds = new ArrayList<>();
    private final List<Integer> slotIds = new ArrayList<>();

    // 混音器相关属性
    private AudioGroup group = AudioGroup.SFX; // 默认分路为特效
    private float localGain = 1.0f;           // 开发者手动设置的音量 (0.0 - 1.0)

    public AudioSource(int bufferId, boolean loop, boolean relative) {
        this.bufferId = bufferId;
        this.sourceId = alGenSources();
        alSourcei(sourceId, AL_BUFFER, bufferId);
        alSourcei(sourceId, AL_LOOPING, loop ? AL_TRUE : AL_FALSE);
        alSourcei(sourceId, AL_SOURCE_RELATIVE, relative ? AL_TRUE : AL_FALSE);

        // 初始物理音量设置
        alSourcef(sourceId, AL_GAIN, 1.0f);
        alSourcef(sourceId, AL_PITCH, 1.0f);
    }

    /**
     * 设置所属的音频分路，并立即根据混音器状态更新物理音量
     */
    public void setGroup(AudioGroup group, AudioMixer mixer) {
        this.group = group;
        updateRealVolume(mixer);
    }

    /**
     * 更新物理音量：Local Gain * Group Gain * Master Gain
     */
    public void updateRealVolume(AudioMixer mixer) {
        float finalGain = localGain * mixer.getOutputGain(this.group);
        alSourcef(sourceId, AL_GAIN, finalGain);
    }

    /**
     * 设置此声源的局部增益。注意：物理上的 AL_GAIN 将受混音器影响。
     */
    public void setGain(float gain, AudioMixer mixer) {
        this.localGain = gain;
        updateRealVolume(mixer);
    }

    public int getSourceId() {
        return sourceId;
    }

    public void addEffectSlot(int slotId) {
        if (slotId != 0) {
            this.slotIds.add(slotId);
        }
    }

    public void addFilter(int filterId) {
        if (filterId != 0) {
            this.filterIds.add(filterId);
        }
    }

    public void setPosition(Vector3f pos) {
        alSource3f(sourceId, AL_POSITION, pos.x, pos.y, pos.z);
    }

    public void setVelocity(Vector3f vel) {
        alSource3f(sourceId, AL_VELOCITY, vel.x, vel.y, vel.z);
    }

    public void setPitch(float pitch) {
        alSourcef(sourceId, AL_PITCH, pitch);
    }

    public void play() {
        alSourcePlay(sourceId);
    }

    public void pause() {
        alSourcePause(sourceId);
    }

    public void stop() {
        alSourceStop(sourceId);
    }

    public boolean isPlaying() {
        return alGetSourcei(sourceId, AL_SOURCE_STATE) == AL_PLAYING;
    }

    public void cleanup() {
        stop();
        alDeleteSources(sourceId);
        for (int slotId : slotIds) alDeleteAuxiliaryEffectSlots(slotId);
        for (int filterId : filterIds) alDeleteFilters(filterId);
        slotIds.clear();
        filterIds.clear();
    }
}