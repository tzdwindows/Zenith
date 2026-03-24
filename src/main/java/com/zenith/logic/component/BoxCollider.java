package com.zenith.logic.component;

import com.zenith.common.math.AABB;
import org.joml.Vector3f;

public class BoxCollider extends Collider {

    private final Vector3f size = new Vector3f(1.0f, 1.0f, 1.0f);
    private final AABB worldAABB = new AABB();
    private final Vector3f tempMin = new Vector3f();
    private final Vector3f tempMax = new Vector3f();
    private final Vector3f halfSize = new Vector3f();

    @Override
    public void onCreate() {}

    @Override
    public void onUpdate(float deltaTime) {
        if (owner != null) {
            Vector3f worldPos = owner.getTransform().getPosition();
            size.mul(0.5f, halfSize);
            tempMin.set(worldPos).add(centerOffset).sub(halfSize);
            tempMax.set(worldPos).add(centerOffset).add(halfSize);
            worldAABB.getMin().set(tempMin);
            worldAABB.getMax().set(tempMax);
        }
    }

    @Override
    public void onDestroy() {
    }

    @Override
    public AABB getBoundingBox() {
        return worldAABB;
    }

    @Override
    public boolean containsPoint(Vector3f point) {
        return worldAABB.contains(point);
    }

    public Vector3f getSize() {
        return size;
    }
}