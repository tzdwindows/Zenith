package com.zenith.core;

import com.zenith.asset.AssetResource;
import com.zenith.common.utils.InternalLogger;
import com.zenith.logic.script.ScriptManager;
import com.zenith.render.Window;
import com.zenith.render.backend.opengl.shader.ScreenShader;
import org.graalvm.polyglot.Value;

/**
 * 脚本引擎，用于加载和执行脚本。
 * 支持热重载：修改 JS 文件后自动重新映射函数。
 */
public class ScriptZenithEngine extends ZenithEngine {
    private AssetResource resource;
    private final ScriptManager scriptManager;
    private Value jsUpdate;
    private Value jsRenderScene;
    private Value jsRenderAfterOpaque;
    private Value jsInit;
    private Value jsOnBufferToScreen;

    public ScriptZenithEngine(Window window, AssetResource resource) {
        super(window);
        this.resource = resource;
        this.scriptManager = new ScriptManager();
        scriptManager.setVariable("engine", this);
        scriptManager.setVariable("camera", this.getCamera());
        scriptManager.setVariable("window", this.getWindow());
        this.resource.onUpdate(this::handleScriptReload);
        reloadScriptContext(this.resource);
    }

    /**
     * 重载脚本上下文并重新映射函数句柄
     */
    private void reloadScriptContext(AssetResource newResource) {
        try {
            this.resource = newResource;
            scriptManager.execute(newResource);
            this.jsUpdate = scriptManager.getGlobal("update");
            this.jsRenderScene = scriptManager.getGlobal("renderScene");
            this.jsRenderAfterOpaque = scriptManager.getGlobal("renderAfterOpaqueScene");
            this.jsInit = scriptManager.getGlobal("init");
            this.jsOnBufferToScreen = scriptManager.getGlobal("onBufferToScreen");
            validateFunctions();
            if (jsInit != null && jsInit.canExecute()) {
                jsInit.execute();
                InternalLogger.info("脚本 init() 已执行（重载/初次加载）。");
            }
        } catch (Exception e) {
            InternalLogger.error("脚本重载失败: " + e.getMessage());
        }
    }

    /**
     * 当文件变动时触发的回调
     */
    private void handleScriptReload(AssetResource newResource) {
        InternalLogger.info("检测到脚本源码变动，正在应用热重载...");
        reloadScriptContext(newResource);
    }

    private void validateFunctions() {
        if (jsUpdate == null || !jsUpdate.canExecute()) {
            InternalLogger.warn("未找到 update(deltaTime) 函数。");
        }
        if (jsRenderScene == null || !jsRenderScene.canExecute()) {
            InternalLogger.warn("未找到 renderScene() 函数。");
        }
    }

    @Override
    protected void onBufferToScreen(ScreenShader screenShader) {
        if (jsOnBufferToScreen != null && jsOnBufferToScreen.canExecute()) {
            jsOnBufferToScreen.execute(screenShader);
        }
        super.onBufferToScreen(screenShader);
    }

    @Override
    protected void init() {
        if (jsInit != null && jsInit.canExecute()) {
            jsInit.execute();
        }
    }

    @Override
    protected void update(float deltaTime) {
        if (resource != null) {
            resource.checkForUpdates();
        }
        if (jsUpdate != null && jsUpdate.canExecute()) {
            jsUpdate.execute(deltaTime);
        }
    }

    @Override
    protected void renderScene() {
        if (jsRenderScene != null && jsRenderScene.canExecute()) {
            jsRenderScene.execute();
        }
    }

    @Override
    protected void renderAfterOpaqueScene() {
        if (jsRenderAfterOpaque != null && jsRenderAfterOpaque.canExecute()) {
            jsRenderAfterOpaque.execute();
        }
    }

    public ScriptManager getScriptManager() {
        return scriptManager;
    }
}