package com.zenith.audio;

import com.zenith.asset.AssetIdentifier;
import com.zenith.asset.AssetResource;
import com.zenith.common.utils.InternalLogger;

import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.openal.AL10.alDeleteBuffers;

public class AudioRegistry {

    private static final Map<AssetIdentifier, Integer> bufferCache = new HashMap<>();
    private static final AudioLoader loader = new AudioLoader();

    public static int getBuffer(AssetIdentifier id) {
        if (bufferCache.containsKey(id)) {
            InternalLogger.debug("从缓存中复用音频 Buffer: " + id);
            return bufferCache.get(id);
        }

        AssetResource resource = AssetResource.loadFromResources(id);
        if (resource == null) {
            InternalLogger.error("音频注册失败，找不到资源: " + id);
            return -1;
        }

        // 修改点：使用实例对象调用方法
        int bufferId = loader.loadToBuffer(resource);

        if (bufferId != -1) {
            bufferCache.put(id, bufferId);
            InternalLogger.info("音频已注册并缓存: " + id + " [ID: " + bufferId + "]");
        }

        try {
            resource.close();
        } catch (Exception e) {
            InternalLogger.error("关闭音频资源流出错: " + id, e);
        }
        return bufferId;
    }

    public static void unload(AssetIdentifier id) {
        if (bufferCache.containsKey(id)) {
            int bufferId = bufferCache.remove(id);
            alDeleteBuffers(bufferId);
            InternalLogger.info("已从内存中卸载音频资源: " + id);
        }
    }

    public static void cleanup() {
        InternalLogger.info("正在清空 AudioRegistry 缓存...");
        for (int bufferId : bufferCache.values()) {
            alDeleteBuffers(bufferId);
        }
        bufferCache.clear();
    }
}