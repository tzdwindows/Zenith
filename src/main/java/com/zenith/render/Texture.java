package com.zenith.render;

import com.zenith.asset.AssetResource;
import com.zenith.common.utils.InternalLogger;
import java.io.IOException;

public abstract class Texture {
    protected int width, height;
    protected final String sourceName;

    protected Texture(int width, int height, String sourceName) {
        this.width = width;
        this.height = height;
        this.sourceName = sourceName;
        InternalLogger.info(String.format("Allocating Texture [%dx%d] from source: %s",
                width, height, sourceName));
    }

    public abstract void load(AssetResource resource) throws IOException;

    public abstract void bind(int slot);
    public abstract void unbind();
    public abstract void dispose();

    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public String getSourceName() { return sourceName; }

    public abstract int getId();
}