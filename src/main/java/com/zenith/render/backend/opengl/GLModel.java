package com.zenith.render.backend.opengl;

import com.zenith.asset.AssetIdentifier;
import com.zenith.asset.AssetResource;
import com.zenith.common.utils.InternalLogger;
import com.zenith.render.*;
import com.zenith.render.backend.opengl.shader.StandardShader;
import com.zenith.render.backend.opengl.texture.GLTexture;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.PointerBuffer;
import org.lwjgl.assimp.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.assimp.Assimp.*;

public class GLModel extends Model {

    private String directory;
    private final List<Integer> meshMaterialIndices = new ArrayList<>();
    private static StandardShader shader;
    private final Map<String, BoneInfo> boneInfoMap = new HashMap<>();
    private int boneCounter = 0;
    private final Matrix4f[] boneMatrices = new Matrix4f[100]; // 对应Shader的100块骨架最大值

    private Node rootNode;
    private Matrix4f globalInverseTransform;

    private final Map<String, SkeletalAnimation> animations = new HashMap<>();
    private SkeletalAnimation currentAnimation;
    private float animationTime = 0.0f;

    private static class BoneInfo {
        int id;
        Matrix4f offset;
        BoneInfo(int id, Matrix4f offset) { this.id = id; this.offset = offset; }
    }

    private static class VertexWeight {
        int[] boneIds = new int[4];
        float[] weights = new float[4];
        int count = 0;
        void addWeight(int boneId, float weight) {
            if (count < 4) {
                boneIds[count] = boneId;
                weights[count] = weight;
                count++;
            }
        }
    }

    private static class Node {
        String name;
        Matrix4f transform;
        List<Node> children = new ArrayList<>();
    }

    private static class AnimChannel {
        String nodeName;
        double[] posTimes; Vector3f[] posKeys;
        double[] rotTimes; Quaternionf[] rotKeys;
        double[] sclTimes; Vector3f[] sclKeys;
    }

    private static class SkeletalAnimation {
        String name;
        double duration;
        double ticksPerSecond;
        Map<String, AnimChannel> channels = new HashMap<>();
    }
    // =========================================================

    public GLModel(String name, StandardShader shader) {
        super(name);
        this.shader = shader;
        for (int i = 0; i < boneMatrices.length; i++) {
            boneMatrices[i] = new Matrix4f().identity();
        }
    }

    @Override
    public void load(AssetResource resource) throws IOException {
        String pathStr = resource.getOrCreateLocalCachePath();
        File file = new File(pathStr);
        this.directory = file.getParent() + File.separator;

        // 加入 aiProcess_LimitBoneWeights 限制每顶点最多4块骨骼参与蒙皮
        AIScene scene = aiImportFile(pathStr,
                aiProcess_Triangulate |
                        aiProcess_FlipUVs |
                        aiProcess_JoinIdenticalVertices |
                        aiProcess_GenSmoothNormals |
                        aiProcess_FixInfacingNormals |
                        aiProcess_LimitBoneWeights
        );

        if (scene == null || scene.mRootNode() == null) {
            throw new IOException("Assimp 导入失败: " + aiGetErrorString());
        }

        // 解析节点层级数 (必须先于动画和骨骼)
        rootNode = buildNodeTree(scene.mRootNode());
        globalInverseTransform = new Matrix4f(rootNode.transform).invert();

        // 解析动画
        processAnimations(scene);

        processMaterials(scene);
        processNode(scene.mRootNode(), scene);

        InternalLogger.info(String.format("Model '%s' loaded: %d meshes, %d materials, %d bones, %d animations.",
                name, meshes.size(), materials.size(), boneInfoMap.size(), animations.size()));
    }

    private void processNode(AINode node, AIScene scene) {
        for (int i = 0; i < node.mNumMeshes(); i++) {
            int meshIndex = node.mMeshes().get(i);
            AIMesh aiMesh = AIMesh.create(scene.mMeshes().get(meshIndex));

            meshes.add(processMesh(aiMesh));
            meshMaterialIndices.add(aiMesh.mMaterialIndex());
        }

        for (int i = 0; i < node.mNumChildren(); i++) {
            processNode(AINode.create(node.mChildren().get(i)), scene);
        }
    }

