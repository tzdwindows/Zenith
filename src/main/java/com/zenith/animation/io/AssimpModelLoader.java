package com.zenith.animation.io;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.zenith.animation.data.AnimatedModel;
import com.zenith.animation.runtime.AnimationClip;
import com.zenith.animation.runtime.Keyframes;
import com.zenith.animation.runtime.Skeleton;
import com.zenith.common.utils.InternalLogger;
import com.zenith.render.Texture;
import com.zenith.render.VertexLayout;
import com.zenith.render.backend.opengl.animation.GLSkinnedMesh;
import org.joml.Matrix4f;
import org.lwjgl.PointerBuffer;
import org.lwjgl.assimp.*;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.IntBuffer;
import java.util.*;

import static org.lwjgl.assimp.Assimp.*;
import static org.lwjgl.opengl.GL11.GL_INT;

/**
 * 工业级动画模型加载器：支持自动识别 (.json) 或 3D 原始文件 (FBX, glTF, OBJ 等)。
 */
public class AssimpModelLoader {

    private static final int MAX_WEIGHTS = 4;

    // 配置 Gson 及其自定义适配器
    private static final Gson gson = new GsonBuilder()
            .registerTypeAdapter(Matrix4f.class, new Matrix4fAdapter())
            .create();

    /**
     * 统一加载入口：自动识别文件格式
     */
    public static AnimatedModel load(String resourcePath) {
        if (resourcePath == null || resourcePath.isEmpty()) {
            throw new IllegalArgumentException("Resource path is invalid.");
        }

        if (resourcePath.toLowerCase().endsWith(".json")) {
            return loadFromJson(resourcePath);
        } else {
            return loadFromAssimp(resourcePath);
        }
    }

    // ============================================================
    // JSON 加载分支
    // ============================================================

    private static AnimatedModel loadFromJson(String path) {
        try (FileReader reader = new FileReader(path)) {
            JsonModelData data = gson.fromJson(reader, JsonModelData.class);

            // 1. 重建 Skeleton
            Matrix4f[] ibps = new Matrix4f[data.skeleton.inverseBindPoses.length];
            for (int i = 0; i < ibps.length; i++) {
                ibps[i] = new Matrix4f().set(data.skeleton.inverseBindPoses[i]);
            }
            Skeleton skeleton = new Skeleton(data.skeleton.parentIndices, data.skeleton.jointNames, ibps);

            // 2. 重建 Mesh (使用交织布局)
            VertexLayout layout = createStandardLayout();
            int vertexCount = data.vertices.length / 16;
            GLSkinnedMesh mesh = new GLSkinnedMesh(vertexCount, layout);
            mesh.updateVertices(data.vertices);
            // mesh.updateIndices(data.indices); // 需 GLMesh 支持

            // 3. 重建 Animations (这里逻辑根据 AnimationClip 的具体实现调整)
            Map<String, AnimationClip> animations = new HashMap<>();
            // ... 解析逻辑 ...

            return new AnimatedModel(data.modelName, mesh, skeleton, animations);
        } catch (IOException e) {
            throw new RuntimeException("JSON loading failed: " + path, e);
        }
    }

    // ============================================================
    // Assimp 加载分支 (FBX, glTF, etc.)
    // ============================================================

    private static AnimatedModel loadFromAssimp(String resourcePath) {
        AIScene scene = aiImportFile(resourcePath,
                aiProcess_Triangulate |
                        aiProcess_GenSmoothNormals |
                        aiProcess_FlipUVs |
                        aiProcess_LimitBoneWeights |
                        aiProcess_JoinIdenticalVertices |
                        aiProcess_PopulateArmatureData
        );

        if (scene == null || scene.mRootNode() == null) {
            throw new RuntimeException("Assimp error: " + aiGetErrorString());
        }

        String modelName = new java.io.File(resourcePath).getName();
        File file = new File(resourcePath);
        String modelDir = file.getParent();

        // 1. 骨骼层级处理
        List<BoneData> boneList = new ArrayList<>();
        Map<String, Integer> boneMap = new HashMap<>();
        buildBoneHierarchy(scene, boneMap, boneList);

        // 2. 顶点数据处理 (交织布局)
        GLSkinnedMesh mesh = loadMeshFromAssimp(scene, boneMap);

        // 3. 运行时骨架创建
        Skeleton skeleton = createSkeletonFromBoneList(boneList);

        // 4. 动画剪辑加载
        Map<String, AnimationClip> animations = loadAnimationsFromAssimp(scene, boneMap, skeleton.numJoints());

        aiReleaseImport(scene);

        InternalLogger.info("Model " + modelName + " loading completed.");
        List<Texture> textures = loadMaterials(scene, modelDir);
        AnimatedModel model = new AnimatedModel(modelName, mesh, skeleton, animations);
        model.setTextures(textures);
        return model;
    }

