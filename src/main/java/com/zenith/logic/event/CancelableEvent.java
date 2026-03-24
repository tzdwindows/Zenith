package com.zenith.logic.event;

/**
 * 支持取消机制的事件基类
 */
public abstract class CancelableEvent extends Event {

    private boolean canceled = false;

    @Override
    public boolean isCancelable() {
        return true;
    }

    public boolean isCanceled() {
        return canceled;
    }

    /**
     * 设置事件的取消状态
     * @param cancel true 表示拦截该事件，后续监听器将无法通过常规逻辑处理
     */
    public void setCanceled(boolean cancel) {
        this.canceled = cancel;
    }
}