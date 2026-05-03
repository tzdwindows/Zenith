package com.zenith.core;

import com.zenith.render.Camera;
import com.zenith.render.Mesh;
import com.zenith.render.backend.opengl.SceneFramebuffer;

import java.util.List;

@Deprecated(since = "17")
public interface RayTracingProvider {
    void init(int width, int height);
    void buildAccelerationStructures(List<Mesh> meshes);
    void trace(SceneFramebuffer fbo, Camera camera);
    void dispose();
}
