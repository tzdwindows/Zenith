package com.zenith.asset;

import com.zenith.render.backend.opengl.test.Test2;

import java.io.InputStream;
import java.util.Objects;

/**
 * 资源标识符，格式为 "namespace:path"
 */
public class AssetIdentifier {
    private final String namespace;
    private final String path;

    public AssetIdentifier(String path) {
        this.namespace = "zenith";
        this.path = path.replace('\\', '/').toLowerCase();
    }

    public AssetIdentifier(String namespace, String path) {
        this.namespace = namespace.toLowerCase();
        this.path = path.replace('\\', '/').toLowerCase();
    }

    public String getNamespace() { return namespace; }
    public String getPath() { return path; }

    @Override
    public String toString() {
        return namespace + ":" + path;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AssetIdentifier)) return false;
        AssetIdentifier that = (AssetIdentifier) o;
        return Objects.equals(namespace, that.namespace) && Objects.equals(path, that.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(namespace, path);
    }
}