    private Mesh processMesh(AIMesh aiMesh) {
        List<Float> vertices = new ArrayList<>();
        int numVertices = aiMesh.mNumVertices();

        // 为每个顶点分配骨骼权重数组
        VertexWeight[] vwArray = new VertexWeight[numVertices];
        for (int i = 0; i < numVertices; i++) vwArray[i] = new VertexWeight();

        // 提取骨骼信息
        if (aiMesh.mBones() != null) {
            for (int i = 0; i < aiMesh.mNumBones(); i++) {
                AIBone bone = AIBone.create(aiMesh.mBones().get(i));
                String boneName = bone.mName().dataString();

                int boneId;
                if (!boneInfoMap.containsKey(boneName)) {
                    boneId = boneCounter++;
                    boneInfoMap.put(boneName, new BoneInfo(boneId, toMatrix4f(bone.mOffsetMatrix())));
                } else {
                    boneId = boneInfoMap.get(boneName).id;
                }

                for (int j = 0; j < bone.mNumWeights(); j++) {
                    AIVertexWeight vw = bone.mWeights().get(j);
                    vwArray[vw.mVertexId()].addWeight(boneId, vw.mWeight());
                }
            }
        }

        int numFaces = aiMesh.mNumFaces();
        for (int i = 0; i < numFaces; i++) {
            AIFace face = aiMesh.mFaces().get(i);

            for (int j = 0; j < face.mNumIndices(); j++) {
                int index = face.mIndices().get(j);

                // Pos
                AIVector3D pos = aiMesh.mVertices().get(index);
                vertices.add(pos.x()); vertices.add(pos.y()); vertices.add(pos.z());

                // Normal
                if (aiMesh.mNormals() != null) {
                    AIVector3D normal = aiMesh.mNormals().get(index);
                    vertices.add(normal.x()); vertices.add(normal.y()); vertices.add(normal.z());
                } else {
                    vertices.add(0.0f); vertices.add(1.0f); vertices.add(0.0f);
                }

                // UV
                if (aiMesh.mTextureCoords(0) != null) {
                    AIVector3D uv = aiMesh.mTextureCoords(0).get(index);
                    vertices.add(uv.x()); vertices.add(uv.y());
                } else {
                    vertices.add(0.0f); vertices.add(0.0f);
                }

                // Color
                vertices.add(1.0f); vertices.add(1.0f); vertices.add(1.0f); vertices.add(1.0f);

                // 【核心修复】：追加骨骼 ID 和 骨骼权重
                VertexWeight vw = vwArray[index];
                vertices.add((float)vw.boneIds[0]); vertices.add((float)vw.boneIds[1]);
                vertices.add((float)vw.boneIds[2]); vertices.add((float)vw.boneIds[3]);

                vertices.add(vw.weights[0]); vertices.add(vw.weights[1]);
                vertices.add(vw.weights[2]); vertices.add(vw.weights[3]);
            }
        }

        float[] vData = new float[vertices.size()];
        for (int i = 0; i < vertices.size(); i++) vData[i] = vertices.get(i);

        VertexLayout layout = new VertexLayout();
        layout.pushFloat("aPos", 3);
        layout.pushFloat("aNormal", 3);
        layout.pushFloat("aTexCoord", 2);
        layout.pushFloat("aColor", 4);
        layout.pushFloat("aBoneIds", 4);  // 对应 Shader 的 location = 4
        layout.pushFloat("aWeights", 4);  // 对应 Shader 的 location = 5

        // 现在每个顶点包含 20 个 Float (3 + 3 + 2 + 4 + 4 + 4 = 20)
        GLMesh mesh = new GLMesh(vData.length / 20, layout);
        mesh.updateVertices(vData);
        return mesh;
    }

