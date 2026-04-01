/**
 * Zenith World System - 深夜火炬 (LightManager 统一管理版)
 * 修复版：支持镜面球光照 & 优化火炬视觉比例
 */

var skyBoxMesh = null;
var terrainMesh = null;
var sphereMesh = null;
var torchMesh = null;
var flameMesh = null;

var skyShader = null;
var terrainShader = null;
var mirrorShader = null;
var standardShader = null;
var totalTime = 0.0;

const RADIUS = 15.0;
const FIX_HEIGHT = 25.0;

// 引入 Java 类
var GL11 = Packages.org.lwjgl.opengl.GL11;
var GL13 = Packages.org.lwjgl.opengl.GL13;
var Vector3f = Packages.org.joml.Vector3f;
var Matrix4f = Packages.org.joml.Matrix4f;
var Color = Packages.com.zenith.common.math.Color;
var GLLight = Packages.com.zenith.render.backend.opengl.GLLight;
var LightManager = Packages.com.zenith.render.backend.opengl.LightManager;

function init() {
    Log.info("JS: 正在初始化深夜火炬版 (全光照支持版)...");
    camera.getTransform().getPosition().set(40, 45, 100);

    try {
        let layout = MeshUtils.createStandardLayout();

        // 1. 模型初始化
        sphereMesh = new GLMesh(MeshUtils.generateSphereData(RADIUS, 64, 64).length / 20, layout);
        sphereMesh.updateVertices(MeshUtils.generateSphereData(RADIUS, 64, 64));

        terrainMesh = new GLMesh(generatePlaneData(4000, 4000, 100, 100).length / 20, layout);
        terrainMesh.updateVertices(generatePlaneData(4000, 4000, 100, 100));

        skyBoxMesh = new GLMesh(generateSkyBoxData(1.0).length / 20, layout);
        skyBoxMesh.updateVertices(generateSkyBoxData(1.0));

        torchMesh = new GLMesh(generateCubeData(0.15, 1.8, 0.15).length / 20, layout);
        torchMesh.updateVertices(generateCubeData(0.15, 1.8, 0.15));

        flameMesh = new GLMesh(MeshUtils.generateSphereData(0.4, 16, 16).length / 20, layout);
        flameMesh.updateVertices(MeshUtils.generateSphereData(0.4, 16, 16));

        // 2. Shader 初始化
        skyShader = GLShaderRegistry.get(GLShaderRegistry.SKY);
        mirrorShader = new Packages.com.zenith.render.backend.opengl.shader.MirrorSurfaceShader();
        standardShader = new Packages.com.zenith.render.backend.opengl.shader.StandardShader();
        terrainShader = new Packages.com.zenith.render.backend.opengl.shader.TerrainShader();

    } catch (e) {
        Log.error(e);
    }
}

function update(deltaTime) {
    totalTime += deltaTime;
}

