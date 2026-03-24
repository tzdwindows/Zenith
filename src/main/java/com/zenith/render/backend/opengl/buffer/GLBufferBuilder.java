package com.zenith.render.backend.opengl.buffer;

import com.zenith.common.utils.InternalLogger;
import static org.lwjgl.opengl.GL15.*;

public abstract class GLBuffer {
    protected int rendererID;
    protected int target;

    public GLBuffer(int target) {
        this.target = target;
        this.rendererID = glGenBuffers();
    }

    public void bind() {
        glBindBuffer(target, rendererID);
    }

    public void unbind() {
        glBindBuffer(target, 0);
    }

    public void dispose() {
        glDeleteBuffers(rendererID);
        InternalLogger.info("Buffer disposed: " + rendererID);
    }
}