    private void processMaterials(AIScene scene) {
        int numMaterials = scene.mNumMaterials();
        if (numMaterials == 0) return;
        PointerBuffer materialBuffer = scene.mMaterials();

        for (int i = 0; i < numMaterials; i++) {
            AIMaterial aiMaterial = AIMaterial.create(materialBuffer.get(i));
            GLMaterial material = new GLMaterial(shader);

            AIString path = AIString.calloc();
            aiGetMaterialTexture(aiMaterial, aiTextureType_DIFFUSE, 0, path, (IntBuffer) null, null, null, null, null, null);
            String textureName = path.dataString();

            if (textureName != null && !textureName.isEmpty()) {
                String cleanPath = textureName.replace("./", "").replace(".\\", "");
                File textureFile = new File(directory + cleanPath);

                if (textureFile.exists()) {
                    try (FileInputStream fis = new FileInputStream(textureFile)) {
                        AssetResource res = new AssetResource(textureName,
                                new AssetIdentifier("model", textureName), fis, null);
                        GLTexture tex = new GLTexture(res);
                        material.setTexture("u_Texture", tex);
                    } catch (Exception e) {
                        InternalLogger.error("Failed to load texture: " + textureName + " | " + e.getMessage());
                    }
                }
            }
            materials.add(material);
        }
    }

    // ================= 动画与骨骼系统具体实现 =================
    private void processAnimations(AIScene scene) {
        int numAnims = scene.mNumAnimations();
        if (numAnims == 0) return;
        PointerBuffer animBuffer = scene.mAnimations();

        for (int i = 0; i < numAnims; i++) {
            AIAnimation aiAnim = AIAnimation.create(animBuffer.get(i));
            SkeletalAnimation anim = new SkeletalAnimation();
            anim.name = aiAnim.mName().dataString();
            anim.duration = aiAnim.mDuration();
            anim.ticksPerSecond = aiAnim.mTicksPerSecond() != 0 ? aiAnim.mTicksPerSecond() : 25.0;

            for (int j = 0; j < aiAnim.mNumChannels(); j++) {
                AINodeAnim aiNodeAnim = AINodeAnim.create(aiAnim.mChannels().get(j));
                AnimChannel channel = new AnimChannel();
                channel.nodeName = aiNodeAnim.mNodeName().dataString();

                // 解析位置帧
                int posCount = aiNodeAnim.mNumPositionKeys();
                channel.posTimes = new double[posCount];
                channel.posKeys = new Vector3f[posCount];
                for (int k = 0; k < posCount; k++) {
                    AIVectorKey key = aiNodeAnim.mPositionKeys().get(k);
                    channel.posTimes[k] = key.mTime();
                    channel.posKeys[k] = new Vector3f(key.mValue().x(), key.mValue().y(), key.mValue().z());
                }

                // 解析旋转帧
                int rotCount = aiNodeAnim.mNumRotationKeys();
                channel.rotTimes = new double[rotCount];
                channel.rotKeys = new Quaternionf[rotCount];
                for (int k = 0; k < rotCount; k++) {
                    AIQuatKey key = aiNodeAnim.mRotationKeys().get(k);
                    channel.rotTimes[k] = key.mTime();
                    channel.rotKeys[k] = new Quaternionf(key.mValue().x(), key.mValue().y(), key.mValue().z(), key.mValue().w());
                }

                // 解析缩放帧
                int sclCount = aiNodeAnim.mNumScalingKeys();
                channel.sclTimes = new double[sclCount];
                channel.sclKeys = new Vector3f[sclCount];
                for (int k = 0; k < sclCount; k++) {
                    AIVectorKey key = aiNodeAnim.mScalingKeys().get(k);
                    channel.sclTimes[k] = key.mTime();
                    channel.sclKeys[k] = new Vector3f(key.mValue().x(), key.mValue().y(), key.mValue().z());
                }

                anim.channels.put(channel.nodeName, channel);
            }
            animations.put(anim.name, anim);
        }
    }

    private Node buildNodeTree(AINode aiNode) {
        Node node = new Node();
        node.name = aiNode.mName().dataString();
        node.transform = toMatrix4f(aiNode.mTransformation());

        for (int i = 0; i < aiNode.mNumChildren(); i++) {
            node.children.add(buildNodeTree(AINode.create(aiNode.mChildren().get(i))));
        }
        return node;
    }

    private Matrix4f toMatrix4f(AIMatrix4x4 m) {
        return new Matrix4f(
                m.a1(), m.b1(), m.c1(), m.d1(),
                m.a2(), m.b2(), m.c2(), m.d2(),
                m.a3(), m.b3(), m.c3(), m.d3(),
                m.a4(), m.b4(), m.c4(), m.d4()
        );
    }

