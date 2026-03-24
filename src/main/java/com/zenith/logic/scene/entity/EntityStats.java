package com.zenith.logic.scene.entity;

import com.zenith.common.utils.InternalLogger;

/**
 * 存储实体的核心数值属性与战斗公式
 */
public class EntityStats {
    // 基础属性
    private float health;
    private float maxHealth;
    private float mana;
    private float maxMana;

    // 战斗属性
    private float attack;      // 攻击力
    private float defense;     // 防御力
    private float moveSpeed;   // 移动速度

    // 高阶属性 (0.0 - 1.0)
    private float critRate;    // 暴击率
    private float critDamage;  // 暴击伤害倍率 (默认 1.5)
    private float dodgeRate;   // 闪避率

    // 等级系统
    private int level;
    private float experience;
    private float nextLevelExp;

    public EntityStats(float baseMaxHealth, float baseAttack, float baseDefense) {
        this.level = 1;
        this.experience = 0;
        this.nextLevelExp = 100;

        this.maxHealth = baseMaxHealth;
        this.health = baseMaxHealth;
        this.attack = baseAttack;
        this.defense = baseDefense;

        this.maxMana = 50.0f;
        this.mana = 50.0f;
        this.moveSpeed = 1.0f;
        this.critRate = 0.05f;
        this.critDamage = 1.5f;
        this.dodgeRate = 0.02f;
    }

    /**
     * 核心受击算法：计算减免后的实际伤害
     * 公式: 实际伤害 = 攻击力 * (100 / (100 + 防御力))
     * 这是一种经典的非线性减伤公式，防御越高收益越平滑，不会出现减伤 100% 的情况。
     */
    public float calculateDamage(float rawDamage) {
        if (Math.random() < dodgeRate) {
            InternalLogger.debug("攻击被闪避！");
            return 0;
        }
        float reductionMultiplier = 100f / (100f + this.defense);
        float finalDamage = rawDamage * reductionMultiplier;
        return Math.max(1.0f, finalDamage);
    }

    /**
     * 尝试对目标造成伤害并返回实际伤害数值
     */
    public float takeDamage(float rawDamage) {
        float actualDamage = calculateDamage(rawDamage);
        float oldHealth = this.health;
        this.health = Math.max(0, health - actualDamage);
        return oldHealth - this.health;
    }

    /**
     * 增加经验值并检查升级
     */
    public void addExperience(float amount) {
        this.experience += amount;
        while (this.experience >= nextLevelExp) {
            levelUp();
        }
    }

    private void levelUp() {
        this.experience -= nextLevelExp;
        this.level++;

        // next = current * 1.2 + 50
        this.nextLevelExp = (float) (nextLevelExp * 1.2 + 50);

        float healthBonus = maxHealth * 0.1f;
        this.maxHealth += healthBonus;
        this.health += healthBonus;

        this.attack *= 1.08f;
        this.defense *= 1.05f;

        InternalLogger.info("等级提升！当前等级: " + level + " | 下一级所需经验: " + nextLevelExp);
    }

    /* -------------------------------------------------------------------------- */
    /* 工具方法                                                                    */
    /* -------------------------------------------------------------------------- */

    public void heal(float amount) { this.health = Math.min(maxHealth, health + amount); }
    public boolean isDead() { return health <= 0; }

    /* Getters (略) */
    public float getHealth() { return health; }
    public float getMaxHealth() { return maxHealth; }
    public float getAttack() { return attack; }
    public float getDefense() { return defense; }
    public int getLevel() { return level; }
}