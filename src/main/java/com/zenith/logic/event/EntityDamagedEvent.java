package com.zenith.logic.event;

import com.zenith.logic.scene.entity.Entity;

/**
 * 当实体受到伤害时触发的事件
 */
public class EntityDamagedEvent extends Event {

    private final Entity victim;   // 受害者
    private final Entity attacker; // 攻击者 (可能为 null)
    private float amount;          // 实际伤害数值
    private boolean canceled = false;

    public EntityDamagedEvent(Entity victim, Entity attacker, float amount) {
        this.victim = victim;
        this.attacker = attacker;
        this.amount = amount;
    }

    @Override
    public boolean isCancelable() {
        return true;
    }

    public void setCanceled(boolean canceled) {
        this.canceled = canceled;
    }

    public boolean isCanceled() {
        return canceled;
    }

    public Entity getVictim() { return victim; }
    public Entity getAttacker() { return attacker; }
    public float getAmount() { return amount; }
    public void setAmount(float amount) { this.amount = amount; }
}