    private static List<Texture> loadMaterials(AIScene scene, String modelDir) {
        List<Texture> textures = new ArrayList<>();
        int materialCount = scene.mNumMaterials();
        PointerBuffer materialBuffer = scene.mMaterials();

        if (materialBuffer == null) return textures;

        for (int i = 0; i < materialCount; i++) {
            AIMaterial material = AIMaterial.create(materialBuffer.get(i));
            // 加载 Diffuse (漫反射) 贴图
            Texture diffuseTex = loadTextureByType(material, aiTextureType_DIFFUSE, modelDir);
            if (diffuseTex != null) {
                textures.add(diffuseTex);
            }
        }
        return textures;
    }

    private static com.zenith.render.Texture loadTextureByType(AIMaterial material, int textureType, String modelDir) {
        AIString path = AIString.calloc();
        int result = aiGetMaterialTexture(material, textureType, 0, path, (IntBuffer) null, null, null, null, null, null);

        if (result != aiReturn_SUCCESS) {
            path.free();
            return null;
        }

        String textPath = path.dataString().replace("\\", "/");
        java.io.File textureFile = new java.io.File(modelDir, textPath);

        // 路径纠错
        if (!textureFile.exists()) {
            textureFile = new java.io.File(modelDir, new java.io.File(textPath).getName());
        }

        if (textureFile.exists()) {
            try {
                com.zenith.asset.AssetIdentifier id = new com.zenith.asset.AssetIdentifier(textureFile.getAbsolutePath());
                java.io.FileInputStream fis = new java.io.FileInputStream(textureFile);
                com.zenith.asset.AssetResource resource = new com.zenith.asset.AssetResource(
                        "LocalDisk",          // sourceName
                        id,                   // location
                        fis,                  // inputStream
                        null,                 // metaStream (贴图不需要 meta)
                        textureFile.lastModified() // lastModified
                );
                com.zenith.render.Texture texture = new com.zenith.render.backend.opengl.texture.GLTexture(resource);
                InternalLogger.info("Texture bound from disk: " + textureFile.getName());
                path.free();
                return texture;
            } catch (Exception e) {
                InternalLogger.error("Failed to bind texture: " + textureFile.getName() + " -> " + e.getMessage());
            }
        }
        path.free();
        return null;
    }

    private static void buildBoneHierarchy(AIScene scene, Map<String, Integer> boneMap, List<BoneData> boneList) {
        // 1. 收集显式蒙皮骨骼的 OffsetMatrix
        Map<String, Matrix4f> offsetMatrices = new HashMap<>();
        int meshCount = scene.mNumMeshes();
        PointerBuffer meshes = scene.mMeshes();
        for (int i = 0; i < meshCount; i++) {
            AIMesh aiMesh = AIMesh.create(meshes.get(i));
            PointerBuffer aiBones = aiMesh.mBones();
            if (aiBones != null) {
                for (int j = 0; j < aiMesh.mNumBones(); j++) {
                    AIBone aiBone = AIBone.create(aiBones.get(j));
                    offsetMatrices.put(aiBone.mName().dataString(), toJoml(aiBone.mOffsetMatrix()));
                }
            }
        }

        // 2. 开始递归遍历，同时传递父节点的全局变换
        InternalLogger.info("Building hierarchy with proper Inverse Bind Poses...");
        // 初始全局变换为单位阵
        traverseNodeTree(Objects.requireNonNull(scene.mRootNode()), -1, new Matrix4f(), boneMap, boneList, offsetMatrices);
    }

