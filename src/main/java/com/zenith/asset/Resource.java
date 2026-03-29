package com.zenith.asset;

public class Resource {
    public static final String LOGO_RESOURCE = "textures/logo.png";
    public static final String FONT_PATH = "font/HarmonyOS_Sans_SC_Regular.ttf";
    public static AssetResource getLogoResource() {
        return AssetResource.loadFromResources(LOGO_RESOURCE);
    }
    public static AssetResource getFontResource() {
        return AssetResource.loadFromResources(FONT_PATH);
    }
}
