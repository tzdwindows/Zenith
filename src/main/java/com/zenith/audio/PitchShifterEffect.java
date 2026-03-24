package com.zenith.audio;

import static org.lwjgl.openal.EXTEfx.*;

/**
 * PitchShifterEffect 允许在不改变播放速度的情况下改变音高。
 */
public class PitchShifterEffect extends AudioEffect {

    public PitchShifterEffect() {
        super(AL_EFFECT_PITCH_SHIFTER);
        setPitch(0);
    }

    /**
     * 设置音高偏移
     * @param semitones 半音数，范围 [-12, 12]
     * -12 是降低一个八度，12 是升高一个八度，0 是原调。
     */
    public void setPitch(int semitones) {
        int tuned = Math.max(-12, Math.min(12, semitones));
        alEffecti(effectId, AL_PITCH_SHIFTER_COARSE_TUNE, tuned);
        alEffecti(effectId, AL_PITCH_SHIFTER_FINE_TUNE, 0);
    }

    @Override
    public void applyParameters() {}

    /**
     * 快速创建预设：怪兽/低沉音效
     */
    public static PitchShifterEffect createDeepMonster() {
        PitchShifterEffect effect = new PitchShifterEffect();
        effect.setPitch(-7);
        return effect;
    }

    /**
     * 快速创建预设：花栗鼠/尖锐音效
     */
    public static PitchShifterEffect createChipmunk() {
        PitchShifterEffect effect = new PitchShifterEffect();
        effect.setPitch(8);
        return effect;
    }
}