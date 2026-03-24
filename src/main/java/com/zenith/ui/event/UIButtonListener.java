package com.zenith.ui.event;

import com.zenith.ui.component.UIButton;

public interface UIButtonListener {
    void onHoverEnter(UIButton button);
    void onHoverExit(UIButton button);
    void onPress(UIButton button);
    void onRelease(UIButton button);
    void onClick(UIButton button); // 只有在按钮范围内按下并松开才触发
}