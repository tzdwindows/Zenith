package com.zenith.render;

import com.zenith.asset.AssetResource;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public abstract class Model {
    protected String name;
    protected List<Mesh> meshes = new ArrayList<>();
    protected List<Material> materials = new ArrayList<>();

    protected Model(String name) {
        this.name = name;
    }

    public abstract void load(AssetResource resource) throws IOException;

    public abstract List<String> getAnimationNames();
    public abstract void playAnimation(String animationName);
    public abstract void update(float deltaTime);
    // =========================================================

    public void draw() {
        for (int i = 0; i < meshes.size(); i++) {
            if (i < materials.size() && materials.get(i) != null) {
                materials.get(i).apply();
            }
            meshes.get(i).render();
        }
    }

    public abstract void dispose();
}