package com.zenith.asset;

import com.zenith.common.utils.InternalLogger;

import java.io.*;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * 代表一个加载后的资源实例。
 * 支持文件读取、目录模拟以及相对路径解析。
 */
public class AssetResource implements AutoCloseable {

    private final String sourceName;
    private final AssetIdentifier location;
    private final InputStream inputStream;
    private final InputStream metaStream;

    public AssetResource(String sourceName, AssetIdentifier location,
                         InputStream inputStream, InputStream metaStream) {
        this.sourceName = sourceName;
        this.location = location;
        this.inputStream = inputStream;
        this.metaStream = metaStream;
    }

    /**
     * 判断当前资源是否被视为一个目录。
     * 在 Classpath 环境下，通常以 "/" 结尾，或者没有文件扩展名。
     */
    public boolean isDirectory() {
        String path = location.getPath();
        return path.endsWith("/") || !path.contains(".");
    }

    /**
     * 基于当前资源（作为目录）解析出一个相对路径的新资源。
     * * @param relativePath 相对路径，例如 "common.glsl" 或 "lib/math.glsl"
     * @return 新的 AssetResource 实例，如果不存在则返回 null
     */
    public AssetResource resolve(String relativePath) {
        String base = location.getPath();
        if (!base.endsWith("/")) {
            base += "/";
        }
        String finalPath = (base + relativePath).replace("//", "/");
        AssetIdentifier newId = new AssetIdentifier(location.getNamespace(), finalPath);
        return loadFromResources(newId);
    }

    /**
     * 将资源内容读取为字符串（UTF-8）。
     */
    public String readAsString() throws IOException {
        if (inputStream == null) {
            throw new IOException("无法读取内容：InputStream 为空 (该资源可能是纯目录)");
        }
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * 获取资源的数据流
     */
    public InputStream getInputStream() {
        return inputStream;
    }

    /**
     * 检查是否有元数据
     */
    public boolean hasMetadata() {
        return metaStream != null;
    }

    /**
     * 获取元数据的流
     */
    public Optional<InputStream> getMetaStream() {
        return Optional.ofNullable(metaStream);
    }

    public AssetIdentifier getLocation() {
        return location;
    }

    public String getSourceName() {
        return sourceName;
    }

    /**
     * 释放底层流资源
     */
    @Override
    public void close() throws IOException {
        try {
            if (inputStream != null) inputStream.close();
        } finally {
            if (metaStream != null) metaStream.close();
        }
    }

    /**
     * 从 Classpath 加载资源。
     * 支持文件和潜在的目录路径。
     */
    public static AssetResource loadFromResources(AssetIdentifier id) {
        String path = id.getPath();
        if (path.startsWith("/")) path = path.substring(1);
        InputStream is = AssetResource.class.getClassLoader().getResourceAsStream(path);
        if (is == null && !path.endsWith("/")) {
            return null;
        }

        return new AssetResource("Resources", id, is, null);
    }

    public static AssetResource loadFromResources(String path) {
        return loadFromResources(new AssetIdentifier(path));
    }

    public String getOrCreateLocalCachePath() throws IOException {
        String userHome = System.getProperty("user.home");
        Path cacheBase = Paths.get(userHome, ".zenith", "assets");
        Path targetPath = cacheBase.resolve(location.getNamespace()).resolve(location.getPath());

        if (Files.exists(targetPath)) {
            return targetPath.toAbsolutePath().toString();
        }

        URL resourceUrl = AssetResource.class.getResource("/" + location.getPath());
        if (resourceUrl == null) throw new FileNotFoundException("无法定位资源: " + location.getPath());

        if (resourceUrl.getProtocol().equals("jar")) {
            extractJarDirectory(resourceUrl, cacheBase.resolve(location.getNamespace()));
        } else {
            String externalForm = resourceUrl.toExternalForm();
            String pathPart = externalForm.startsWith("file:/") ? externalForm.substring(6) : externalForm;
            Path sourcePath = Paths.get(pathPart);
            copyDirectory(sourcePath.getParent(), targetPath.getParent());
        }

        return targetPath.toAbsolutePath().toString();
    }

    private void extractJarDirectory(URL resourceUrl, Path targetBase) throws IOException {
        JarURLConnection connection = (JarURLConnection) resourceUrl.openConnection();
        try (JarFile jarFile = connection.getJarFile()) {
            String prefix = location.getPath();
            if (prefix.contains("/")) {
                prefix = prefix.substring(0, prefix.lastIndexOf('/') + 1);
            }

            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.startsWith(prefix)) {
                    Path dest = targetBase.resolve(name);
                    if (entry.isDirectory()) {
                        Files.createDirectories(dest);
                    } else {
                        Files.createDirectories(dest.getParent());
                        try (InputStream is = jarFile.getInputStream(entry)) {
                            Files.copy(is, dest, StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                }
            }
        }
        InternalLogger.info("已从 JAR 提取资源至: " + targetBase);
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        Files.walk(source).forEach(getPath -> {
            try {
                Path dest = target.resolve(source.relativize(getPath));
                if (Files.isDirectory(getPath)) {
                    Files.createDirectories(dest);
                } else {
                    Files.createDirectories(dest.getParent());
                    Files.copy(getPath, dest, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
            }
        });
    }

    @Override
    public String toString() {
        return location.toString() + (isDirectory() ? "/" : "");
    }
}