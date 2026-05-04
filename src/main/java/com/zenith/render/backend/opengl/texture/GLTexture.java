package com.zenith.render.backend.opengl.texture;

import com.zenith.asset.AssetResource;
import com.zenith.common.utils.InternalLogger;
import com.zenith.render.Texture;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL42.glTexStorage2D;
import static org.lwjgl.opengl.GL45.*;

public class GLTexture extends Texture {
    private int rendererID;

    // 缓存配置
    private static final String CACHE_DIR = "TextureCache/";
    private static final int LARGE_TEXTURE_THRESHOLD = 2048;

    public GLTexture(AssetResource resource) throws IOException {
        super(0, 0, resource.getLocation().toString());
        this.load(resource);
    }

    /**
     * 原始内存数据构造函数 (如字体/图标)
     */
    public GLTexture(int width, int height, ByteBuffer data, int format) {
        super(width, height, "Raw_Memory_Buffer");
        this.width = width;
        this.height = height;
        uploadToGPU(data, format == GL_RED);
    }

    /**
     * 空纹理构造函数 (如 FrameBuffer 附件)
     */
    public GLTexture(int width, int height) {
        super(width, height, "Empty_FrameBuffer_Texture");
        this.width = width;
        this.height = height;
        uploadToGPU(null, false);
    }

    @Override
    public void load(AssetResource resource) throws IOException {
        if (rendererID != 0) {
            glDeleteTextures(rendererID);
        }

        // 生成基于路径的唯一缓存文件名
        String cacheKey = hashString(resource.getLocation().toString());
        File cacheFile = new File(CACHE_DIR + cacheKey + ".ztex");

        // 策略：如果缓存存在，直接读取二进制（极快）；否则解码并创建缓存
        if (cacheFile.exists()) {
            try {
                loadFromCache(cacheFile);
                InternalLogger.info("GLTexture [Cache Hit]: " + sourceName);
                return;
            } catch (Exception e) {
                InternalLogger.warn("Cache corrupted, re-decoding: " + sourceName);
            }
        }

        loadAndSaveCache(resource, cacheFile);
    }

    /**
     * 核心上传逻辑：自动识别大图并选择现代优化路径
     */
    private void uploadToGPU(ByteBuffer data, boolean isSingleChannel) {
        boolean useDSA = GL.getCapabilities().OpenGL45;
        int internalFormat = isSingleChannel ? GL_R8 : GL_RGBA8;
        int format = isSingleChannel ? GL_RED : GL_RGBA;

        if (useDSA) {
            this.rendererID = glCreateTextures(GL_TEXTURE_2D);
            // 【强制改为 1 层，不使用 Mipmap 进行排查】
            int levels = 1;

            glTextureStorage2D(rendererID, levels, internalFormat, width, height);

            if (data != null) {
                glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
                glTextureSubImage2D(rendererID, 0, 0, 0, width, height, format, GL_UNSIGNED_BYTE, data);
            }

            // 【关键修复】确保采样器设置绝对匹配层级
            glTextureParameteri(rendererID, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTextureParameteri(rendererID, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTextureParameteri(rendererID, GL_TEXTURE_WRAP_S, GL_REPEAT);
            glTextureParameteri(rendererID, GL_TEXTURE_WRAP_T, GL_REPEAT);
        } else {
            // 传统路径同理
            this.rendererID = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, rendererID);
            glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
            glTexImage2D(GL_TEXTURE_2D, 0, internalFormat, width, height, 0, format, GL_UNSIGNED_BYTE, data);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glBindTexture(GL_TEXTURE_2D, 0);
        }
    }

    private void loadFromCache(File file) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r");
             FileChannel channel = raf.getChannel()) {

            this.width = raf.readInt();
            this.height = raf.readInt();

            long dataSize = channel.size() - 8;
            ByteBuffer data = BufferUtils.createByteBuffer((int) dataSize);
            channel.read(data);
            data.flip();

            uploadToGPU(data, false);
        }
    }

    private void loadAndSaveCache(AssetResource resource, File cacheFile) throws IOException {
        try (resource; MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer imageBuffer = readStreamToDirectBuffer(resource.getInputStream());
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            IntBuffer comp = stack.mallocInt(1);

            ByteBuffer data = STBImage.stbi_load_from_memory(imageBuffer, w, h, comp, 4);
            if (data == null) throw new IOException("STB Decode Failed: " + STBImage.stbi_failure_reason());

            this.width = w.get(0);
            this.height = h.get(0);

            // 上传到 GPU
            uploadToGPU(data, false);

            // 存入本地缓存
            saveToDisk(cacheFile, width, height, data);

            STBImage.stbi_image_free(data);
            InternalLogger.info("GLTexture [Decoded & Cached]: " + sourceName);
        }
    }

    private void saveToDisk(File file, int w, int h, ByteBuffer data) {
        try {
            Files.createDirectories(Paths.get(CACHE_DIR));
            try (RandomAccessFile raf = new RandomAccessFile(file, "rw");
                 FileChannel channel = raf.getChannel()) {
                raf.writeInt(w);
                raf.writeInt(h);
                data.rewind();
                channel.write(data);
                data.rewind(); // 保持 buffer 状态以供后续可能的用途
            }
        } catch (IOException e) {
            InternalLogger.error("Failed to write cache: " + e.getMessage());
        }
    }

    private ByteBuffer readStreamToDirectBuffer(InputStream source) throws IOException {
        ReadableByteChannel rbc = Channels.newChannel(source);
        ByteBuffer buffer = BufferUtils.createByteBuffer(1024 * 64);
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

    private String hashString(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(input.hashCode());
        }
    }

    @Override
    public void bind(int slot) {
        if (GL.getCapabilities().OpenGL45) {
            glBindTextureUnit(slot, rendererID);
        } else {
            glActiveTexture(GL_TEXTURE0 + slot);
            glBindTexture(GL_TEXTURE_2D, rendererID);
        }
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