    private static void traverseNodeTree(AINode node, int parentIdx, Matrix4f parentGlobalTransform,
                                         Map<String, Integer> boneMap, List<BoneData> boneList,
                                         Map<String, Matrix4f> offsetMatrices) {
        String name = node.mName().dataString();
        int currentIdx = boneList.size();

        // 计算当前节点的初始全局变换：ParentGlobal * Local
        Matrix4f localTransform = toJoml(node.mTransformation());
        Matrix4f globalTransform = new Matrix4f(parentGlobalTransform).mul(localTransform);

        // 确定逆绑定矩阵 (IBP)
        Matrix4f ibp;
        if (offsetMatrices.containsKey(name)) {
            // 如果 Assimp 提供了显式的 OffsetMatrix，优先使用它
            ibp = offsetMatrices.get(name);
        } else {
            // 否则（对于普通节点），IBP = 初始全局变换的逆
            ibp = new Matrix4f(globalTransform).invert();
        }

        boneMap.put(name, currentIdx);
        BoneData data = new BoneData(currentIdx, name, ibp);
        data.parentIndex = parentIdx;
        boneList.add(data);

        // 递归子节点
        int childCount = node.mNumChildren();
        PointerBuffer children = node.mChildren();
        if (children != null) {
            for (int i = 0; i < childCount; i++) {
                // 将当前的全局变换传给子节点
                traverseNodeTree(AINode.create(children.get(i)), currentIdx, globalTransform, boneMap, boneList, offsetMatrices);
            }
        }
    }


    /**
     * 递归遍历：确保父节点永远比子节点先被添加
     */
    private static void traverseNodeTree(AINode node, int parentIdx, Map<String, Integer> boneMap,
                                         List<BoneData> boneList, Map<String, Matrix4f> offsetMatrices) {
        String name = node.mName().dataString();
        int currentIdx = boneList.size(); // 当前长度即为下一个可用的 ID

        // 获取该节点的逆绑定矩阵，如果没有（只是普通节点），则使用单位阵
        Matrix4f ibp = offsetMatrices.getOrDefault(name, new Matrix4f());

        // 记录 ID 映射并添加到列表
        boneMap.put(name, currentIdx);
        BoneData data = new BoneData(currentIdx, name, ibp);
        data.parentIndex = parentIdx; // 记录父节点索引
        boneList.add(data);

        // 递归处理子节点
        int childCount = node.mNumChildren();
        PointerBuffer children = node.mChildren();
        if (children != null) {
            for (int i = 0; i < childCount; i++) {
                traverseNodeTree(AINode.create(children.get(i)), currentIdx, boneMap, boneList, offsetMatrices);
            }
        }
    }

    private static void setParentIndices(AINode node, int lastValidParentIdx, Map<String, Integer> boneMap, List<BoneData> boneList) {
        String name = node.mName().dataString();
        int currentBoneIdx = boneMap.getOrDefault(name, -1);

        int nextParentIdxForChildren = lastValidParentIdx;

        if (currentBoneIdx != -1) {
            boneList.get(currentBoneIdx).parentIndex = lastValidParentIdx;
            nextParentIdxForChildren = currentBoneIdx;
        }

        int childCount = node.mNumChildren();
        PointerBuffer children = node.mChildren();
        for (int i = 0; i < childCount; i++) {
            setParentIndices(AINode.create(children.get(i)), nextParentIdxForChildren, boneMap, boneList);
        }
    }


