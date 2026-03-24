package com.zenith.ui.skin;

import com.zenith.common.math.Color;

/**
 * 全局 UI 皮肤配置
 */
public class UISkin {

    // 调色板
    public Color mainBackground = new Color(0.1f, 0.1f, 0.11f, 1.0f);
    public Color componentBase = new Color(0.2f, 0.2f, 0.22f, 1.0f);
    public Color accent = new Color(0.3f, 0.5f, 0.9f, 1.0f);
    public Color textPrimary = new Color(0.95f, 0.95f, 0.95f, 1.0f);

    // 间距与样式常量
    public float defaultCornerRadius = 4.0f;
    public float defaultPadding = 8.0f;

    // 单例模式方便组件访问
    private static UISkin instance;
    public static UISkin getInstance() {
        if (instance == null) instance = new UISkin();
        return instance;
    }
}