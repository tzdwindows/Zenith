package com.zenith.render.backend.opengl.shader;

import com.zenith.common.utils.InternalLogger;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 全局着色器注册表，管理所有着色器的生命周期。
 */
public class GLShaderRegistry {
    private static final Map<String, GLShader> SHADER_CACHE = new HashMap<>();
    public static final String STANDARD = "standard";
    public static final String IMAGE = "image";
    public static final String UI_DEFAULT = "ui_default";
    public static final String UI_SOLID = "ui_solid";
    public static final String UI_TEXT = "ui_text";
    public static final String ENTITY = "entity";
    public static final String EMISSIVE = "emissive";
    public static final String WATER = "water";
    public static final String SKY = "sky";
    public static final String ANIMATION = "animation";
    static {
        register(STANDARD, new StandardShader());
        register(IMAGE, new ImageShader());
        register(UI_DEFAULT, new UIShader());
        register(UI_SOLID, new SolidColorUIShader());
        register(UI_TEXT, new UITextShader());
        register(ENTITY, new EntityShader());
        register(EMISSIVE, new EmissiveShader());
        register(WATER, new WaterShader());
        register(SKY, new SkyShader());
        register(ANIMATION, new AnimationShader());
    }

    /**
     * 注册一个着色器。如果名称已存在，会先释放旧的着色器资源。
     */
    public static void register(String name, GLShader shader) {
        if (SHADER_CACHE.containsKey(name)) {
            SHADER_CACHE.get(name).dispose();
        }
        SHADER_CACHE.put(name, shader);
    }

    /**
     * 获取指定名称的着色器，并自动进行类型转换。
     */
    @SuppressWarnings("unchecked")
    public static <T extends GLShader> T get(String name) {
        GLShader shader = SHADER_CACHE.get(name);
        if (shader == null) {
            InternalLogger.error("Shader not found in registry: " + name);
        }
        return (T) shader;
    }

    /**
     * 如果着色器不存在，则创建并注册它（惰性加载）。
     */
    @SuppressWarnings("unchecked")
    public static <T extends GLShader> T getOrCreate(String name, Supplier<T> supplier) {
        return (T) SHADER_CACHE.computeIfAbsent(name, k -> supplier.get());
    }

    /**
     * 程序退出时调用，释放所有 GPU 端的 Shader Program 资源。
     */
    public static void disposeAll() {
        for (GLShader shader : SHADER_CACHE.values()) {
            if (shader != null) {
                shader.dispose();
            }
        }
        SHADER_CACHE.clear();
        InternalLogger.info("All shaders disposed from registry.");
    }
}