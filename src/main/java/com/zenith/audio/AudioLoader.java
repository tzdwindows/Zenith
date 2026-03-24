package com.zenith.audio;

import com.zenith.asset.AssetIdentifier;
import com.zenith.asset.AssetResource;
import com.zenith.common.math.Transform;
import com.zenith.common.utils.InternalLogger;
import org.joml.Vector3f;
import org.lwjgl.stb.STBVorbisInfo;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.BufferedInputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

import static org.lwjgl.openal.AL10.*;
import static org.lwjgl.openal.AL11.alSource3i;
import static org.lwjgl.openal.EXTEfx.*;
import static org.lwjgl.stb.STBVorbis.*;

public class AudioLoader {

    public AudioLoader() {
    }

    public AudioSource create3DSource(AssetIdentifier id, Transform transform, boolean loop, AudioEffect... effects) {
        return create3DSource(id, transform.getPosition(), loop, effects);
    }

    public AudioSource create3DSource(AssetIdentifier id, Vector3f position, boolean loop, AudioEffect... effects) {
        int bufferId = AudioRegistry.getBuffer(id);
        if (bufferId == -1) return null;

        AudioSource source = new AudioSource(bufferId, loop, false);
        source.setPosition(position);

        if (effects != null && effects.length > 0) {
            // 【关键修复 1】静音直接路径 (干声) 只做一次！
            // 这样就听不到那个讨厌的“原调重影”了
            int muteFilter = alGenFilters();
            alFilteri(muteFilter, AL_FILTER_TYPE, AL_FILTER_LOWPASS);
            alFilterf(muteFilter, AL_LOWPASS_GAIN, 0.0f);
            alFilterf(muteFilter, AL_LOWPASS_GAINHF, 0.0f);
            alSourcei(source.getSourceId(), AL_DIRECT_FILTER, muteFilter);

            // 记录 filter 句柄以便后续清理 (需在 AudioSource 中实现相应方法)
            source.addFilter(muteFilter);

            // 【关键修复 2】分配效果插槽
            for (int i = 0; i < effects.length; i++) {
                if (i >= 4) break; // OpenAL 通常最多支持 4 个插槽

                int slotId = alGenAuxiliaryEffectSlots();
                alAuxiliaryEffectSloti(slotId, AL_EFFECTSLOT_EFFECT, effects[i].getEffectId());
                // 将信号发送到插槽 i
                alSource3i(source.getSourceId(), AL_AUXILIARY_SEND_FILTER, slotId, i, AL_FILTER_NULL);

                source.addEffectSlot(slotId);
            }
        }

        return source;
    }
    public AudioSource create2DSource(AssetIdentifier id, boolean loop) {
        int bufferId = AudioRegistry.getBuffer(id);
        if (bufferId == -1) return null;
        return new AudioSource(bufferId, loop, true);
    }

    private void applyEffectToSource(AudioSource source, AudioEffect effect, int sendIndex) {
        if (effect == null) return;

        // 生成效果插槽
        int slotId = alGenAuxiliaryEffectSlots();
        alAuxiliaryEffectSloti(slotId, AL_EFFECTSLOT_EFFECT, effect.getEffectId());

        // 发送信号到插槽
        alSource3i(source.getSourceId(), AL_AUXILIARY_SEND_FILTER, slotId, sendIndex, AL_FILTER_NULL);

        // 让 Source 记录插槽，以便 cleanup
        source.addEffectSlot(slotId);
        InternalLogger.debug("声源 [" + source.getSourceId() + "] 插槽 " + sendIndex + " 绑定效果: " + effect.getClass().getSimpleName());
    }

    // --- 底层加载逻辑 (保持高效加载) ---

    public int loadToBuffer(AssetResource resource) {
        String fileName = resource.getLocation().getPath().toLowerCase();
        if (fileName.endsWith(".ogg")) return loadOGG(resource);
        if (fileName.endsWith(".wav")) return loadWAV(resource);
        return -1;
    }

    private int loadOGG(AssetResource resource) {
        ByteBuffer vorbisBuffer = null;
        try {
            byte[] data = resource.getInputStream().readAllBytes();
            vorbisBuffer = MemoryUtil.memAlloc(data.length);
            vorbisBuffer.put(data).flip();
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer error = stack.mallocInt(1);
                long decoder = stb_vorbis_open_memory(vorbisBuffer, error, null);
                if (decoder == MemoryUtil.NULL) return -1;
                STBVorbisInfo info = STBVorbisInfo.malloc(stack);
                stb_vorbis_get_info(decoder, info);
                int channels = info.channels();
                ShortBuffer pcm = MemoryUtil.memAllocShort(stb_vorbis_stream_length_in_samples(decoder) * channels);
                stb_vorbis_get_samples_short_interleaved(decoder, channels, pcm);
                stb_vorbis_close(decoder);
                int bufferId = alGenBuffers();
                alBufferData(bufferId, channels == 1 ? AL_FORMAT_MONO16 : AL_FORMAT_STEREO16, pcm, info.sample_rate());
                MemoryUtil.memFree(pcm);
                return bufferId;
            }
        } catch (Exception e) {
            return -1;
        } finally {
            if (vorbisBuffer != null) MemoryUtil.memFree(vorbisBuffer);
        }
    }

    private int loadWAV(AssetResource resource) {
        try (AudioInputStream ais = AudioSystem.getAudioInputStream(new BufferedInputStream(resource.getInputStream()))) {
            AudioFormat format = ais.getFormat();
            byte[] data = ais.readAllBytes();
            ByteBuffer buffer = MemoryUtil.memAlloc(data.length);
            buffer.put(data).flip();
            int alFormat = (format.getChannels() == 1) ?
                    (format.getSampleSizeInBits() == 8 ? AL_FORMAT_MONO8 : AL_FORMAT_MONO16) :
                    (format.getSampleSizeInBits() == 8 ? AL_FORMAT_STEREO8 : AL_FORMAT_STEREO16);
            int bufferId = alGenBuffers();
            alBufferData(bufferId, alFormat, buffer, (int) format.getSampleRate());
            MemoryUtil.memFree(buffer);
            return bufferId;
        } catch (Exception e) {
            return -1;
        }
    }
}