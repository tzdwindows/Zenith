package com.zenith.audio;

import com.zenith.common.utils.InternalLogger;
import static org.lwjgl.openal.EXTEfx.*;

/**
 * AudioEffect 是所有音频效果（如混响、回声）的基类。
 */
public abstract class AudioEffect implements AutoCloseable {

    protected final int effectId;

    protected AudioEffect(int effectType) {
        this.effectId = alGenEffects();
        alEffecti(effectId, AL_EFFECT_TYPE, effectType);

        int error = org.lwjgl.openal.AL10.alGetError();
        if (error != org.lwjgl.openal.AL10.AL_NO_ERROR) {
            InternalLogger.error("创建音频效果失败，错误码: " + error);
        }
    }

    /**
     * 应用具体的混响参数
     */
    public abstract void applyParameters();

    public int getEffectId() {
        return effectId;
    }

    @Override
    public void close() {
        if (effectId != 0) {
            alDeleteEffects(effectId);
        }
    }
}