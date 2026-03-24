package com.zenith.logic.state;

import com.zenith.logic.scene.entity.Entity;

/**
 * AI 状态基类
 */
public abstract class AIState {

    protected Entity owner;

    public void setOwner(Entity owner) {
        this.owner = owner;
    }

    /** 进入状态时调用一次 */
    public abstract void onEnter();

    /** 状态激活期间每帧调用 */
    public abstract void onUpdate(float deltaTime);

    /** 退出状态时调用一次 */
    public abstract void onExit();
}