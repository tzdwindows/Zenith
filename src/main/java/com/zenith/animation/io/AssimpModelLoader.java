package com.zenith.animation.io;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.zenith.animation.data.AnimatedModel;
import com.zenith.animation.runtime.AnimationClip;
import com.zenith.animation.runtime.Keyframes;
import com.zenith.animation.runtime.Skeleton;
import com.zenith.asset.AssetIdentifier;
import com.zenith.asset.AssetResource;
import com.zenith.common.utils.InternalLogger;
import com.zenith.render.Texture;
import com.zenith.render.VertexLayout;
import com.zenith.render.backend.opengl.animation.GLSkinnedMesh;
import com.zenith.render.backend.opengl.texture.GLTexture;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Quaternionf;
import org.lwjgl.PointerBuffer;
import org.lwjgl.assimp.*;
import org.lwjgl.system.MemoryUtil;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.*;

import static org.lwjgl.assimp.Assimp.*;

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
    // JSON 加载分支 (已完整实现)
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

            // 注意：如果你的 JSON 里有 indices 数组，可以取消注释下面这行
            if (data.indices != null && data.indices.length > 0) {
                mesh.updateIndices(data.indices);
            }

            // 3. 重建 Animations
            Map<String, AnimationClip> animations = new HashMap<>();
            if (data.animations != null) {
                for (JsonModelData.JsonAnimation jAnim : data.animations) {
                    List<AnimationClip.JointData> jointDataList = new ArrayList<>();

                    for (JsonModelData.JsonJointData jData : jAnim.jointData) {
                        AnimationClip.JointData jd = new AnimationClip.JointData();
                        jd.jointIndex = jData.jointIndex;

                        // 解析位移
                        jd.translations = new Keyframes.TranslationKey[jData.translations.length];
                        for (int i = 0; i < jData.translations.length; i++) {
                            jd.translations[i] = new Keyframes.TranslationKey();
                            jd.translations[i].time = jData.translations[i].time;
                            jd.translations[i].value.set(jData.translations[i].value);
                        }

                        // 解析旋转
                        jd.rotations = new Keyframes.RotationKey[jData.rotations.length];
                        for (int i = 0; i < jData.rotations.length; i++) {
                            jd.rotations[i] = new Keyframes.RotationKey();
                            jd.rotations[i].time = jData.rotations[i].time;
                            jd.rotations[i].value.set(jData.rotations[i].value);
                        }

                        // 解析缩放
                        jd.scales = new Keyframes.ScaleKey[jData.scales.length];
                        for (int i = 0; i < jData.scales.length; i++) {
                            jd.scales[i] = new Keyframes.ScaleKey();
                            jd.scales[i].time = jData.scales[i].time;
                            jd.scales[i].value.set(jData.scales[i].value);
                        }

                        jointDataList.add(jd);
                    }
                    AnimationClip clip = new AnimationClip(jAnim.name, jAnim.duration, skeleton.numJoints(), jointDataList.toArray(new AnimationClip.JointData[0]));
                    animations.put(jAnim.name, clip);
                }
            }

            InternalLogger.info("Model " + data.modelName + " loaded successfully from JSON.");
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
                        aiProcess_FlipUVs | // 这个必须有，配合 GLTexture 翻转
                        aiProcess_LimitBoneWeights |
                        aiProcess_JoinIdenticalVertices |
                        aiProcess_PopulateArmatureData
        );

        if (scene == null || scene.mRootNode() == null) {
            throw new RuntimeException("Assimp error: " + aiGetErrorString());
        }

        String modelName = new File(resourcePath).getName();
        String modelDir = new File(resourcePath).getParent();

        List<BoneData> boneList = new ArrayList<>();
        Map<String, Integer> boneMap = new HashMap<>();
        buildBoneHierarchy(scene, boneMap, boneList);

        GLSkinnedMesh mesh = loadMeshFromAssimp(scene, boneMap);
        Skeleton skeleton = createSkeletonFromBoneList(boneList);

        Map<String, AnimationClip> animations = loadAnimationsFromAssimp(scene, boneMap, skeleton.numJoints());

        // 解析贴图 (现在传递了 scene 对象，以支持解析内嵌贴图)
        List<Texture> textures = loadMaterials(scene, modelDir);

        aiReleaseImport(scene);

        InternalLogger.info("Model " + modelName + " loading completed.");
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
            // 传入 scene 以便处理内嵌贴图 (Embedded textures)
            Texture diffuseTex = loadTextureByType(scene, material, aiTextureType_DIFFUSE, modelDir);
            if (diffuseTex != null) {
                textures.add(diffuseTex);
            }
        }
        return textures;
    }

    private static Texture loadTextureByType(AIScene scene, AIMaterial material, int textureType, String modelDir) {
        AIString path = AIString.calloc();
        int result = aiGetMaterialTexture(material, textureType, 0, path, (IntBuffer) null, null, null, null, null, null);

        if (result != aiReturn_SUCCESS) {
            path.free();
            return null;
        }

        String textPath = path.dataString().replace("\\", "/");

        // --- 核心修复：处理内嵌贴图 (Embedded Texture) ---
        // 在 glTF / glb 模型中，贴图通常被打包在文件里，路径以 * 开头，后面跟着索引（例如 *0, *1）
        if (textPath.startsWith("*")) {
            try {
                int texIndex = Integer.parseInt(textPath.substring(1));
                PointerBuffer texturesBuf = scene.mTextures();
                if (texturesBuf != null && texIndex < scene.mNumTextures()) {
                    AITexture aiTexture = AITexture.create(texturesBuf.get(texIndex));

                    // 获取内嵌贴图的字节数据
                    ByteBuffer buffer;
                    if (aiTexture.mHeight() == 0) {
                        // 1. 压缩格式：宽度 mWidth 是字节大小
                        int dataSize = aiTexture.mWidth();
                        // 使用 pcData().address() 获取内存起始地址，并创建 ByteBuffer
                        buffer = MemoryUtil.memByteBuffer(aiTexture.pcData().address(), dataSize);
                    } else {
                        // 2. 未压缩格式：ARGB 数据
                        int dataSize = aiTexture.mWidth() * aiTexture.mHeight() * 4;
                        buffer = MemoryUtil.memByteBuffer(aiTexture.pcData().address(), dataSize);
                    }

                    // 将 ByteBuffer 转换为 InputStream，复用之前的 AssetResource 加载管线
                    byte[] bytes = new byte[buffer.remaining()];
                    buffer.get(bytes);
                    ByteArrayInputStream bais = new ByteArrayInputStream(bytes);

                    AssetIdentifier id = new AssetIdentifier("EmbeddedTexture_" + texIndex);
                    AssetResource resource = new AssetResource("Memory", id, bais, null, 0);
                    Texture texture = new GLTexture(resource);

                    InternalLogger.info("Loaded embedded texture: " + textPath);
                    path.free();
                    return texture;
                }
            } catch (Exception e) {
                InternalLogger.error("Failed to load embedded texture: " + e.getMessage());
            }
        }
        // --- 如果不是内嵌贴图，则按之前的逻辑从硬盘加载 ---
        else {
            File textureFile = new File(modelDir, textPath);
            if (!textureFile.exists()) {
                textureFile = new File(modelDir, new File(textPath).getName());
            }

            if (textureFile.exists()) {
                try {
                    AssetIdentifier id = new AssetIdentifier(textureFile.getAbsolutePath());
                    java.io.FileInputStream fis = new java.io.FileInputStream(textureFile);
                    AssetResource resource = new AssetResource(
                            "LocalDisk", id, fis, null, textureFile.lastModified()
                    );
                    Texture texture = new GLTexture(resource);
                    InternalLogger.info("Texture bound from disk: " + textureFile.getName());
                    path.free();
                    return texture;
                } catch (Exception e) {
                    InternalLogger.error("Failed to bind texture: " + textureFile.getName() + " -> " + e.getMessage());
                }
            }
        }

        path.free();
        return null;
    }

    private static void buildBoneHierarchy(AIScene scene, Map<String, Integer> boneMap, List<BoneData> boneList) {
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

        InternalLogger.info("Building hierarchy with proper Inverse Bind Poses...");
        traverseNodeTree(Objects.requireNonNull(scene.mRootNode()), -1, new Matrix4f(), boneMap, boneList, offsetMatrices);
    }

    private static void traverseNodeTree(AINode node, int parentIdx, Matrix4f parentGlobalTransform,
                                         Map<String, Integer> boneMap, List<BoneData> boneList,
                                         Map<String, Matrix4f> offsetMatrices) {
        String name = node.mName().dataString();
        int currentIdx = boneList.size();

        Matrix4f localTransform = toJoml(node.mTransformation());
        Matrix4f globalTransform = new Matrix4f(parentGlobalTransform).mul(localTransform);

        Matrix4f ibp;
        if (offsetMatrices.containsKey(name)) {
            ibp = offsetMatrices.get(name);
        } else {
            ibp = new Matrix4f(globalTransform).invert();
        }

        boneMap.put(name, currentIdx);
        BoneData data = new BoneData(currentIdx, name, ibp);
        data.parentIndex = parentIdx;
        boneList.add(data);

        int childCount = node.mNumChildren();
        PointerBuffer children = node.mChildren();
        if (children != null) {
            for (int i = 0; i < childCount; i++) {
                traverseNodeTree(AINode.create(children.get(i)), currentIdx, globalTransform, boneMap, boneList, offsetMatrices);
            }
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

        Map<Integer, Integer> meshToNodeMap = new HashMap<>();
        mapMeshesToNodes(scene.mRootNode(), boneMap, meshToNodeMap);

        int vOffset = 0;
        for (int i = 0; i < meshCount; i++) {
            AIMesh m = AIMesh.create(meshes.get(i));
            PointerBuffer aiBones = m.mBones();

            if (aiBones != null && m.mNumBones() > 0) {
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
                int nodeId = meshToNodeMap.getOrDefault(i, -1);
                if (nodeId != -1) {
                    for (int v = 0; v < m.mNumVertices(); v++) {
                        fillVertexBoneData(allBoneIds, allWeights, vOffset + v, nodeId, 1.0f);
                    }
                }
            }
            vOffset += m.mNumVertices();
        }

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

            PointerBuffer channels = aiAnim.mChannels();
            for (int c = 0; c < channelCount; c++) {
                AINodeAnim channel = AINodeAnim.create(channels.get(c));
                String channelName = channel.mNodeName().dataString();

                int jIdx = boneMap.getOrDefault(channelName, -1);

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
                }
            }
            clips.put(animName, new AnimationClip(animName, duration, numJoints, jointDataList.toArray(new AnimationClip.JointData[0])));
        }
        return clips;
    }

    private static Keyframes.TranslationKey[] extractTranslationKeys(AINodeAnim c, double tps) {
        Keyframes.TranslationKey[] k = new Keyframes.TranslationKey[c.mNumPositionKeys()];
        for (int i = 0; i < k.length; i++) {
            AIVectorKey key = c.mPositionKeys().get(i);
            k[i] = new Keyframes.TranslationKey();
            k[i].time = (float) (key.mTime() / tps);
            k[i].value.set(key.mValue().x(), key.mValue().y(), key.mValue().z());
        }
        return k;
    }

    private static Keyframes.RotationKey[] extractRotationKeys(AINodeAnim c, double tps) {
        Keyframes.RotationKey[] k = new Keyframes.RotationKey[c.mNumRotationKeys()];
        for (int i = 0; i < k.length; i++) {
            AIQuatKey key = c.mRotationKeys().get(i);
            k[i] = new Keyframes.RotationKey();
            k[i].time = (float) (key.mTime() / tps);
            k[i].value.set(key.mValue().x(), key.mValue().y(), key.mValue().z(), key.mValue().w());
        }
        return k;
    }

    private static Keyframes.ScaleKey[] extractScaleKeys(AINodeAnim c, double tps) {
        Keyframes.ScaleKey[] k = new Keyframes.ScaleKey[c.mNumScalingKeys()];
        for (int i = 0; i < k.length; i++) {
            AIVectorKey key = c.mScalingKeys().get(i);
            k[i] = new Keyframes.ScaleKey();
            k[i].time = (float) (key.mTime() / tps);
            k[i].value.set(key.mValue().x(), key.mValue().y(), key.mValue().z());
        }
        return k;
    }

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

    // ============================================================
    // JSON 数据映射类 (用于配合 Gson 解析)
    // ============================================================
    private static class JsonModelData {
        public String modelName;
        public JsonSkeleton skeleton;
        public float[] vertices;
        public int[] indices;
        public JsonAnimation[] animations;

        public static class JsonSkeleton {
            public int[] parentIndices;
            public String[] jointNames;
            public float[][] inverseBindPoses;
        }

        public static class JsonAnimation {
            public String name;
            public float duration;
            public JsonJointData[] jointData;
        }

        public static class JsonJointData {
            public int jointIndex;
            public JsonKeyframeVector3[] translations;
            public JsonKeyframeVector4[] rotations;
            public JsonKeyframeVector3[] scales;
        }

        public static class JsonKeyframeVector3 {
            public float time;
            public Vector3f value;
        }

        public static class JsonKeyframeVector4 {
            public float time;
            public Quaternionf value;
        }
    }
}