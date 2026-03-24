package com.zenith.ui.component;

import com.zenith.common.math.Color;
import com.zenith.common.math.Rectf;
import com.zenith.render.backend.opengl.GLLight;
import com.zenith.render.backend.opengl.GLTextRenderer;
import com.zenith.render.backend.opengl.shader.GLShader;
import com.zenith.render.backend.opengl.shader.GLShaderRegistry;
import com.zenith.render.backend.opengl.shader.StandardShader;
import com.zenith.render.backend.opengl.shader.UITextShader;
import com.zenith.ui.event.UIButtonListener;
import com.zenith.ui.render.UIRenderContext;
import com.zenith.render.backend.opengl.texture.GLTexture;

// 引入文字渲染与着色器相关类 (请根据实际项目结构调整包名)
import com.zenith.render.Font;
import com.zenith.render.TextRenderer;
import com.zenith.render.backend.opengl.GLMaterial;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL11C.*;

public class UIButton extends UIComponent {

    /** 碰撞检测模式 */
    public enum CollisionMode {
        RECTANGLE,      // 纯矩形
        CIRCLE,         // 纯圆形
        ROUNDED_RECT    // 圆角矩形（更精确的点击判定）
    }

    private final GLTexture texture;
    private final List<UIButtonListener> listeners = new ArrayList<>();

    // --- 样式与交互参数 ---
    private CollisionMode collisionMode = CollisionMode.RECTANGLE;
    private float cornerRadius = 0.0f;        // 圆角半径
    private float borderWidth = 0.0f;         // 边框宽度
    private Color borderColor = Color.BLACK;  // 边框颜色
    private float pressedScale = 0.95f;       // 按下时的缩放比例
    private boolean enableScaleEffect = true; // 是否开启缩放反馈

    private Color normalColor = new Color(1, 1, 1, 1);
    private Color hoverColor = new Color(0.8f, 0.8f, 0.8f, 1.0f);
    private Color pressedColor = new Color(0.6f, 0.6f, 0.6f, 1.0f);

    // --- 状态变量 ---
    private boolean isHovered = false;
    private boolean isPressed = false;

    // 记录鼠标最新位置用于渲染 Tooltip
    private float mouseX = 0.0f;
    private float mouseY = 0.0f;

    // --- Tooltip 逻辑 ---
    private String tooltipText = null;
    private float hoverTimer = 0.0f;
    private boolean tooltipTriggered = false;

    public UIButton(float x, float y, float width, float height, GLTexture texture) {
        super(x, y, width, height);
        this.texture = texture;
    }

    /* -------------------------------------------------------------------------- */
    /* 链式设置方法                                                                */
    /* -------------------------------------------------------------------------- */

    public UIButton setCollisionMode(CollisionMode mode) {
        this.collisionMode = mode;
        return this;
    }

    public UIButton setCornerRadius(float radius) {
        this.cornerRadius = radius;
        if (radius > 0 && collisionMode == CollisionMode.RECTANGLE) {
            this.collisionMode = CollisionMode.ROUNDED_RECT;
        }
        return this;
    }

    public UIButton setBorder(float width, Color color) {
        this.borderWidth = width;
        this.borderColor = color;
        return this;
    }

    public UIButton setPressedScale(float scale) {
        this.pressedScale = scale;
        return this;
    }

    public UIButton setEnableScaleEffect(boolean enable) {
        this.enableScaleEffect = enable;
        return this;
    }

    public UIButton setTooltip(String text) {
        this.tooltipText = text;
        return this;
    }

    public UIButton setColors(Color normal, Color hover, Color pressed) {
        this.normalColor = normal;
        this.hoverColor = hover;
        this.pressedColor = pressed;
        return this;
    }

    /* -------------------------------------------------------------------------- */
    /* 核心逻辑实现                                                                */
    /* -------------------------------------------------------------------------- */

    @Override
    public void update(float deltaTime) {
        super.update(deltaTime);

        // Tooltip 计时逻辑：如果鼠标悬停在按钮上，开始计时
        if (isHovered && tooltipText != null && !tooltipText.isEmpty()) {
            hoverTimer += deltaTime;
            if (hoverTimer >= 0.5f) { // 悬停超过 0.5 秒
                tooltipTriggered = true;
            }
        } else {
            hoverTimer = 0.0f;
            tooltipTriggered = false;
        }
    }

