package com.zenith.ui.component;

import com.zenith.asset.AssetResource;
import com.zenith.common.math.Color;
import com.zenith.common.math.Rectf;
import com.zenith.common.utils.InternalLogger;
import com.zenith.render.Texture;
import com.zenith.render.backend.opengl.texture.GLTexture;
import com.zenith.ui.render.UIRenderContext;

import java.io.IOException;

/**
 * 专门用于渲染图片的 UI 组件。
 * 深度集成 ZenithEngine 的 AssetResource 系统与 GLTexture 渲染后端。
 */
public class UIImageComponent extends UIComponent {

    private Texture texture;
    private Color tint = Color.WHITE;
    private float u = 0, v = 0, uw = 1, uh = 1;

    /**
     * 构造函数 A：使用现有的纹理实例。
     * 推荐用于 UI 图集 (Atlas) 或频繁复用的图标。
     */
    public UIImageComponent(float x, float y, float width, float height, Texture texture) {
        super(x, y, width, height);
        this.texture = texture;
    }

    /**
     * 构造函数 B：直接通过 AssetResource 异步或同步加载。
     * 内部直接调用 GLTexture 的 Resource 构造函数，利用 STBImage 进行解码。
     */
    public UIImageComponent(float x, float y, float width, float height, AssetResource resource) {
        super(x, y, width, height);
        try {
            this.texture = new GLTexture(resource);
        } catch (IOException e) {
            InternalLogger.error("UIImageComponent 无法加载纹理资源: " + e.getMessage());
        }
    }

    @Override
    public void onRender(UIRenderContext ctx) {
        if (texture == null) return;

        // 绑定纹理并提交绘制
        ctx.bindTexture(texture);

        Rectf bounds = getBounds();
        ctx.drawTextureRect(
                bounds.x, bounds.y,
                bounds.width, bounds.height,
                u, v, uw, uh,
                tint
        );
    }

    /**
     * 设置采样区域。如果你的图片是在一个大图集里，用这个设置 UV。
     */
    public void setUV(float u, float v, float uw, float uh) {
        this.u = u;
        this.v = v;
        this.uw = uw;
        this.uh = uh;
    }

    /**
     * 设置颜色滤镜，Color.a 可以控制透明度。
     */
    public void setTint(Color tint) {
        this.tint = tint;
    }

    public Texture getTexture() {
        return texture;
    }

    public void setTexture(Texture texture) {
        this.texture = texture;
    }
}