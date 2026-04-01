package com.zenith.animation.runtime;

import com.zenith.animation.data.AnimatedModel; // 新增导入
import com.zenith.render.Texture;               // 新增导入
import com.zenith.render.backend.opengl.animation.GLBoneBuffer;
import com.zenith.common.math.Transform;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;
import java.nio.FloatBuffer;
import java.util.List;                          // 新增导入

import static org.lwjgl.opengl.ARBInternalformatQuery2.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11C.glBindTexture;
import static org.lwjgl.opengl.GL13C.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13C.glActiveTexture;

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

    private static final int MAX_BONES = 100;

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

        this.skinningBuffer = MemoryUtil.memAllocFloat(MAX_BONES * 16);
        for(int i = 0; i < MAX_BONES; i++) {
            new Matrix4f().get(i * 16, skinningBuffer);
        }
        this.gpuBuffer = new GLBoneBuffer(MAX_BONES);
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
        // 如果没有动画，至少也要更新一次矩阵（确保模型以 T-Pose 显示）
        if (currentClip == null) {
            L2M.execute(skeleton, currentLocalPoses, modelMatrices);
            SKINNER.execute(skeleton, modelMatrices, skinningBuffer);
            gpuBuffer.update(skinningBuffer);
            return;
        }

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

        // --- 【修复】必须对当前剪辑进行采样，否则 currentLocalPoses 永远没数据 ---
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

        // 计算全局变换和蒙皮矩阵
        L2M.execute(skeleton, currentLocalPoses, modelMatrices);
        SKINNER.execute(skeleton, modelMatrices, skinningBuffer);

        // 更新 GPU 缓冲区
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
        gpuBuffer.bind(boneBufferSlot);
        if (textures != null && !textures.isEmpty()) {
            for (int i = 0; i < textures.size(); i++) {
                Texture tex = textures.get(i);
                if (tex != null) {
                    tex.bind(i);
                }
            }
        } else {
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, 0);
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