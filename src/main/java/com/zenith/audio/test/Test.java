package com.zenith.audio.test;

import com.zenith.asset.AssetIdentifier;
import com.zenith.audio.*;
import com.zenith.common.utils.InternalLogger;
import org.joml.Vector3f;

/**
 * Zenith 音频系统集成测试
 * 测试目标：非静态 AudioLoader、效果叠加（混响 + 升降调）
 */
public class Test {

    public static void main(String[] args) {
        // 1. 初始化音频硬件管理器
        AudioManager audioManager = new AudioManager();
        try {
            audioManager.init();
        } catch (Exception e) {
            InternalLogger.error("音频系统初始化失败", e);
            return;
        }

        // 2. 实例化非静态的 AudioLoader
        AudioLoader loader = new AudioLoader();

        // 3. 定义资源标识符
        AssetIdentifier soundId = new AssetIdentifier("zenith", "sounds/test.wav");

        // 4. 创建多个音频效果
        // 效果 A: 石室混响
        ReverbEffect reverb = ReverbEffect.createEtherealVoid();

        PitchShifterEffect pitchShift2 = new PitchShifterEffect();
        pitchShift2.setPitch(0);

        // 5. 加载并创建 3D 声源
        // 传入变长参数：同时应用 reverb 和 pitchShift
        Vector3f soundPos = new Vector3f(0, 0, -5.0f);
        AudioSource source = loader.create3DSource(soundId, soundPos, true, reverb,pitchShift2);

        if (source != null) {
            InternalLogger.info("开始播放测试音频（叠加效果：混响 + 升调）: " + soundId);
            source.play();
            long startTime = System.currentTimeMillis();
            while (System.currentTimeMillis() - startTime < 10000) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    break;
                }
            }
            source.cleanup();
        }
        reverb.close();

        AudioRegistry.cleanup();
        audioManager.cleanup();
        pitchShift2.close();

        InternalLogger.info("测试完成。");
    }
}