package com.zenith.audio;

import static org.lwjgl.openal.EXTEfx.*;

/**
 * 混响效果
 */
public class ReverbEffect extends AudioEffect {

    public ReverbEffect() {
        super(AL_EFFECT_REVERB);
        setPreset(0.5f, 1.49f, 0.1f);
    }

    /**
     * 快速设置混响参数
     * @param density 密度 (0.0 to 1.0)
     * @param decay 衰减时间 (0.1 to 20.0)
     * @param gain 增益 (0.0 to 1.0)
     */
    public void setPreset(float density, float decay, float gain) {
        alEffectf(effectId, AL_REVERB_DENSITY, density);
        alEffectf(effectId, AL_REVERB_DECAY_TIME, decay);
        alEffectf(effectId, AL_REVERB_GAIN, gain);
    }

    @Override
    public void applyParameters() {
    }


    public static ReverbEffect createStoneRoom() {
        ReverbEffect effect = new ReverbEffect();
        effect.setPreset(1.0f, 1.2f, 0.3f);
        return effect;
    }

    public static ReverbEffect createLargeHall() {
        ReverbEffect effect = new ReverbEffect();
        effect.setPreset(0.8f, 5.0f, 0.5f);
        return effect;
    }

    /**
     * 【新增】空灵深渊：极重混响效果。
     * 模拟巨大的洞穴或宇宙空间，伴随极长的尾音。
     */
    public static ReverbEffect createEtherealVoid() {
        ReverbEffect effect = new ReverbEffect();
        effect.setPreset(1.0f, 15.0f, 0.8f);
        alEffectf(effect.effectId, AL_REVERB_REFLECTIONS_GAIN, 0.7f);
        alEffectf(effect.effectId, AL_REVERB_LATE_REVERB_GAIN, 1.0f);
        return effect;
    }
}