package com.zenith.render.backend.opengl;

import com.zenith.asset.AssetResource;
import com.zenith.render.Font;
import com.zenith.render.Texture;
import com.zenith.render.backend.opengl.texture.GLTexture;
import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBTTFontinfo;
import org.lwjgl.stb.STBTruetype;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL33.GL_TEXTURE_SWIZZLE_RGBA;

public class GLFont extends Font {

    private final Map<Character, Glyph> glyphs = new HashMap<>();
    private GLTexture texture;
    private float scale;

    // 初始测试建议使用 1024，稳定后再改 2048
    private static final int ATLAS_SIZE = 1024;
    private static final int PADDING = 2; // 增加间距防止采样污染

    public GLFont(AssetResource resource, int fontSize) throws IOException {
        this(loadResourceToDirectBuffer(resource), resource.getLocation().getPath(), fontSize);
        resource.close();
    }

    public GLFont(String path, int fontSize) throws IOException {
        this(loadPathToDirectBuffer(path), path, fontSize);
    }

    private GLFont(ByteBuffer ttfData, String debugPath, int fontSize) throws IOException {
        super(debugPath, fontSize);

        try {
            STBTTFontinfo info = STBTTFontinfo.create();
            if (!STBTruetype.stbtt_InitFont(info, ttfData)) {
                throw new IOException("STB failed to init font: " + debugPath);
            }

            this.scale = STBTruetype.stbtt_ScaleForPixelHeight(info, fontSize);

            // 分配并手动清零
            ByteBuffer atlasBitmap = MemoryUtil.memAlloc(ATLAS_SIZE * ATLAS_SIZE);
            MemoryUtil.memSet(atlasBitmap, 0);

            try {
                bakeAtlas(info, atlasBitmap);
                setupTexture(atlasBitmap);
            } finally {
                MemoryUtil.memFree(atlasBitmap);
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new IOException("Critical error during font loading: " + e.getMessage());
        }
    }

    private void bakeAtlas(STBTTFontinfo info, ByteBuffer atlasBitmap) {
        int curX = PADDING;
        int curY = PADDING;
        int maxRowHeight = 0;

        // 【重改】大幅缩减初始范围，先确保能跑通
        String charset = generateTestCharset();

        for (int i = 0; i < charset.length(); i++) {
            char c = charset.charAt(i);

            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer w = stack.mallocInt(1);
                IntBuffer h = stack.mallocInt(1);
                IntBuffer ox = stack.mallocInt(1);
                IntBuffer oy = stack.mallocInt(1);
                IntBuffer adv = stack.mallocInt(1);

                // 获取位图
                ByteBuffer charPixels = STBTruetype.stbtt_GetCodepointBitmap(info, scale, scale, c, w, h, ox, oy);
                STBTruetype.stbtt_GetCodepointHMetrics(info, c, adv, null);

                int charW = w.get(0);
                int charH = h.get(0);

                // 换行逻辑
                if (curX + charW + PADDING >= ATLAS_SIZE) {
                    curX = PADDING;
                    curY += maxRowHeight + PADDING;
                    maxRowHeight = 0;
                }

                // 溢出检查
                if (curY + charH + PADDING >= ATLAS_SIZE) {
                    break;
                }

                if (charPixels != null) {
                    // 安全拷贝：逐行 put，并在每行结束后重置 position
                    for (int row = 0; row < charH; row++) {
                        int atlasPos = (curY + row) * ATLAS_SIZE + curX;
                        atlasBitmap.position(atlasPos);

                        for (int col = 0; col < charW; col++) {
                            atlasBitmap.put(charPixels.get(row * charW + col));
                        }
                    }
                    STBTruetype.stbtt_FreeBitmap(charPixels);
                }

                Glyph glyph = new Glyph();
                glyph.width = charW;
                glyph.height = charH;
                glyph.offsetX = ox.get(0);
                glyph.offsetY = oy.get(0);
                glyph.advance = (int) (adv.get(0) * scale);
                glyph.u = curX / (float) ATLAS_SIZE;
                glyph.v = curY / (float) ATLAS_SIZE;
                glyph.u2 = (curX + charW) / (float) ATLAS_SIZE;
                glyph.v2 = (curY + charH) / (float) ATLAS_SIZE;

                glyphs.put(c, glyph);

                curX += charW + PADDING;
                maxRowHeight = Math.max(maxRowHeight, charH);
            }
        }
        atlasBitmap.rewind();
    }

    private String generateTestCharset() {
        StringBuilder sb = new StringBuilder();
        // 仅 ASCII + 常用标点 + 少量中文
        for (char c = 32; c < 127; c++) sb.append(c);
        sb.append("，。！？ZenithEngine渲染测试提示");

        // 只加 500 个常用汉字测试稳定性
        for (int i = 0x4E00; i <= 0x4FFF; i++) {
            sb.append((char) i);
        }
        return sb.toString();
    }

    private void setupTexture(ByteBuffer atlasBitmap) {
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
        this.texture = new GLTexture(ATLAS_SIZE, ATLAS_SIZE, atlasBitmap, GL_RED);

        this.texture.bind(0);
        int[] swizzle = {GL_RED, GL_RED, GL_RED, GL_RED};
        glTexParameteriv(GL_TEXTURE_2D, GL_TEXTURE_SWIZZLE_RGBA, swizzle);

        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    }

    // 辅助加载方法（保持不变）
    private static ByteBuffer loadResourceToDirectBuffer(AssetResource resource) throws IOException {
        try (InputStream is = resource.getInputStream();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[32768];
            int read;
            while ((read = is.read(buffer)) != -1) baos.write(buffer, 0, read);
            byte[] data = baos.toByteArray();
            ByteBuffer direct = MemoryUtil.memAlloc(data.length);
            direct.put(data).flip();
            return direct;
        }
    }

    private static ByteBuffer loadPathToDirectBuffer(String path) throws IOException {
        byte[] data = Files.readAllBytes(Paths.get(path));
        ByteBuffer direct = MemoryUtil.memAlloc(data.length);
        direct.put(data).flip();
        return direct;
    }

    @Override public Texture getTexture() { return texture; }
    @Override public Glyph getGlyph(char c) { return glyphs.getOrDefault(c, glyphs.get(' ')); }
    @Override public float getWidth(String text) {
        float w = 0;
        if(text == null) return 0;
        for (char c : text.toCharArray()) {
            Glyph g = getGlyph(c);
            if (g != null) w += g.advance;
        }
        return w;
    }
    @Override public void dispose() { if (texture != null) texture.dispose(); }
}