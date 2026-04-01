package com.zenith.animation.data;

import com.zenith.animation.runtime.AnimationClip;
import com.zenith.animation.runtime.Skeleton;
import com.zenith.render.Texture;
import com.zenith.render.backend.opengl.animation.GLSkinnedMesh;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class AnimatedModel {
    private final String name;
    private final GLSkinnedMesh mesh;
    private final Skeleton skeleton;
    private final Map<String, AnimationClip> animations;
    private List<Texture> textures = new ArrayList<>();

    public AnimatedModel(String name, GLSkinnedMesh mesh, Skeleton skeleton, Map<String, AnimationClip> animations) {
        this.name = name;
        this.mesh = mesh;
        this.skeleton = skeleton;
        this.animations = animations;
    }

    public GLSkinnedMesh getMesh() { return mesh; }
    public Skeleton getSkeleton() { return skeleton; }
    public AnimationClip getAnimation(String animName) { return animations.get(animName); }
    public Map<String, AnimationClip> getAllAnimations() { return Collections.unmodifiableMap(animations); }
    public List<Texture> getTextures() { return textures; }

    public void dispose() {
        if (mesh != null) mesh.dispose();
    }

    public void setTextures(List<Texture> textures) {
        if (textures != null) {
            this.textures = textures;
        }
    }
}