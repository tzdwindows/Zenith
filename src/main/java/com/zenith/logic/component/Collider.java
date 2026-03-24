package com.zenith.logic.component;

import com.zenith.common.math.AABB;
import org.joml.Vector3f;

/**
 * 所有碰撞箱的基类，支持受击部位判定
 */
public abstract class Collider extends Component {

    /**
     * 定义碰撞部位及其默认伤害倍率
     */
    public enum HitPart {
        BODY(1.0f),    // 身体：正常伤害
        HEAD(2.5f),    // 头部：高额伤害
        LIMB(0.7f),    // 四肢：减免伤害
        CRITICAL(3.0f); // 弱点/暴击点

        private final float multiplier;

        HitPart(float multiplier) {
            this.multiplier = multiplier;
        }

        public float getMultiplier() {
            return multiplier;
        }
    }

    protected final Vector3f centerOffset = new Vector3f(0, 0, 0);
    private boolean isTrigger = false;
    private HitPart part = HitPart.BODY;

    /**
     * 获取世界坐标系下的包围盒
     * 用于初步的粗略碰撞检测
     */
    public abstract AABB getBoundingBox();

    /**
     * 检测是否包含某个点（用于鼠标拾取或精确检测）
     */
    public abstract boolean containsPoint(Vector3f point);

    /* -------------------------------------------------------------------------- */
    /* Getter & Setter                                                            */
    /* -------------------------------------------------------------------------- */

    public void setCenterOffset(float x, float y, float z) {
        this.centerOffset.set(x, y, z);
    }

    public boolean isTrigger() { return isTrigger; }
    public void setTrigger(boolean trigger) { isTrigger = trigger; }

    public HitPart getPart() { return part; }
    public void setPart(HitPart part) { this.part = part; }
}