    private static GLSkinnedMesh loadMeshFromAssimp(AIScene scene, Map<String, Integer> boneMap) {
        int meshCount = scene.mNumMeshes();
        PointerBuffer meshes = scene.mMeshes();

        int totalVertices = 0;
        int totalIndices = 0;
        for (int i = 0; i < meshCount; i++) {
            AIMesh m = AIMesh.create(meshes.get(i));
            totalVertices += m.mNumVertices();
            totalIndices += m.mNumFaces() * 3;
        }

        int[] allBoneIds = new int[totalVertices * 4];
        float[] allWeights = new float[totalVertices * 4];
        Arrays.fill(allBoneIds, -1);

        // --- 核心修复：建立 Mesh 到 NodeID 的映射 ---
        // 这样我们才知道没有骨骼的 Mesh 到底属于哪个会动的节点
        Map<Integer, Integer> meshToNodeMap = new HashMap<>();
        mapMeshesToNodes(scene.mRootNode(), boneMap, meshToNodeMap);

        int vOffset = 0;
        for (int i = 0; i < meshCount; i++) {
            AIMesh m = AIMesh.create(meshes.get(i));
            PointerBuffer aiBones = m.mBones();

            if (aiBones != null && m.mNumBones() > 0) {
                // 1. 蒙皮动画逻辑 (模型自带权重)
                for (int b = 0; b < m.mNumBones(); b++) {
                    AIBone bone = AIBone.create(aiBones.get(b));
                    int bId = boneMap.getOrDefault(bone.mName().dataString(), -1);
                    if (bId == -1) continue;
                    AIVertexWeight.Buffer weights = bone.mWeights();
                    for (int w = 0; w < bone.mNumWeights(); w++) {
                        AIVertexWeight vw = weights.get(w);
                        fillVertexBoneData(allBoneIds, allWeights, vOffset + vw.mVertexId(), bId, vw.mWeight());
                    }
                }
            } else {
                // 2. 节点动画逻辑 (刚体，整个 Mesh 跟着节点动)
                int nodeId = meshToNodeMap.getOrDefault(i, -1);
                if (nodeId != -1) {
                    for (int v = 0; v < m.mNumVertices(); v++) {
                        // 强制给这个顶点的第一个槽位绑定 1.0 的权重，关联到其父节点
                        fillVertexBoneData(allBoneIds, allWeights, vOffset + v, nodeId, 1.0f);
                    }
                }
            }
            vOffset += m.mNumVertices();
        }

        // 构建交织数组 (Interleaved data)
        float[] interleaved = new float[totalVertices * 16];
        int[] indices = new int[totalIndices];
        vOffset = 0;
        int iOffset = 0;

        for (int i = 0; i < meshCount; i++) {
            AIMesh m = AIMesh.create(meshes.get(i));
            for (int v = 0; v < m.mNumVertices(); v++) {
                int p = (vOffset + v) * 16;
                int gV = vOffset + v;

                AIVector3D pos = m.mVertices().get(v);
                interleaved[p + 0] = pos.x(); interleaved[p + 1] = pos.y(); interleaved[p + 2] = pos.z();

                if (m.mTextureCoords(0) != null) {
                    AIVector3D uv = m.mTextureCoords(0).get(v);
                    interleaved[p + 3] = uv.x(); interleaved[p + 4] = uv.y();
                } else {
                    interleaved[p + 3] = 0; interleaved[p + 4] = 0;
                }

                AIVector3D norm = m.mNormals().get(v);
                if(norm != null) {
                    interleaved[p + 5] = norm.x(); interleaved[p + 6] = norm.y(); interleaved[p + 7] = norm.z();
                }

                // 写入 IDs 和 Weights
                interleaved[p + 8] = Float.intBitsToFloat(allBoneIds[gV * 4 + 0]);
                interleaved[p + 9] = Float.intBitsToFloat(allBoneIds[gV * 4 + 1]);
                interleaved[p + 10] = Float.intBitsToFloat(allBoneIds[gV * 4 + 2]);
                interleaved[p + 11] = Float.intBitsToFloat(allBoneIds[gV * 4 + 3]);
                interleaved[p + 12] = allWeights[gV * 4 + 0];
                interleaved[p + 13] = allWeights[gV * 4 + 1];
                interleaved[p + 14] = allWeights[gV * 4 + 2];
                interleaved[p + 15] = allWeights[gV * 4 + 3];
            }

            for (int f = 0; f < m.mNumFaces(); f++) {
                AIFace face = m.mFaces().get(f);
                if (face.mNumIndices() == 3) {
                    indices[iOffset++] = vOffset + face.mIndices().get(0);
                    indices[iOffset++] = vOffset + face.mIndices().get(1);
                    indices[iOffset++] = vOffset + face.mIndices().get(2);
                }
            }
            vOffset += m.mNumVertices();
        }

        GLSkinnedMesh mesh = new GLSkinnedMesh(totalVertices, createStandardLayout());
        mesh.updateVertices(interleaved);
        mesh.updateIndices(indices);
        return mesh;
    }

