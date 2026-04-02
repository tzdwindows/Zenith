package com.zenith.render.backend.opengl.shader;

import com.zenith.asset.AssetResource;
import com.zenith.common.utils.InternalLogger;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 增强型着色器预处理器：支持包含文件处理、动态宏注入及版本号规范化。
 * 修复了 OpenGL 原生不支持 #include 以及子文件 #version 冲突的问题。
 */
public class ShaderPreprocessor {
    // 匹配 #include "file" 指令
    private static final Pattern INCLUDE_PATTERN = Pattern.compile("^\\s*#include\\s+\"(.+)\"\\s*$", Pattern.MULTILINE);
    // 用于剔除子文件中的 #version 指令
    private static final Pattern VERSION_PATTERN = Pattern.compile("^\\s*#version.*$", Pattern.MULTILINE);

    private static final List<AssetResource> includeRoots = new CopyOnWriteArrayList<>();

    static {
        // 基础包含目录
        AssetResource defaultRoot = AssetResource.loadFromResources("shaders/include/");
        if (defaultRoot != null) includeRoots.add(defaultRoot);

        // 自动添加光线追踪相关的包含目录
        String[] paths = {
                "shaders/include/filament/", "shaders/include/psrdnoise/", "shaders/include/fluid/",
                "shaders/include/hash/", "shaders/include/nois/", "shaders/include/pathTracer/",
                "shaders/include/raytrace/", "shaders/include/zentih/", "shaders/rt/"
        };
        for (String path : paths) {
            addIncludeRoot(AssetResource.loadFromResources(path));
        }
    }

    public static void addIncludeRoot(AssetResource resource) {
        if (resource == null) return;
        boolean exists = includeRoots.stream()
                .anyMatch(root -> root.getLocation().equals(resource.getLocation()));
        if (!exists) includeRoots.add(resource);
    }

    /**
     * 处理入口：处理包含关系并注入宏定义
     * @param source 原始源码
     * @param defines 需要注入的宏定义 Map
     */
    public static String process(String source, Map<String, String> defines) {
        // 1. 先进行递归包含处理（主文件保留 version，子文件剔除）
        String processed = processInternal(source, new HashSet<>(), true);

        // 2. 规范化处理：确保 #version 在第一行，#define 紧随其后
        StringBuilder sb = new StringBuilder();
        String[] lines = processed.split("\\r?\\n");
        int startLine = 0;

        // 提取并放置第一行版本号
        if (lines.length > 0 && lines[0].trim().startsWith("#version")) {
            sb.append(lines[0].trim()).append("\n");
            startLine = 1;
        } else {
            // 如果主文件没写版本号，默认补一个（可选）
            // sb.append("#version 430 core\n");
        }

        // 3. 注入宏定义 (Defines)
        if (defines != null && !defines.isEmpty()) {
            sb.append("\n// --- Dynamic Macros ---\n");
            for (Map.Entry<String, String> entry : defines.entrySet()) {
                sb.append("#define ").append(entry.getKey()).append(" ").append(entry.getValue()).append("\n");
            }
            sb.append("// ----------------------\n\n");
        }

        // 4. 拼装剩余内容
        for (int i = startLine; i < lines.length; i++) {
            sb.append(lines[i]).append("\n");
        }

        return sb.toString();
    }

    public static String process(String source) {
        return process(source, null);
    }

    /**
     * 内部递归处理逻辑
     * @param isMain 是否为主文件（主文件保留 #version）
     */
    private static String processInternal(String source, Set<String> includedFiles, boolean isMain) {
        StringBuilder sb = new StringBuilder();

        // 如果不是主文件，先剔除掉它自带的 #version，防止 C0204 错误
        String currentSource = isMain ? source : VERSION_PATTERN.matcher(source).replaceAll("// [Sub-file version removed]");

        String[] lines = currentSource.split("\\r?\\n");

        for (String line : lines) {
            String trimmed = line.trim();

            // 跳过注释掉的包含指令
            if (trimmed.startsWith("//")) {
                sb.append(line).append("\n");
                continue;
            }

            Matcher matcher = INCLUDE_PATTERN.matcher(line);
            if (matcher.matches()) {
                String includeFileName = matcher.group(1);

                // 防止循环包含
                if (includedFiles.contains(includeFileName)) {
                    continue;
                }
                includedFiles.add(includeFileName);

                String includeSource = resolveInclude(includeFileName);

                if (includeSource != null) {
                    sb.append("\n// >>> Begin Include: ").append(includeFileName).append("\n");
                    // 递归处理子文件，isMain 设为 false
                    sb.append(processInternal(includeSource, includedFiles, false));
                    sb.append("\n// <<< End Include: ").append(includeFileName).append("\n");
                } else {
                    InternalLogger.error("Shader Preprocessor: Cannot find include file: " + includeFileName);
                    throw new RuntimeException("Shader Preprocessor Error: Cannot find \"" + includeFileName + "\"");
                }
            } else {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }

    private static String resolveInclude(String name) {
        for (AssetResource root : includeRoots) {
            if (root == null) continue;

            try {
                // 1. 尝试直接解析（全路径或相对根目录路径）
                try (AssetResource res = root.resolve(name)) {
                    if (res != null && res.exists()) return res.readAsString();
                }

                // 2. 模糊匹配：如果包含路径，尝试只用文件名匹配（解决 pathTracer/common/xxx 这种路径简写）
                if (name.contains("/")) {
                    String fileNameOnly = name.substring(name.lastIndexOf("/") + 1);
                    try (AssetResource res = root.resolve(fileNameOnly)) {
                        if (res != null && res.exists()) return res.readAsString();
                    }
                }
            } catch (IOException ignored) {}
        }
        return null;
    }
}