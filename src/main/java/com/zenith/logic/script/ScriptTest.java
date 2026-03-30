package com.zenith.logic.script;

import com.zenith.asset.AssetResource;

public class ScriptTest {
    public static void main(String[] args) {
        ScriptManager manager = new ScriptManager();
        manager.execute(AssetResource.loadFromResources("scripts/test.js"));

    }
}