    /** 判定点是否在按钮有效形状内 */
    private boolean isInside(float mx, float my) {
        if (!bounds.contains(mx, my)) return false;
        if (collisionMode == CollisionMode.RECTANGLE) return true;

        float centerX = bounds.x + bounds.width / 2.0f;
        float centerY = bounds.y + bounds.height / 2.0f;

        if (collisionMode == CollisionMode.CIRCLE) {
            float radius = Math.min(bounds.width, bounds.height) / 2.0f;
            float dx = mx - centerX;
            float dy = my - centerY;
            return (dx * dx + dy * dy) <= (radius * radius);
        }

        if (collisionMode == CollisionMode.ROUNDED_RECT) {
            float dx = Math.abs(mx - centerX) - (bounds.width / 2.0f - cornerRadius);
            float dy = Math.abs(my - centerY) - (bounds.height / 2.0f - cornerRadius);
            if (dx > 0 && dy > 0) {
                return (dx * dx + dy * dy) <= (cornerRadius * cornerRadius);
            }
            return true;
        }
        return false;
    }

    @Override
    protected void onRender(UIRenderContext ctx) {
        Color drawColor = isPressed ? pressedColor : (isHovered ? hoverColor : normalColor);
        float currentScale = (isPressed && enableScaleEffect) ? pressedScale : 1.0f;
        ctx.pushTransform();
        float cx = bounds.x + bounds.width / 2f;
        float cy = bounds.y + bounds.height / 2f;
        ctx.translate(cx, cy);
        if (currentScale != 1.0f) {
            ctx.scale(currentScale, currentScale);
        }
        ctx.translate(-bounds.width / 2f, -bounds.height / 2f);
        if (texture != null) {
            ctx.getBufferBuilder().withTexture(texture);
        }
        ctx.drawRect(new Rectf(0, 0, bounds.width, bounds.height), drawColor);
        ctx.popTransform();
        if (tooltipTriggered && tooltipText != null && !tooltipText.isEmpty()) {
            ctx.drawTooltip(tooltipText, mouseX, mouseY);
        }
    }

    /* -------------------------------------------------------------------------- */
    /* 事件分发                                                                    */
    /* -------------------------------------------------------------------------- */

    public boolean onMouseMove(float mx, float my) {
        // 更新记录鼠标位置用于提示框渲染
        this.mouseX = mx;
        this.mouseY = my;

        boolean nowHovered = isInside(mx, my);
        if (nowHovered && !isHovered) {
            isHovered = true;
            listeners.forEach(l -> l.onHoverEnter(this));
        } else if (!nowHovered && isHovered) {
            isHovered = false;
            isPressed = false;
            listeners.forEach(l -> l.onHoverExit(this));
        }

        return nowHovered;
    }

    public boolean onMouseButton(int action, float mx, float my) {
        boolean inside = isInside(mx, my);
        boolean consumed = false;

        if (action == 1 && inside) {
            isPressed = true;
            listeners.forEach(l -> l.onPress(this));
            consumed = true;
        } else if (action == 0) {
            if (isPressed && inside) {
                listeners.forEach(l -> l.onClick(this));
                consumed = true;
            }
            if (isPressed) {
                isPressed = false;
                listeners.forEach(l -> l.onRelease(this));
                consumed = true;
            }
        }
        return consumed;
    }

    public void addListener(UIButtonListener listener) {
        this.listeners.add(listener);
    }

    public void setOnClick(Runnable action) {
        addListener(new UIButtonListener() {
            @Override public void onClick(UIButton b) { action.run(); }
            @Override public void onHoverEnter(UIButton b) {}
            @Override public void onHoverExit(UIButton b) {}
            @Override public void onPress(UIButton b) {}
            @Override public void onRelease(UIButton b) {}
        });
    }

    public String getTooltipText() { return tooltipText; }
    public boolean isTooltipVisible() { return tooltipTriggered; }
}