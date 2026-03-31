package com.zenith.render.backend.opengl;

import com.zenith.asset.AssetResource;
import com.zenith.render.Font;
import com.zenith.render.Texture;
import com.zenith.render.backend.opengl.texture.GLTexture;
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
    private final ByteBuffer ttfData;
    private final String debugPath;

    private static final int ATLAS_SIZE = 2048;
    private static final int PADDING = 2;
    private final Glyph tempGlyph = new Glyph();
    public GLFont(AssetResource resource, int fontSize) throws IOException {
        this(loadResourceToDirectBuffer(resource), resource.getLocation().getPath(), fontSize);
        resource.close();
    }

    private GLFont(ByteBuffer ttfData, String debugPath, int fontSize, Map<Character, Glyph> glyphs, GLTexture texture) {
        super(debugPath, fontSize);
        this.ttfData = ttfData; // 注意：如果是堆外内存，需考虑引用计数，否则 dispose 会出问题
        this.debugPath = debugPath;
        this.glyphs.putAll(glyphs);
        this.texture = texture;
    }

    public GLFont(String path, int fontSize) throws IOException {
        this(loadPathToDirectBuffer(path), path, fontSize);
    }

    @Override
    public Font copy() {
        GLFont newFont = new GLFont(this.ttfData, this.fontName, this.fontSize, this.glyphs, this.texture);
        this.copyStateTo(newFont);
        return newFont;
    }

    private GLFont(ByteBuffer ttfData, String debugPath, int fontSize) throws IOException {
        super(debugPath, fontSize);
        this.ttfData = ttfData;
        this.debugPath = debugPath;

        // 执行初始构建
        rebuild(fontSize);
    }

    /**
     * 实现字号动态修改
     * 注意：由于涉及位图重绘和纹理上传，不建议在每帧执行
     */
    @Override
    public void setFontSize(int fontSize) {
        if (this.fontSize == fontSize) return;
        try {
            this.fontSize = fontSize;
            rebuild(fontSize);
        } catch (IOException e) {
            System.err.println("Failed to rebuild font atlas for size: " + fontSize);
            e.printStackTrace();
        }
    }

    private void rebuild(int fontSize) throws IOException {
        // 1. 清理旧纹理和旧数据
        if (this.texture != null) {
            this.texture.dispose();
        }
        glyphs.clear();

        // 2. 初始化 STB 字体信息
        STBTTFontinfo info = STBTTFontinfo.create();
        if (!STBTruetype.stbtt_InitFont(info, ttfData)) {
            throw new IOException("STB failed to init font during rebuild: " + debugPath);
        }

        // 3. 计算新缩放
        this.scale = STBTruetype.stbtt_ScaleForPixelHeight(info, fontSize);

        // 4. 分配并清空临时图集内存
        ByteBuffer atlasBitmap = MemoryUtil.memAlloc(ATLAS_SIZE * ATLAS_SIZE);
        MemoryUtil.memSet(atlasBitmap, 0);

        try {
            bakeAtlas(info, atlasBitmap);
            setupTexture(atlasBitmap);
        } finally {
            MemoryUtil.memFree(atlasBitmap);
        }
    }

    private void bakeAtlas(STBTTFontinfo info, ByteBuffer atlasBitmap) {
        int curX = PADDING;
        int curY = PADDING;
        int maxRowHeight = 0;

        String charset = generateTestCharset();

        for (int i = 0; i < charset.length(); i++) {
            char c = charset.charAt(i);

            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer w = stack.mallocInt(1);
                IntBuffer h = stack.mallocInt(1);
                IntBuffer ox = stack.mallocInt(1);
                IntBuffer oy = stack.mallocInt(1);
                IntBuffer adv = stack.mallocInt(1);

                ByteBuffer charPixels = STBTruetype.stbtt_GetCodepointBitmap(info, scale, scale, c, w, h, ox, oy);
                STBTruetype.stbtt_GetCodepointHMetrics(info, c, adv, null);

                int charW = w.get(0);
                int charH = h.get(0);

                if (curX + charW + PADDING >= ATLAS_SIZE) {
                    curX = PADDING;
                    curY += maxRowHeight + PADDING;
                    maxRowHeight = 0;
                }

                if (curY + charH + PADDING >= ATLAS_SIZE) {
                    break;
                }

                if (charPixels != null) {
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
        for (char c = 32; c < 127; c++) sb.append(c);
        sb.append("，。！？ZenithEngine渲染测试提示");
        // 常用中文字符范围
        for (int i = 0x4E00; i <= 0x4FFF; i++) {
            sb.append((char) i);
        }
        return sb.toString();
    }

    private void setupTexture(ByteBuffer atlasBitmap) {
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
        this.texture = new GLTexture(ATLAS_SIZE, ATLAS_SIZE, atlasBitmap, GL_RED);

        this.texture.bind(0);
        // 使用 Swizzle 将单通道 R 映射到 RGBA，让文字通过 Alpha 通道显示
        int[] swizzle = {GL_ONE, GL_ONE, GL_ONE, GL_RED};
        glTexParameteriv(GL_TEXTURE_2D, GL_TEXTURE_SWIZZLE_RGBA, swizzle);

        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    }

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
    @Override
    public Glyph getGlyph(char c) {
        Glyph base = glyphs.getOrDefault(c, glyphs.get(' '));
        if (base == null) return null;

        // 将原始数据拷贝到临时对象并应用当前 Font 类的 scaleX/scaleY
        tempGlyph.u = base.u;
        tempGlyph.v = base.v;
        tempGlyph.u2 = base.u2;
        tempGlyph.v2 = base.v2;

        // 应用动态缩放
        tempGlyph.width = base.width * this.scaleX;
        tempGlyph.height = base.height * this.scaleY;
        tempGlyph.offsetX = base.offsetX * this.scaleX;
        tempGlyph.offsetY = base.offsetY * this.scaleY;
        tempGlyph.advance = base.advance * this.scaleX;

        return tempGlyph;
    }

    @Override
    public float getWidth(String text) {
        float totalWidth = 0;
        if (text == null) return 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            Glyph g = glyphs.get(c);
            if (g == null) g = glyphs.get(' ');

            if (g != null) {
                // 这里直接使用原始 advance 乘以当前缩放
                totalWidth += g.advance * this.scaleX;
            }
        }
        return totalWidth;
    }

    @Override
    public void dispose() {
        if (texture != null) texture.dispose();
        if (ttfData != null) MemoryUtil.memFree(ttfData); // 必须释放堆外内存
    }
}