    @Override
    public List<String> getAnimationNames() {
        return new ArrayList<>(animations.keySet());
    }

    @Override
    public void playAnimation(String animationName) {
        if (animations.containsKey(animationName)) {
            currentAnimation = animations.get(animationName);
            animationTime = 0.0f;
        }
    }

    @Override
    public void update(float deltaTime) {
        if (currentAnimation == null || rootNode == null) return;

        animationTime += deltaTime * currentAnimation.ticksPerSecond;
        animationTime = (float) (animationTime % currentAnimation.duration);

        updateNodeHierarchy(animationTime, rootNode, new Matrix4f().identity());
    }

    private void updateNodeHierarchy(float animTime, Node node, Matrix4f parentTransform) {
        String nodeName = node.name;
        Matrix4f nodeTransform = new Matrix4f(node.transform);

        AnimChannel channel = currentAnimation.channels.get(nodeName);
        if (channel != null) {
            Vector3f pos = interpolatePos(animTime, channel);
            Quaternionf rot = interpolateRot(animTime, channel);
            Vector3f scl = interpolateScl(animTime, channel);
            nodeTransform.translationRotateScale(pos, rot, scl);
        }

        Matrix4f globalTransform = new Matrix4f(parentTransform).mul(nodeTransform);

        if (boneInfoMap.containsKey(nodeName)) {
            int boneId = boneInfoMap.get(nodeName).id;
            boneMatrices[boneId] = new Matrix4f(globalInverseTransform)
                    .mul(globalTransform)
                    .mul(boneInfoMap.get(nodeName).offset);
        }

        for (Node child : node.children) {
            updateNodeHierarchy(animTime, child, globalTransform);
        }
    }

    private Vector3f interpolatePos(float time, AnimChannel channel) {
        if (channel.posKeys.length == 1) return channel.posKeys[0];
        int index = 0;
        for(int i = 0; i < channel.posTimes.length - 1; i++) if (time < channel.posTimes[i+1]) { index = i; break; }
        int nextIndex = (index + 1) % channel.posKeys.length;
        float factor = (float) ((time - channel.posTimes[index]) / (channel.posTimes[nextIndex] - channel.posTimes[index]));
        return new Vector3f(channel.posKeys[index]).lerp(channel.posKeys[nextIndex], factor);
    }

    private Quaternionf interpolateRot(float time, AnimChannel channel) {
        if (channel.rotKeys.length == 1) return channel.rotKeys[0];
        int index = 0;
        for(int i = 0; i < channel.rotTimes.length - 1; i++) if (time < channel.rotTimes[i+1]) { index = i; break; }
        int nextIndex = (index + 1) % channel.rotKeys.length;
        float factor = (float) ((time - channel.rotTimes[index]) / (channel.rotTimes[nextIndex] - channel.rotTimes[index]));
        return new Quaternionf(channel.rotKeys[index]).slerp(channel.rotKeys[nextIndex], factor);
    }

    private Vector3f interpolateScl(float time, AnimChannel channel) {
        if (channel.sclKeys.length == 1) return channel.sclKeys[0];
        int index = 0;
        for(int i = 0; i < channel.sclTimes.length - 1; i++) if (time < channel.sclTimes[i+1]) { index = i; break; }
        int nextIndex = (index + 1) % channel.sclKeys.length;
        float factor = (float) ((time - channel.sclTimes[index]) / (channel.sclTimes[nextIndex] - channel.sclTimes[index]));
        return new Vector3f(channel.sclKeys[index]).lerp(channel.sclKeys[nextIndex], factor);
    }
    // =========================================================

    @Override
    public void draw() {
        // 如果有骨骼数据则传给 GPU
        if (!boneInfoMap.isEmpty()) {
            shader.setBones(boneMatrices);
        } else {
            shader.setBones(null);
        }

        for (int i = 0; i < meshes.size(); i++) {
            int matIdx = meshMaterialIndices.get(i);
            if (matIdx >= 0 && matIdx < materials.size()) {
                materials.get(matIdx).apply();
            }
            meshes.get(i).render();
        }
    }

    @Override
    public void dispose() {
        meshes.forEach(Mesh::dispose);
        materials.forEach(Material::dispose);
    }
}