    private static void mapMeshesToNodes(AINode node, Map<String, Integer> boneMap, Map<Integer, Integer> meshToNodeMap) {
        String name = node.mName().dataString();
        int nodeId = boneMap.getOrDefault(name, -1);

        IntBuffer meshes = node.mMeshes();
        if (meshes != null) {
            for (int i = 0; i < node.mNumMeshes(); i++) {
                meshToNodeMap.put(meshes.get(i), nodeId);
            }
        }

        PointerBuffer children = node.mChildren();
        if (children != null) {
            for (int i = 0; i < node.mNumChildren(); i++) {
                mapMeshesToNodes(AINode.create(children.get(i)), boneMap, meshToNodeMap);
            }
        }
    }

    private static Map<String, AnimationClip> loadAnimationsFromAssimp(AIScene scene, Map<String, Integer> boneMap, int numJoints) {
        Map<String, AnimationClip> clips = new HashMap<>();
        int count = scene.mNumAnimations();
        if (count == 0) return clips;

        PointerBuffer anims = scene.mAnimations();
        for (int i = 0; i < count; i++) {
            AIAnimation aiAnim = AIAnimation.create(anims.get(i));
            String animName = aiAnim.mName().dataString();
            double tps = aiAnim.mTicksPerSecond() != 0 ? aiAnim.mTicksPerSecond() : 25.0;
            float duration = (float) (aiAnim.mDuration() / tps);

            List<AnimationClip.JointData> jointDataList = new ArrayList<>();
            int channelCount = aiAnim.mNumChannels();
            int matchedChannels = 0;

            PointerBuffer channels = aiAnim.mChannels();
            for (int c = 0; c < channelCount; c++) {
                AINodeAnim channel = AINodeAnim.create(channels.get(c));
                String channelName = channel.mNodeName().dataString();

                // 尝试匹配骨骼 ID
                int jIdx = boneMap.getOrDefault(channelName, -1);

                // 针对 GLTF 的特殊处理：有时候名字会带有路径或前缀
                if (jIdx == -1 && channelName.contains("_")) {
                    String shortName = channelName.substring(channelName.lastIndexOf("_") + 1);
                    jIdx = boneMap.getOrDefault(shortName, -1);
                }

                if (jIdx != -1) {
                    AnimationClip.JointData jd = new AnimationClip.JointData();
                    jd.jointIndex = jIdx;
                    jd.translations = extractTranslationKeys(channel, tps);
                    jd.rotations = extractRotationKeys(channel, tps);
                    jd.scales = extractScaleKeys(channel, tps);
                    jointDataList.add(jd);
                    matchedChannels++;
                }
            }

            InternalLogger.info(String.format("Animation '%s': Found %d channels, Matched %d/%d to joints.",
                    animName, channelCount, matchedChannels, numJoints));

            clips.put(animName, new AnimationClip(animName, duration, numJoints, jointDataList.toArray(new AnimationClip.JointData[0])));
        }
        return clips;
    }


    private static Keyframes.TranslationKey[] extractTranslationKeys(AINodeAnim c, double tps) {
        Keyframes.TranslationKey[] k = new Keyframes.TranslationKey[c.mNumPositionKeys()];
        for (int i = 0; i < k.length; i++) {
            AIVectorKey key = c.mPositionKeys().get(i);
            k[i] = new Keyframes.TranslationKey();
            k[i].time = (float) (key.mTime() / tps); // 转换为秒
            k[i].value.set(key.mValue().x(), key.mValue().y(), key.mValue().z());
        }
        return k;
    }

    private static Keyframes.RotationKey[] extractRotationKeys(AINodeAnim c, double tps) {
        Keyframes.RotationKey[] k = new Keyframes.RotationKey[c.mNumRotationKeys()];
        for (int i = 0; i < k.length; i++) {
            AIQuatKey key = c.mRotationKeys().get(i);
            k[i] = new Keyframes.RotationKey();
            k[i].time = (float) (key.mTime() / tps); // 转换为秒
            k[i].value.set(key.mValue().x(), key.mValue().y(), key.mValue().z(), key.mValue().w());
        }
        return k;
    }

