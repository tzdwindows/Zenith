package com.zenith.render.backend.opengl.texture;

import com.zenith.asset.AssetResource;
import com.zenith.common.utils.InternalLogger;
import com.zenith.render.Texture;
import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL30.*;

public class GLTexture extends Texture {
    private int rendererID;

    /**
     * 构造函数
     */
    public GLTexture(AssetResource resource) throws IOException {
        super(0, 0, resource.getLocation().toString());
        this.load(resource);
    }

    public GLTexture(int width, int height, ByteBuffer data, int format) {
        super(width, height, "Raw_Memory_Buffer");

        this.rendererID = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, rendererID);

        // 如果是单通道（字体），设置对齐
        if (format == GL_RED) {
            glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
        }
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        int internalFormat = (format == GL_RED) ? GL_R8 : GL_RGBA8;
        glTexImage2D(GL_TEXTURE_2D, 0, internalFormat, width, height, 0, format, GL_UNSIGNED_BYTE, data);

        glBindTexture(GL_TEXTURE_2D, 0);
    }

    /**
     * 为 FrameBuffer 创建一个空的 RGBA8 纹理
     */
    public GLTexture(int width, int height) {
        super(width, height, "Empty_FrameBuffer_Texture");

        this.rendererID = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, rendererID);

        // 设置过滤方式，这对 FBO 纹理至关重要
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

        // 核心：上传一个 null 数据的纹理，告诉 OpenGL 分配显存但不填充内容
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);

        glBindTexture(GL_TEXTURE_2D, 0);
    }

    /**
     * 实现基类的抽象方法：负责真正的 GPU 上传逻辑
     */
    @Override
    public void load(AssetResource resource) throws IOException {
        if (rendererID != 0) {
            glDeleteTextures(rendererID);
        }

        try (resource; MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer imageBuffer = readStreamToDirectBuffer(resource.getInputStream());

            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            IntBuffer channels = stack.mallocInt(1);

            // 强行解码为 4 通道 (RGBA)
            ByteBuffer data = STBImage.stbi_load_from_memory(imageBuffer, w, h, channels, 4);
            if (data == null) {
                throw new IOException("Failed to decode image: " + STBImage.stbi_failure_reason());
            }

            this.width = w.get(0);
            this.height = h.get(0);

            this.rendererID = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, rendererID);

            // --- 核心修正：添加下面这一行 ---
            // 告诉 OpenGL 像素数据是紧密排列的，不进行 4 字节对齐
            glPixelStorei(GL_UNPACK_ALIGNMENT, 1);

            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);

            // 上传
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, data);
            glGenerateMipmap(GL_TEXTURE_2D);

            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);

            STBImage.stbi_image_free(data);
            InternalLogger.info(String.format("GLTexture Loaded: %s [%dx%d]", sourceName, width, height));
        }
    }

    // --- 辅助方法保持不变 ---

    private ByteBuffer readStreamToDirectBuffer(java.io.InputStream source) throws IOException {
        ReadableByteChannel rbc = Channels.newChannel(source);
        ByteBuffer buffer = BufferUtils.createByteBuffer(1024 * 16);
        while (rbc.read(buffer) != -1) {
            if (!buffer.hasRemaining()) {
                ByteBuffer newBuffer = BufferUtils.createByteBuffer(buffer.capacity() * 2);
                buffer.flip();
                newBuffer.put(buffer);
                buffer = newBuffer;
            }
        }
        buffer.flip();
        return buffer;
    }

    @Override
    public void bind(int slot) {
        glActiveTexture(GL_TEXTURE0 + slot);
        glBindTexture(GL_TEXTURE_2D, rendererID);
    }

    @Override
    public void unbind() {
        glBindTexture(GL_TEXTURE_2D, 0);
    }

    @Override
    public void dispose() {
        if (rendererID != 0) {
            glDeleteTextures(rendererID);
            rendererID = 0;
        }
    }

    @Override
    public int getId() {
        return rendererID;
    }
}