function renderScene() {
    if (terrainMesh == null || skyShader == null) return;

    let pMat = camera.getProjection().getMatrix();
    let vMat = camera.getViewMatrix();
    let camPos = camera.getTransform().getPosition();
    let vpMat = new Matrix4f(pMat).mul(vMat);
    let sunDir = new Vector3f(0.2, -0.6, 0.4).normalize(); // 稍微压低月光角度

    // --- 1. 渲染天空 ---
    GL11.glDepthMask(false);
    GL11.glDepthFunc(515);
    skyShader.bind();
    let viewNoTrans = new Matrix4f(vMat);
    viewNoTrans.m30(0); viewNoTrans.m31(0); viewNoTrans.m32(0);
    skyShader.setUniform("u_ViewProjection", new Matrix4f(pMat).mul(viewNoTrans));
    skyShader.setUniform("u_SunDir", sunDir);
    skyShader.setUniform("u_Time", totalTime);
    skyBoxMesh.render();
    GL11.glDepthFunc(513);
    GL11.glDepthMask(true);

    // --- 2. 使用 LightManager 准备光照 ---
    let right = new Vector3f(vMat.m00(), vMat.m10(), vMat.m20()).normalize();
    let up = new Vector3f(vMat.m01(), vMat.m11(), vMat.m21()).normalize();
    let forward = new Vector3f(-vMat.m02(), -vMat.m12(), -vMat.m22()).normalize();

    // 优化：火炬位置挪远一点，防止挡住屏幕 (forward * 2.5, up * -1.0)
    let torchPos = new Vector3f(camPos)
        .add(new Vector3f(forward).mul(2.2))
        .add(new Vector3f(right).mul(0.85))
        .add(new Vector3f(up).mul(-1.0));

    let lm = LightManager.get();
    lm.clear();

    // 火炬点光源
    let torchLight = new GLLight();
    torchLight.setType(1);
    torchLight.setPosition(torchPos);
    torchLight.setDirection(new Vector3f(0, -1, 0));
    torchLight.setColor(new Color(1.0, 0.45, 0.1, 1.0));
    let intensity = 45000.0 + Math.sin(totalTime * 25.0) * 6000.0;  // PBR 强度需要高
    torchLight.setIntensity(intensity);
    torchLight.setRange(180.0);
    lm.addLight(torchLight);

    // 平行光：月光
    let moonLight = new GLLight();
    moonLight.setType(0);
    moonLight.setDirection(sunDir);
    moonLight.setColor(new Color(0.15, 0.15, 0.35, 1.0));
    moonLight.setIntensity(0.8);
    moonLight.setAmbientStrength(0.02);
    lm.addLight(moonLight);

    // --- 3. 渲染地形 ---
    terrainShader.bind();
    lm.apply(terrainShader, camPos);
    terrainShader.setup(vpMat, new Matrix4f().identity(), camPos);

    terrainShader.setUniform("u_TerrainMat.hasGrassMap", 0.0);
    terrainShader.setUniform("u_TerrainMat.hasRockMap", 0.0);
    terrainShader.setUniform("u_TerrainMat.hasNormalMap", 0.0);
    terrainShader.setUniform("u_TerrainMat.uvScale", 1.0);
    terrainShader.setUniform("u_TerrainMat.grassColor", new Vector3f(1.0, 1.0, 1.0));
    terrainShader.setUniform("u_TerrainMat.rockColor", new Vector3f(1.0, 1.0, 1.0));
    terrainShader.setUniform("u_TerrainMat.snowColor", new Vector3f(1.0, 1.0, 1.0));
    terrainShader.setUniform("u_TerrainMat.amplitude", 10.0); // 稍微加点起伏
    terrainShader.setUniform("u_TerrainMat.frequency", 0.005);
    terrainShader.setUniform("u_TerrainMat.snowHeight", 1000.0);

    terrainMesh.render();

    // --- 4. 渲染手持火炬 ---
    let bobbing = Math.sin(totalTime * 2.5) * 0.02;
    let modelTorchM = new Matrix4f()
        .translation(torchPos.x, torchPos.y + bobbing, torchPos.z)
        .rotate(camera.getTransform().getRotation());

    standardShader.bind();
    lm.apply(standardShader, camPos); // 让火炬柄也被月光照亮

    // 火炬柄
    standardShader.setup(vpMat, modelTorchM, new Color(0.15, 0.08, 0.03, 1.0));
    standardShader.setEmissive(false, new Vector3f(0, 0, 0), 0.0);
    torchMesh.render();

    // 火焰球 (缩小比例，降低自发光，防止变成巨大黄球)
    let flameM = new Matrix4f(modelTorchM).translate(0, 0.95, 0).scale(0.3);
    standardShader.setup(vpMat, flameM, new Color(1.0, 0.8, 0.2, 1.0));
    standardShader.setEmissive(true, new Vector3f(1.0, 0.5, 0.1), 12.0);
    flameMesh.render();

    standardShader.setEmissive(false, new Vector3f(0, 0, 0), 0.0);
}

function renderAfterOpaqueScene() {
    if (mirrorShader == null || sphereMesh == null || sceneFBO == null) return;

    let vMat = camera.getViewMatrix();
    let pMat = camera.getProjection().getMatrix();
    let camPos = camera.getTransform().getPosition();
    let vpMat = new Matrix4f(pMat).mul(vMat);

    GL13.glActiveTexture(GL13.GL_TEXTURE0);
    GL11.glBindTexture(GL11.GL_TEXTURE_2D, sceneFBO.getColorCopyTex());

    mirrorShader.bind();

    // 【关键新增】：给镜面球应用光照系统，这样它才能反射火炬的高光
    mirrorShader.applyLights(LightManager.get(), camPos);

    let modelM = new Matrix4f().identity().translate(0, FIX_HEIGHT, 0);
    modelM.rotateY(totalTime * 0.5);

    // 基础颜色改为纯白，让 PBR 表现更明显
    mirrorShader.setup(
        vpMat,
        vMat,
        modelM,
        new Color(0.95, 0.95, 0.95, 1.0),
        1920, 1080,     // 这里最好换成你的真实视口大小
        0.08,           // roughness
        1.0,            // metallic
        totalTime
    );
    //mirrorShader.setUniform("u_Roughness", 0.25);
    //mirrorShader.setUniform("u_Metallic", 1.0);
    mirrorShader.applyLights(LightManager.get(), camPos);
    sphereMesh.render();
}

