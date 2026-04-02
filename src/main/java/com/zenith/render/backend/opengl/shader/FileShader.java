package com.zenith.render.backend.opengl.shader;

import com.zenith.asset.AssetResource;
import com.zenith.common.utils.InternalLogger;

import java.io.IOException;

/**
 * FileShader
 * 负责从 AssetResource 系统加载源码字符串。
 * 依赖基类 GLShader 的 ShaderPreprocessor 处理 #include 逻辑。
 */
public class FileShader extends GLShader {

    private final AssetResource vertexResource;
    private final AssetResource fragmentResource;

    /**
     * 通过 AssetResource 实例创建 Shader
     * @param name 着色器名称
     * @param vertexResource 顶点着色器资源
     * @param fragmentResource 片元着色器资源
     * @param includePaths 额外的包含路径资源
     */
    public FileShader(String name, AssetResource vertexResource, AssetResource fragmentResource, AssetResource... includePaths) {
        super(name,
                readResourceSafe(vertexResource),
                readResourceSafe(fragmentResource),
                includePaths
        );

        this.vertexResource = vertexResource;
        this.fragmentResource = fragmentResource;
        setupHotReload();
    }

    /**
     * 静态辅助方法：通过路径直接加载
     */
    public static FileShader load(String name, String vertPath, String fragPath) {
        AssetResource vert = AssetResource.loadFromResources(vertPath);
        AssetResource frag = AssetResource.loadFromResources(fragPath);

        if (vert == null || frag == null) {
            throw new RuntimeException("Shader 资源加载失败: " + vertPath + " 或 " + fragPath);
        }

        return new FileShader(name, vert, frag);
    }

    /**
     * 安全读取资源字符串
     */
    private static String readResourceSafe(AssetResource resource) {
        if (resource == null) return "";
        try {
            return resource.readAsString();
        } catch (IOException e) {
            InternalLogger.error("无法读取 Shader 资源内容: " + resource.getLocation(), e);
            return "";
        }
    }

    /**
     * 设置热重载监听
     */
    private void setupHotReload() {
        // 顶点着色器更新回调
        vertexResource.onUpdate(res -> {
            InternalLogger.info("检测到顶点着色器更新: " + name); // 直接使用基类继承的 name 字段
            recompile();
        });

        // 片元着色器更新回调
        fragmentResource.onUpdate(res -> {
            InternalLogger.info("检测到片元着色器更新: " + name);
            recompile();
        });
    }

    /**
     * 重新编译流程
     * 注意：由于 GLShader 的 rendererID 是 final 且在构造函数中初始化，
     * 完整的热重载通常需要 GLShader 提供一个重载/更新的方法。
     * 如果基类不支持，此处建议通过重新实例化 Shader 对象来处理。
     */
    public void recompile() {
        String newVert = readResourceSafe(vertexResource);
        String newFrag = readResourceSafe(fragmentResource);

        // 逻辑占位：此处应调用基类的 reload 或重新编译方法
        // 如果基类没有实现 reload，你可能需要在 GLShader 中添加该功能以更新 rendererID
        InternalLogger.warn("Shader 重载功能需要在 GLShader 基类中支持 rendererID 的更新");
    }
}