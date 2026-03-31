package com.zenith.core;

import com.zenith.asset.AssetResource;
import com.zenith.common.utils.InternalLogger;
import com.zenith.logic.script.ScriptManager;
import com.zenith.render.Window;
import com.zenith.render.backend.opengl.shader.ScreenShader;
import org.graalvm.polyglot.Value;

/**
 * 脚本引擎，用于加载和执行脚本。
 * 支持异步加载与热重载：修改 JS 文件后自动重新映射函数。
 */
public class ScriptZenithEngine extends ZenithEngine {
    private AssetResource resource;
    private final ScriptManager scriptManager;
    private Value jsAsyncLoad;
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
    }

    @Override
    protected void asyncLoad() {
        InternalLogger.info("正在后台编译 JS 脚本...");
        setLoadingProgress(0.1f);

        try {
            scriptManager.execute(resource);
            this.jsAsyncLoad = scriptManager.getGlobal("asyncLoad");
            this.jsUpdate = scriptManager.getGlobal("update");
            this.jsRenderScene = scriptManager.getGlobal("renderScene");
            this.jsRenderAfterOpaque = scriptManager.getGlobal("renderAfterOpaqueScene");
            this.jsInit = scriptManager.getGlobal("init");
            this.jsOnBufferToScreen = scriptManager.getGlobal("onBufferToScreen");

            validateFunctions();
            setLoadingProgress(0.3f);

            if (jsAsyncLoad != null && jsAsyncLoad.canExecute()) {
                InternalLogger.info("正在执行 JS 脚本的 asyncLoad()...");
                jsAsyncLoad.execute();
            } else {
                Thread.sleep(300);
            }

            setLoadingProgress(1.0f);

        } catch (Exception e) {
            InternalLogger.error("脚本初始编译/加载失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    protected void init() {
        // 主线程正式初始化：执行 JS 的 init()
        if (jsInit != null && jsInit.canExecute()) {
            InternalLogger.info("正在执行 JS 脚本的 init() (绑定 GL 资源)...");
            jsInit.execute();
        }
    }

    /**
     * 当文件变动时触发的回调 (热重载)
     */
    private void handleScriptReload(AssetResource newResource) {
        InternalLogger.info("检测到脚本源码变动，正在应用热重载...");
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
                runOnMainThread(() -> {
                    jsInit.execute();
                    InternalLogger.info("脚本热重载完毕，init() 已重新执行。");
                });
            }
        } catch (Exception e) {
            InternalLogger.error("脚本重载失败: " + e.getMessage());
        }
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

    @Override
    protected void onBufferToScreen(float realDeltaTime, ScreenShader screenShader) {
        if (jsOnBufferToScreen != null && jsOnBufferToScreen.canExecute()) {
            jsOnBufferToScreen.execute(realDeltaTime, screenShader);
        }
        super.onBufferToScreen(realDeltaTime, screenShader);
    }

    public ScriptManager getScriptManager() {
        return scriptManager;
    }
}