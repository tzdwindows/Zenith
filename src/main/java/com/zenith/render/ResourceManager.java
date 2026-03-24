package com.zenith.render;

import com.zenith.render.Shader;
import com.zenith.render.Texture;
import java.util.HashMap;
import java.util.Map;

public abstract class ResourceManager {
    protected static Map<String, Texture> textureCache = new HashMap<>();
    protected static Map<String, Shader> shaderCache = new HashMap<>();

    /** 抽象加载方法，由具体后端（如 GLResourceManager）实现 */
    public abstract Texture loadTexture(String path);
    public abstract Shader loadShader(String name, String vertPath, String fragPath);

    public static void unloadAll() {
        textureCache.values().forEach(Texture::dispose);
        shaderCache.values().forEach(Shader::dispose);
        textureCache.clear();
        shaderCache.clear();
    }
}