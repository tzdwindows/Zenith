package com.zenith.render.backend.opengl.shader;

import com.zenith.asset.AssetResource;
import com.zenith.common.utils.InternalLogger;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 着色器预处理器，用于处理包含文件。
 */
public class ShaderPreprocessor {
    private static final Pattern INCLUDE_PATTERN = Pattern.compile("^\\s*#include\\s+\"(.+)\"\\s*$", Pattern.MULTILINE);
    private static final List<AssetResource> includeRoots = new CopyOnWriteArrayList<>();

    static {
        AssetResource defaultRoot = AssetResource.loadFromResources("shaders/include/");
        if (defaultRoot != null) {
            includeRoots.add(defaultRoot);
        }
        addIncludeRoot(AssetResource.loadFromResources("shaders/include/filament/"));
        addIncludeRoot(AssetResource.loadFromResources("shaders/include/psrdnoise/"));
        addIncludeRoot(AssetResource.loadFromResources("shaders/include/fluid/"));
        addIncludeRoot(AssetResource.loadFromResources("shaders/include/hash/"));
        addIncludeRoot(AssetResource.loadFromResources("shaders/include/nois/"));
        addIncludeRoot(AssetResource.loadFromResources("shaders/include/pathTracer/"));
        addIncludeRoot(AssetResource.loadFromResources("shaders/include/raytrace/"));
        addIncludeRoot(AssetResource.loadFromResources("shaders/include/zentih/"));
    }

    /**
     * 添加一个新的包含根目录。
     * 会检查是否已经存在相同的资源位置，避免重复添加。
     */
    public static void addIncludeRoot(AssetResource resource) {
        if (resource == null) {
            InternalLogger.warn("ShaderPreprocessor: 尝试添加空的 AssetResource，已忽略。");
            return;
        }
        boolean exists = includeRoots.stream()
                .anyMatch(root -> root.getLocation().equals(resource.getLocation()));
        if (!exists) {
            includeRoots.add(resource);
        }
    }

    /**
     * 重置并设置包含路径列表。
     */
    public static void setIncludePaths(AssetResource... resources) {
        includeRoots.clear();
        if (resources == null) return;

        for (AssetResource res : resources) {
            addIncludeRoot(res);
        }
    }

    public static String process(String source) {
        return process(source, new HashSet<>());
    }

    private static String process(String source, Set<String> includedFiles) {
        StringBuilder sb = new StringBuilder();
        String[] lines = source.split("\\r?\\n");

        for (String line : lines) {
            Matcher matcher = INCLUDE_PATTERN.matcher(line);
            if (matcher.matches()) {
                String includeFileName = matcher.group(1);

                if (includedFiles.contains(includeFileName)) continue;
                includedFiles.add(includeFileName);

                String includeSource = null;
                String finalPath = "Unknown";
                for (AssetResource root : includeRoots) {
                    if (root == null) continue;
                    try (AssetResource foundRes = root.resolve(includeFileName)) {
                        if (foundRes != null && foundRes.getInputStream() != null) {
                            includeSource = foundRes.readAsString();
                            finalPath = foundRes.getLocation().toString();
                            break;
                        }
                        if (includeFileName.contains("/")) {
                            String sub = includeFileName.substring(includeFileName.lastIndexOf("/") + 1);
                            try (AssetResource retryRes = root.resolve(sub)) {
                                if (retryRes != null && retryRes.getInputStream() != null) {
                                    includeSource = retryRes.readAsString();
                                    finalPath = retryRes.getLocation().toString();
                                    break;
                                }
                            }
                        }
                    } catch (IOException e) {}
                }

                if (includeSource != null) {
                    sb.append("\n// --- Begin Include: ").append(includeFileName)
                            .append(" (").append(finalPath).append(") ---\n");
                    sb.append(process(includeSource, includedFiles));
                    sb.append("\n// --- End Include: ").append(includeFileName).append(" ---\n");
                } else {
                    String errorMsg = "Shader Preprocessor FATAL: Cannot find \"" + includeFileName +
                            "\" in any AssetResource roots. Roots count: " + includeRoots.size();
                    InternalLogger.error(errorMsg);
                    throw new RuntimeException(errorMsg);
                }
            } else {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }
}