package com.zenith.audio;

import com.zenith.common.utils.InternalLogger;
import com.zenith.render.Camera;
import org.joml.Vector3f;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALCCapabilities;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.openal.AL10.*;
import static org.lwjgl.openal.ALC10.*;
import static org.lwjgl.system.MemoryUtil.NULL;

/**
 * AudioManager 负责控制 OpenAL 硬件设备的生命周期。
 * 现已集成 AudioMixer 分路管理系统。
 */
public class AudioManager {

    private long device;
    private long context;

    private AudioMixer mixer;
    private final List<AudioSource> activeSources = new ArrayList<>();

    private final Vector3f atCache = new Vector3f();
    private final Vector3f upCache = new Vector3f();

    public void init() {
        InternalLogger.info("正在初始化音频系统...");

        this.device = alcOpenDevice((ByteBuffer) null);
        if (device == NULL) {
            InternalLogger.error("无法打开 OpenAL 音频设备。");
            throw new IllegalStateException("Failed to open the default OpenAL device.");
        }

        ALCCapabilities deviceCaps = ALC.createCapabilities(device);
        this.context = alcCreateContext(device, (IntBuffer) null);
        if (context == NULL) {
            alcCloseDevice(device);
            InternalLogger.error("无法创建 OpenAL 上下文。");
            throw new IllegalStateException("Failed to create OpenAL context.");
        }

        alcMakeContextCurrent(context);
        AL.createCapabilities(deviceCaps);

        // 初始化混音器
        this.mixer = new AudioMixer();

        InternalLogger.info("OpenAL 初始化成功! [版本: " + alGetString(AL_VERSION) + "]");
    }

    /**
     * 注册并追踪一个声源。
     * 被追踪的声源会自动响应 Mixer 的音量变化。
     */
    public void registerSource(AudioSource source) {
        if (!activeSources.contains(source)) {
            activeSources.add(source);
            // 注册时立即同步一次音量
            source.updateRealVolume(mixer);
        }
    }

    /**
     * 取消追踪声源（通常在声源 cleanup 时调用）。
     */
    public void unregisterSource(AudioSource source) {
        activeSources.remove(source);
    }

    /**
     * 设置特定分组的音量，并实时刷新所有受影响的声源。
     */
    public void setGroupVolume(AudioGroup group, float volume) {
        mixer.setVolume(group, volume);
        // 核心：遍历所有活跃声源，重新计算它们的物理 AL_GAIN
        for (AudioSource source : activeSources) {
            source.updateRealVolume(mixer);
        }
        InternalLogger.debug("音频分路 [" + group + "] 音量调整为: " + volume);
    }

    /**
     * 将音频监听者（Listener）的状态与指定的相机同步。
     */
    public void updateListener(Camera camera) {
        Vector3f pos = camera.getTransform().getPosition();
        alListener3f(AL_POSITION, pos.x, pos.y, pos.z);

        // 更新朝向
        camera.getTransform().getRotation().transform(atCache.set(0, 0, -1));
        camera.getTransform().getRotation().transform(upCache.set(0, 1, 0));

        float[] data = new float[]{
                atCache.x, atCache.y, atCache.z,
                upCache.x, upCache.y, upCache.z
        };
        alListenerfv(AL_ORIENTATION, data);
    }

    public void cleanup() {
        InternalLogger.info("正在关闭音频系统...");

        // 清理所有追踪的声源（可选，视你的引擎资源管理策略而定）
        activeSources.clear();

        if (context != NULL) {
            alcMakeContextCurrent(NULL);
            alcDestroyContext(context);
        }
        if (device != NULL) {
            alcCloseDevice(device);
        }
        InternalLogger.info("音频系统资源已清理完毕。");
    }

    public AudioMixer getMixer() { return mixer; }
    public long getDevice() { return device; }
    public long getContext() { return context; }
}