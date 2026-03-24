package com.zenith.render.backend.opengl.texture;

import com.zenith.render.Font;
import com.zenith.render.Texture;
import com.zenith.render.backend.opengl.texture.GLTexture;
import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBTTFontinfo;
import org.lwjgl.stb.STBTruetype;
import org.lwjgl.system.MemoryStack;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL11.*;

public class GLFont extends Font {

    private final Map<Character, Glyph> glyphs = new HashMap<>();
    private GLTexture texture;
    private final float scale;
    private int ascent, descent, lineGap;

    public GLFont(String path, int fontSize) throws IOException {
        super(path, fontSize);

        // 1. 读取 TTF 文件到 ByteBuffer
        byte[] bytes = Files.readAllBytes(Paths.get(path));
        ByteBuffer ttfData = BufferUtils.createByteBuffer(bytes.length);
        ttfData.put(bytes).flip();

        // 2. 初始化 STB 字体信息
        STBTTFontinfo info = STBTTFontinfo.create();
        if (!STBTruetype.stbtt_InitFont(info, ttfData)) {
            throw new IOException("Failed to initialize STBTrueType font info.");
        }

        // 3. 计算缩放和度量信息
        this.scale = STBTruetype.stbtt_ScaleForPixelHeight(info, fontSize);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer pAscent = stack.mallocInt(1);
            IntBuffer pDescent = stack.mallocInt(1);
            IntBuffer pLineGap = stack.mallocInt(1);
            STBTruetype.stbtt_GetFontVMetrics(info, pAscent, pDescent, pLineGap);
            this.ascent = pAscent.get(0);
            this.descent = pDescent.get(0);
            this.lineGap = pLineGap.get(0);
        }

        // 4. 创建纹理图集 (Atlas)
        // 注意：阿里妈妈大楷比较宽，建议使用 1024x1024 甚至更大，或者只加载常用字符
        int atlasWidth = 1024;
        int atlasHeight = 1024;
        ByteBuffer bitmap = BufferUtils.createByteBuffer(atlasWidth * atlasHeight);

        // 预定义要渲染的字符范围（常用中文+英文）
        String charset = " !\"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~"
                + "ZenithEngine测试阿里妈妈东方大楷渲染";

        int x = 1;
        int y = 1;
        int rowHeight = 0;

        for (char c : charset.toCharArray()) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer w = stack.mallocInt(1);
                IntBuffer h = stack.mallocInt(1);
                IntBuffer ox = stack.mallocInt(1);
                IntBuffer oy = stack.mallocInt(1);

                // 获取字符位图
                ByteBuffer charPixels = STBTruetype.stbtt_GetCodepointBitmap(info, scale, scale, c, w, h, ox, oy);

                // 换行逻辑
                if (x + w.get(0) >= atlasWidth) {
                    x = 1;
                    y += rowHeight + 1;
                    rowHeight = 0;
                }

                // 写入大位图
                if (charPixels != null) {
                    for (int i = 0; i < h.get(0); i++) {
                        for (int j = 0; j < w.get(0); j++) {
                            bitmap.put((y + i) * atlasWidth + (x + j), charPixels.get(i * w.get(0) + j));
                        }
                    }
                }

                // 计算水平间距
                IntBuffer adv = stack.mallocInt(1);
                STBTruetype.stbtt_GetCodepointHMetrics(info, c, adv, null);

                // 保存 Glyph 信息
                Glyph glyph = new Glyph();
                glyph.width = w.get(0);
                glyph.height = h.get(0);
                glyph.offsetX = ox.get(0);
                glyph.offsetY = oy.get(0);
                glyph.advance = (int) (adv.get(0) * scale);
                // 归一化纹理坐标
                glyph.u = x / (float) atlasWidth;
                glyph.v = y / (float) atlasHeight;
                glyph.u2 = (x + glyph.width) / (float) atlasWidth;
                glyph.v2 = (y + glyph.height) / (float) atlasHeight;

                glyphs.put(c, glyph);

                x += w.get(0) + 1;
                rowHeight = Math.max(rowHeight, h.get(0));
            }
        }

        // 5. 生成 OpenGL 纹理
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1); // 必须！因为位图是 1 字节（GL_RED）对齐的
        this.texture = new GLTexture(atlasWidth, atlasHeight, bitmap, GL_RED);
        // 设置过滤参数，防止文字模糊
        this.texture.bind(0);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    }

    @Override
    public Texture getTexture() {
        return texture;
    }

    @Override
    public Glyph getGlyph(char c) {
        // 如果字符不在图集中，返回空格或默认字符
        return glyphs.getOrDefault(c, glyphs.get(' '));
    }

    @Override
    public float getWidth(String text) {
        float width = 0;
        for (char c : text.toCharArray()) {
            Glyph g = getGlyph(c);
            if (g != null) width += g.advance;
        }
        return width;
    }

    @Override
    public void dispose() {
        if (texture != null) texture.dispose();
    }
}