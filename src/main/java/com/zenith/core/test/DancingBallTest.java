package com.zenith.core.test;

import com.zenith.asset.AssetResource;
import com.zenith.core.ScriptZenithEngine;
import com.zenith.render.backend.opengl.GLWindow;

public class DancingBallTest {
    public static void main(String[] args) {
        ScriptZenithEngine engine = new ScriptZenithEngine(new GLWindow("Zenith World System - Dancing Ball Test", 1280, 720),
                AssetResource.loadFromResources("scripts/DancingBallTest.js"));
        engine.start();
    }
}