    private static Keyframes.ScaleKey[] extractScaleKeys(AINodeAnim c, double tps) {
        Keyframes.ScaleKey[] k = new Keyframes.ScaleKey[c.mNumScalingKeys()];
        for (int i = 0; i < k.length; i++) {
            AIVectorKey key = c.mScalingKeys().get(i);
            k[i] = new Keyframes.ScaleKey();
            k[i].time = (float) (key.mTime() / tps); // 转换为秒
            k[i].value.set(key.mValue().x(), key.mValue().y(), key.mValue().z());
        }
        return k;
    }

    // ============================================================
    // 辅助工具方法
    // ============================================================

    private static VertexLayout createStandardLayout() {
        VertexLayout layout = new VertexLayout();
        layout.pushFloat("aPos", 3);
        layout.pushFloat("aTexCoord", 2);
        layout.pushFloat("aNormal", 3);
        layout.pushInt("aBoneIDs", 4);
        layout.pushFloat("aWeights", 4);
        return layout;
    }

    private static void fillVertexBoneData(int[] ids, float[] ws, int v, int b, float w) {
        for (int i = 0; i < 4; i++) {
            if (ids[v * 4 + i] == -1) {
                ids[v * 4 + i] = b;
                ws[v * 4 + i] = w;
                break;
            }
        }
    }

    private static Keyframes.TranslationKey[] extractTranslationKeys(AINodeAnim c) {
        Keyframes.TranslationKey[] k = new Keyframes.TranslationKey[c.mNumPositionKeys()];
        for (int i = 0; i < k.length; i++) {
            AIVectorKey key = c.mPositionKeys().get(i);
            k[i] = new Keyframes.TranslationKey();
            k[i].time = (float) key.mTime();
            k[i].value.set(key.mValue().x(), key.mValue().y(), key.mValue().z());
        }
        return k;
    }

    private static Keyframes.RotationKey[] extractRotationKeys(AINodeAnim c) {
        Keyframes.RotationKey[] k = new Keyframes.RotationKey[c.mNumRotationKeys()];
        for (int i = 0; i < k.length; i++) {
            AIQuatKey key = c.mRotationKeys().get(i);
            k[i] = new Keyframes.RotationKey();
            k[i].time = (float) key.mTime();
            k[i].value.set(key.mValue().x(), key.mValue().y(), key.mValue().z(), key.mValue().w());
        }
        return k;
    }

    private static Keyframes.ScaleKey[] extractScaleKeys(AINodeAnim c) {
        Keyframes.ScaleKey[] k = new Keyframes.ScaleKey[c.mNumScalingKeys()];
        for (int i = 0; i < k.length; i++) {
            AIVectorKey key = c.mScalingKeys().get(i);
            k[i] = new Keyframes.ScaleKey();
            k[i].time = (float) key.mTime();
            k[i].value.set(key.mValue().x(), key.mValue().y(), key.mValue().z());
        }
        return k;
    }

    private static Matrix4f toJoml(AIMatrix4x4 m) {
        return new Matrix4f(
                m.a1(), m.b1(), m.c1(), m.d1(),
                m.a2(), m.b2(), m.c2(), m.d2(),
                m.a3(), m.b3(), m.c3(), m.d3(),
                m.a4(), m.b4(), m.c4(), m.d4()
        );
    }



    private static Skeleton createSkeletonFromBoneList(List<BoneData> list) {
        int s = list.size();
        int[] p = new int[s]; String[] n = new String[s]; Matrix4f[] i = new Matrix4f[s];
        for (int x = 0; x < s; x++) {
            p[x] = list.get(x).parentIndex; n[x] = list.get(x).name; i[x] = list.get(x).inverseBindPose;
        }
        return new Skeleton(p, n, i);
    }

    private static class BoneData {
        int id; String name; int parentIndex = -1; Matrix4f inverseBindPose;
        BoneData(int id, String name, Matrix4f ibp) { this.id = id; this.name = name; this.inverseBindPose = ibp; }
    }
}