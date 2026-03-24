package com.zenith.logic.component;

import com.zenith.logic.state.AIState;
import com.zenith.logic.scene.entity.Entity; // 确保引入 Entity
import com.zenith.common.utils.InternalLogger;
import java.util.HashMap;
import java.util.Map;

public class AIController extends Component {

    private final Map<String, AIState> stateMap = new HashMap<>();
    private AIState currentState;
    private String currentStateName = "";

    /**
     * 注册一个状态到 AI 中
     */
    public void addState(String name, AIState state) {
        state.setOwner(owner);
        stateMap.put(name, state);
    }

    /**
     * 切换状态
     */
    public void switchState(String name) {
        if (!stateMap.containsKey(name)) {
            InternalLogger.error("AI 错误: 找不到状态 " + name);
            return;
        }

        if (currentState != null) {
            currentState.onExit();
        }

        currentStateName = name;
        currentState = stateMap.get(name);
        currentState.onEnter();

        InternalLogger.debug("实体 [" + owner.getName() + "] AI 切换至状态: " + name);
    }

    /**
     * 当宿主实体受到伤害时的 AI 反应
     * @param attacker 攻击者
     * @param damage 伤害数值
     */
    public void onDamaged(Entity attacker, float damage) {
        if (currentState == null) return;
        if (currentStateName.equals("PATROL") && attacker != null) {
            InternalLogger.debug("实体 [" + owner.getName() + "] 受到攻击，从巡逻切换至警戒！");
        }
    }

    @Override
    public void onCreate() {}

    @Override
    public void onUpdate(float deltaTime) {
        if (currentState != null) {
            currentState.onUpdate(deltaTime);
        }
    }

    @Override
    public void onDestroy() {
        if (currentState != null) {
            currentState.onExit();
        }
        stateMap.clear();
    }

    public String getCurrentStateName() {
        return currentStateName;
    }
}