package com.zenith.asset;

import com.zenith.common.utils.InternalLogger;

import java.io.*;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * 代表一个加载后的资源实例。
 * 支持文件读取、目录模拟、相对路径解析以及文件更新监听。
 */
public class AssetResource implements AutoCloseable {

    private final String sourceName;
    private final AssetIdentifier location;
    private final InputStream inputStream;
    private final InputStream metaStream;
    private final long lastModified;
    private final List<Consumer<AssetResource>> updateListeners = new ArrayList<>();

    public AssetResource(String sourceName, AssetIdentifier location,
                         InputStream inputStream, InputStream metaStream, long lastModified) {
        this.sourceName = sourceName;
        this.location = location;
        this.inputStream = inputStream;
        this.metaStream = metaStream;
        if (lastModified <= 0) {
            this.lastModified = fetchLastModifiedFromLocation(location);
        } else {
            this.lastModified = lastModified;
        }
    }

    /**
     * 内部辅助方法：根据位置信息获取时间戳
     */
    private long fetchLastModifiedFromLocation(AssetIdentifier location) {
        try {
            String path = location.getPath();
            if (path.startsWith("/")) path = path.substring(1);
            URL url = AssetResource.class.getClassLoader().getResource(path);
            if (url != null) {
                java.net.URLConnection connection = url.openConnection();
                connection.setUseCaches(false);
                return connection.getLastModified();
            }
        } catch (Exception ignored) {
        }
        return 0L;
    }

    /**
     * 注册一个当文件更新时触发的事件。
     * @param listener 接收新 AssetResource 实例的回调函数
     */
    public void onUpdate(Consumer<AssetResource> listener) {
        if (listener != null) {
            this.updateListeners.add(listener);
        }
    }

    /**
     * 检查文件是否已更新。
     * 如果检测到磁盘上的时间戳更新，则创建一个全新的 AssetResource，
     * 并触发所有已注册的监听器。
     */
    public void checkForUpdates() {
        long currentDiskTimestamp = getLatestDiskTimestamp();
        if (currentDiskTimestamp > this.lastModified) {
            InternalLogger.info("检测到资源更新，准备重载: " + location.getPath());
            AssetResource newResource = loadFromResources(this.location);
            if (newResource != null) {
                for (Consumer<AssetResource> listener : updateListeners) {
                    newResource.onUpdate(listener);
                    listener.accept(newResource);
                }
            }
        }
    }

    /**
     * 辅助方法：快速获取磁盘/JAR 中资源的最新时间戳，不打开完整流
     */
    private long getLatestDiskTimestamp() {
        try {
            String path = location.getPath();
            if (path.startsWith("/")) path = path.substring(1);
            URL url = AssetResource.class.getClassLoader().getResource(path);
            if (url != null) {
                URLConnection connection = url.openConnection();
                connection.setUseCaches(false); // 关键：禁用缓存以获取最新属性
                return connection.getLastModified();
            }
        } catch (IOException ignored) {}
        return this.lastModified;
    }

    /**
     * 将资源内容读取为字符串（UTF-8）。
     * 注意：InputStream 是消耗性的，读取后该实例的流将不可再用。
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
     * 从 Classpath 加载资源。
     */
    public static AssetResource loadFromResources(AssetIdentifier id) {
        String path = id.getPath();
        if (path.startsWith("/")) path = path.substring(1);

        URL url = AssetResource.class.getClassLoader().getResource(path);
        if (url == null) {
            return null;
        }

        try {
            URLConnection connection = url.openConnection();
            // 确保在开发环境下能读取到最新修改的文件
            connection.setUseCaches(false);

            long lastModified = connection.getLastModified();
            InputStream is = connection.getInputStream();

            InputStream metaIs = null;
            URL metaUrl = AssetResource.class.getClassLoader().getResource(path + ".meta");
            if (metaUrl != null) {
                metaIs = metaUrl.openStream();
            }

            return new AssetResource("Resources", id, is, metaIs, lastModified);
        } catch (IOException e) {
            InternalLogger.error("加载资源失败: " + path, e);
            return null;
        }
    }

    public static AssetResource loadFromResources(String path) {
        return loadFromResources(new AssetIdentifier(path));
    }

    /**
     * 基于当前资源解析出一个相对路径的新资源。
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

    public boolean isDirectory() {
        String path = location.getPath();
        return path.endsWith("/") || !path.contains(".");
    }

    public long getLastModified() {
        return lastModified;
    }

    public InputStream getInputStream() {
        return inputStream;
    }

    public AssetIdentifier getLocation() {
        return location;
    }

    public String getSourceName() {
        return sourceName;
    }

    @Override
    public void close() throws IOException {
        try {
            if (inputStream != null) inputStream.close();
        } finally {
            if (metaStream != null) metaStream.close();
        }
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
            } catch (IOException ignored) {
            }
        });
    }

    public boolean exists() {
        return this.getInputStream() != null;
    }

    @Override
    public String toString() {
        return location.toString() + (isDirectory() ? "/" : "");
    }
}