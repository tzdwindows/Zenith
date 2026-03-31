package com.zenith.ui.render;

import com.zenith.asset.AssetResource;
import org.w3c.dom.*;
import javax.xml.parsers.*;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class TextureAtlas {
    private final Map<String, SpriteRegion> sprites = new HashMap<>();
    private final com.zenith.render.Texture texture;
    private final int width;
    private final int height;

    public static class SpriteRegion {
        public float x, y, w, h;
        public float u, v, uw, uh;
    }

    /**
     * 修改后的构造函数
     * @param texture 纹理实例
     * @param xmlResource 使用 AssetResource 加载的 XML 资源
     */
    public TextureAtlas(com.zenith.render.Texture texture, AssetResource xmlResource) {
        this.texture = texture;
        this.width = texture.getWidth();
        this.height = texture.getHeight();

        // 从资源实例中加载 XML
        loadFromResource(xmlResource);
    }

    private void loadFromResource(AssetResource resource) {
        // 使用 try-with-resources 确保流被关闭（AssetResource 实现了 AutoCloseable）
        try (InputStream is = resource.getInputStream()) {
            if (is == null) {
                throw new Exception("无法获取资源输入流: " + resource.getLocation().getPath());
            }

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(is); // 直接解析 InputStream

            NodeList nList = doc.getElementsByTagName("SubTexture");

            for (int i = 0; i < nList.getLength(); i++) {
                Element e = (Element) nList.item(i);
                SpriteRegion region = new SpriteRegion();

                region.x = Float.parseFloat(e.getAttribute("x"));
                region.y = Float.parseFloat(e.getAttribute("y"));
                region.w = Float.parseFloat(e.getAttribute("width"));
                region.h = Float.parseFloat(e.getAttribute("height"));

                // 计算 UV 坐标
                region.u = region.x / width;
                region.v = region.y / height;
                region.uw = region.w / width;
                region.uh = region.h / height;

                String name = e.getAttribute("name");
                sprites.put(name, region);
            }
        } catch (Exception e) {
            System.err.println("解析 TextureAtlas XML 失败: " + resource.getLocation().getPath());
            e.printStackTrace();
        }
    }

    public SpriteRegion getRegion(String name) {
        return sprites.get(name);
    }

    public com.zenith.render.Texture getTexture() {
        return texture;
    }
}