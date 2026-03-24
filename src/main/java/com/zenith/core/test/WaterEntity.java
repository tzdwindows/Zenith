package com.zenith.core.test;

import com.zenith.common.math.Transform;
import com.zenith.render.Material;
import com.zenith.render.Mesh;
import com.zenith.render.Renderable;

public class WaterEntity implements Renderable {
    private final Mesh mesh;
    private final Material material;
    private final Transform transform;

    public WaterEntity(Mesh mesh, Material material) {
        this.mesh = mesh;
        this.material = material;
        this.transform = new Transform();
    }

    @Override public Mesh getMesh() { return mesh; }
    @Override public Material getMaterial() { return material; }
    @Override public Transform getTransform() { return transform; }
}