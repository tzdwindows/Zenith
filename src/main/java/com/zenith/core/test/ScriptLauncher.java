package com.zenith.core.test;

import com.zenith.asset.AssetResource;
import com.zenith.core.ZenithEngine;
import com.zenith.logic.script.ScriptManager;
import com.zenith.render.backend.opengl.GLWindow;
import org.graalvm.polyglot.Value;

/**
 * 脚本测试启动器
 */
public class ScriptLauncher extends ZenithEngine {
    private final ScriptManager scriptManager;
    private boolean scriptInitialized = false;
    private Value jsUpdate;
    private Value jsRenderScene;
    private Value jsRenderAfterOpaque;

    public ScriptLauncher() {
        super(new GLWindow("Zenith World - Scripted", 1280, 720));
        this.scriptManager = new ScriptManager();
        scriptManager.setVariable("engine", this);
        scriptManager.setVariable("camera", this.getCamera());
        scriptManager.setVariable("window", this.getWindow());
        scriptManager.execute(AssetResource.loadFromResources("scripts/test.js"));
        scriptManager.registerClass("WaterEntity", WaterEntity.class);
        this.jsUpdate = scriptManager.getGlobal("update");
        this.jsRenderScene = scriptManager.getGlobal("renderScene");
        this.jsRenderAfterOpaque = scriptManager.getGlobal("renderAfterOpaqueScene");
    }

    @Override
    protected void init() {

    }

    /**
     * 实现父类抽象方法：逻辑更新
     */
    @Override
    protected void update(float deltaTime) {
        // 技巧：如果脚本尚未初始化，在第一帧调用脚本的 init()
        if (!scriptInitialized) {
            Value jsInit = scriptManager.getGlobal("init");
            if (jsInit != null && jsInit.canExecute()) {
                jsInit.execute();
            }
            scriptInitialized = true;
        }

        // 执行脚本的 update
        if (jsUpdate != null && jsUpdate.canExecute()) {
            jsUpdate.execute(deltaTime);
        }
    }

    /**
     * 实现父类抽象方法：场景渲染
     */
    @Override
    protected void renderScene() {
        if (jsRenderScene != null && jsRenderScene.canExecute()) {
            jsRenderScene.execute();
        }
    }

    /**
     * 实现父类抽象方法：后处理/水体渲染
     */
    @Override
    protected void renderAfterOpaqueScene() {
        if (jsRenderAfterOpaque != null && jsRenderAfterOpaque.canExecute()) {
            jsRenderAfterOpaque.execute();
        }
    }

    public static void main(String[] args) {
        new ScriptLauncher().start();
    }
}