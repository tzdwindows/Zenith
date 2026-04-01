package com.zenith.animation.runtime;

import com.zenith.animation.data.AnimatedModel; // 新增导入
import com.zenith.render.Texture;               // 新增导入
import com.zenith.render.backend.opengl.animation.GLBoneBuffer;
import com.zenith.common.math.Transform;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;
import java.nio.FloatBuffer;
import java.util.List;                          // 新增导入

public class Animator {
    private final Skeleton skeleton;
    private final int numJoints;
    private List<Texture> textures;             // 新增：用于存储贴图引用

    private AnimationClip currentClip;
    private AnimationClip nextClip;
    private float currentTime;
    private float blendTime = 0.0f;
    private float totalBlendTime = 0.0f;

    private float playbackSpeed = 1.0f;
    private boolean isLooping = true;

    private final Transform[] currentLocalPoses;
    private final Transform[] nextLocalPoses;
    private final Matrix4f[] modelMatrices;
    private final FloatBuffer skinningBuffer;

    private final GLBoneBuffer gpuBuffer;

    private static final SamplingJob SAMPLER = new SamplingJob();
    private static final LocalToModelJob L2M = new LocalToModelJob();
    private static final SkinningMatrixJob SKINNER = new SkinningMatrixJob();

    /**
     * 推荐构造函数：直接传入 AnimatedModel
     */
    public Animator(AnimatedModel model) {
        this(model.getSkeleton());
        // 自动获取模型中的贴图
        this.textures = model.getTextures();
    }

    public Animator(Skeleton skeleton) {
        this.skeleton = skeleton;
        this.numJoints = skeleton.numJoints();

        this.currentLocalPoses = new Transform[numJoints];
        this.nextLocalPoses = new Transform[numJoints];
        for (int i = 0; i < numJoints; i++) {
            currentLocalPoses[i] = new Transform();
            nextLocalPoses[i] = new Transform();
        }

        this.modelMatrices = new Matrix4f[numJoints];
        for (int i = 0; i < numJoints; i++) {
            modelMatrices[i] = new Matrix4f();
        }

        this.skinningBuffer = MemoryUtil.memAllocFloat(numJoints * 16);
        this.gpuBuffer = new GLBoneBuffer(numJoints);
    }

    // --- 新增：手动设置贴图的方法 ---
    public void setTextures(List<Texture> textures) {
        this.textures = textures;
    }

    public void setLooping(boolean looping) {
        this.isLooping = looping;
    }

    public void crossfade(AnimationClip newClip, float duration) {
        if (currentClip == null) {
            play(newClip);
            return;
        }
        if (currentClip == newClip) return;

        this.nextClip = newClip;
        this.totalBlendTime = duration;
        this.blendTime = 0.0f;
    }

    public FloatBuffer getSkinningBuffer() {
        skinningBuffer.rewind();
        return skinningBuffer;
    }

    public Matrix4f[] getModelMatrices() {
        return modelMatrices;
    }

    public Skeleton getSkeleton() {
        return skeleton;
    }

    public void play(AnimationClip clip) {
        this.currentClip = clip;
        this.nextClip = null;
        this.currentTime = 0.0f;
        this.totalBlendTime = 0.0f;
    }

    public void update(float dt) {
        if (currentClip == null) return;

        currentTime += dt * playbackSpeed;

        if (isLooping) {
            if (currentTime >= currentClip.getDuration()) {
                currentTime %= currentClip.getDuration();
            }
        } else {
            if (currentTime > currentClip.getDuration()) {
                currentTime = currentClip.getDuration();
            }
        }

        SAMPLER.execute(skeleton, currentClip, currentTime, currentLocalPoses);

        if (nextClip != null && totalBlendTime > 0) {
            blendTime += dt;
            float alpha = Math.min(blendTime / totalBlendTime, 1.0f);
            SAMPLER.execute(skeleton, nextClip, currentTime, nextLocalPoses);
            blendPoses(currentLocalPoses, nextLocalPoses, alpha);

            if (alpha >= 1.0f) {
                currentClip = nextClip;
                nextClip = null;
                totalBlendTime = 0.0f;
            }
        }

        L2M.execute(skeleton, currentLocalPoses, modelMatrices);
        SKINNER.execute(skeleton, modelMatrices, skinningBuffer);
        gpuBuffer.update(skinningBuffer);
    }

    private void blendPoses(Transform[] base, Transform[] target, float alpha) {
        for (int i = 0; i < numJoints; i++) {
            base[i].getPosition().lerp(target[i].getPosition(), alpha);
            base[i].getRotation().slerp(target[i].getRotation(), alpha);
            base[i].getScale().lerp(target[i].getScale(), alpha);
            base[i].setDirty();
        }
    }

    /**
     * 修改后的绑定方法：同时绑定骨骼缓冲区和所有贴图
     * @param boneBufferSlot 骨骼数据所在的绑定点 (Binding Point)
     */
    public void bind(int boneBufferSlot) {
        // 1. 绑定骨骼数据到指定的 Uniform/ShaderStorage Block 槽位
        gpuBuffer.bind(boneBufferSlot);

        // 2. 自动绑定所有贴图到纹理单元 (Texture Unit)
        // 通常：单元 0 为 Diffuse, 单元 1 为 Normal 等，取决于模型加载时的顺序
        if (textures != null) {
            for (int i = 0; i < textures.size(); i++) {
                Texture tex = textures.get(i);
                if (tex != null) {
                    tex.bind(i); // 将第 i 张贴图绑定到 GL_TEXTURE0 + i
                }
            }
        }
    }

    public void setPlaybackSpeed(float speed) { this.playbackSpeed = speed; }

    public void dispose() {
        if (skinningBuffer != null) MemoryUtil.memFree(skinningBuffer);
        gpuBuffer.dispose();
    }

    public String getCurrentTime() {
        return String.format("%.2f", currentTime);
    }
}