// --- 辅助函数保持不变 ---
function generateCubeData(w, h, d) {
    let x = w / 2, y = h / 2, z = d / 2;
    let v = [
        -x,-y,z,  x,-y,z,  x,y,z,  -x,y,z,
        -x,-y,-z, -x,y,-z, x,y,-z, x,-y,-z,
        -x,y,z,  x,y,z,  x,y,-z, -x,y,-z,
        -x,-y,z, -x,-y,-z, x,-y,-z, x,-y,z,
        x,-y,z,  x,-y,-z, x,y,-z, x,y,z,
        -x,-y,z, -x,y,z, -x,y,-z, -x,-y,-z
    ];
    let indices = [
        0,1,2, 0,2,3, 4,5,6, 4,6,7, 8,9,10, 8,10,11,
        12,13,14, 12,14,15, 16,17,18, 16,18,19, 20,21,22, 20,22,23
    ];
    let res = [];
    for (let i = 0; i < indices.length; i++) {
        let idx = indices[i];
        res.push(v[idx * 3], v[idx * 3 + 1], v[idx * 3 + 2]);
        res.push(0, 1, 0, 0, 0);
        res.push(1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0);
    }
    return res;
}

function generatePlaneData(w, d, xn, zn) {
    let vertices = [];
    for (let z = 0; z <= zn; z++) {
        for (let x = 0; x <= xn; x++) {
            vertices.push((x / xn) * w - w / 2.0, 0.0, (z / zn) * d - d / 2.0);
            vertices.push(0.0, 1.0, 0.0, x / xn, z / zn);
            vertices.push(1.0, 1.0, 1.0, 1.0, 0, 0, 0, 0, 0, 0, 0, 0);
        }
    }
    let indices = [];
    for (let z = 0; z < zn; z++) {
        for (let x = 0; x < xn; x++) {
            let s = z * (xn + 1) + x;
            indices.push(s, s + xn + 1, s + 1, s + xn + 1, s + xn + 2, s + 1);
        }
    }
    let res = [];
    for (let i = 0; i < indices.length; i++) {
        let idx = indices[i];
        for (let j = 0; j < 20; j++) res.push(vertices[idx * 20 + j]);
    }
    return res;
}

function generateSkyBoxData(size) {
    let c = [
        -1,1,-1, -1,-1,-1, 1,-1,-1, 1,-1,-1, 1,1,-1, -1,1,-1,
        -1,-1,1, -1,-1,-1, -1,1,-1, -1,1,-1, -1,1,1, -1,-1,1,
        1,-1,-1, 1,-1,1, 1,1,1, 1,1,1, 1,1,-1, 1,-1,-1,
        -1,-1,1, -1,1,1, 1,1,1, 1,1,1, 1,-1,1, -1,-1,1,
        -1,1,-1, 1,1,-1, 1,1,1, 1,1,1, -1,1,1, -1,1,-1,
        -1,-1,-1, -1,-1,1, 1,-1,-1, 1,-1,-1, -1,-1,1, 1,-1,1
    ];
    let res = [];
    for (let i = 0; i < c.length / 3; i++) {
        res[i * 20] = c[i * 3] * size;
        res[i * 20 + 1] = c[i * 3 + 1] * size;
        res[i * 20 + 2] = c[i * 3 + 2] * size;
        res[i * 20 + 3] = 0; res[i * 20 + 4] = 1; res[i * 20 + 5] = 0;
        res[i * 20 + 6] = 0; res[i * 20 + 7] = 0;
        for (let j = 8; j < 20; j++) res[i * 20 + j] = 0.0;
    }
    return res;
}

function asyncLoad() {}
function onBufferToScreen(realDeltaTime, screenShader) {}