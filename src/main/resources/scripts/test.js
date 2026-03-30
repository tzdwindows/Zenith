/**
 * Zenith World System - 电影级暴雨实现 (GPU Rain Simulation)
 */

var waterEntity;
var skyBoxMesh, skyShader, waterShader;
var rainMesh, rainShader; // 新增雨水渲染组件
var waterNormal;
var time = 0.0;

const RAIN_COUNT = 2500; // 雨丝数量

// 数学对象
var sunDir = new Vector3f(0.8, 0.3, 0.8).normalize();
var lightIntensity = new Vector3f(1.5, 1.5, 1.5);
var viewMatrix = new Matrix4f();
var projMatrix = new Matrix4f();
var camPosCached = new Vector3f();

function init() {
    Log.info("正在启动 GPU 暴雨模拟...");

    camera.getTransform().getPosition().set(0, 15, 60);

    let layout = MeshUtils.createStandardLayout();

    // 1. 初始化天空
    skyShader = GLShaderRegistry.get(GLShaderRegistry.SKY);
    let skyData = MeshUtils.generateSkyBoxData();
    skyBoxMesh = new GLMesh(skyData.length / 20, layout);
    skyBoxMesh.updateVertices(skyData);

    // 2. 初始化水体
    waterShader = new WaterShader();
    let waterData = MeshUtils.generatePlaneData(1000.0, 1000.0, 300, 300);
    waterEntity = new WaterEntity(new GLMesh(waterData.length / 20, layout), new GLMaterial(waterShader));
    waterEntity.getMesh().updateVertices(waterData);

    // 3. 初始化 GPU 雨丝 (参考 Test6.java 逻辑)
    rainShader = new RainShader();
    let rainData = generateRainData(RAIN_COUNT);
    rainMesh = new GLMesh(rainData.length / 20, layout);
    rainMesh.updateVertices(rainData);

    // 4. 加载资源
    try {
        waterNormal = new GLTexture(AssetResource.loadFromResources("textures/water/Water_0341normal.jpg"));
    } catch (e) {
        Log.warn("未找到水体法线，将使用程序化波纹");
    }
}

function update(deltaTime) {
    time += deltaTime; // 正常时间流速以匹配雨水下落
}

function renderScene() {
    viewMatrix.set(camera.getViewMatrix());
    projMatrix.set(camera.getProjection().getMatrix());
    camPosCached.set(camera.getTransform().getPosition());

    // 暴雨环境背景
    GL11.glClearColor(0.05, 0.08, 0.1, 1.0);
    GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

    // --- 1. 渲染天空 ---
    GL11.glDisable(GL11.GL_CULL_FACE);
    GL11.glDepthMask(false);
    GL11.glDepthFunc(GL11.GL_LEQUAL);

    skyShader.bind();
    let vNoT = new Matrix4f(viewMatrix);
    vNoT.m30(0); vNoT.m31(0); vNoT.m32(0);
    skyShader.setUniform("u_ViewProjection", new Matrix4f(projMatrix).mul(vNoT));
    skyShader.setUniform("u_SunDir", sunDir);
    skyBoxMesh.render();

    GL11.glDepthMask(true);
    GL11.glDepthFunc(GL11.GL_LESS);
}

function renderAfterOpaqueScene() {
    let sceneFBO = engine.getSceneFBO();
    if (!waterEntity || !sceneFBO) return;

    // --- 2. 渲染物理水面 (带雨滴冲击效果) ---
    waterShader.bind();

    // 绑定纹理防止乱码
    let sceneColorID = sceneFBO.getColorCopyTex();
    let sceneDepthID = sceneFBO.getDepthCopyTex();
    GL13.glActiveTexture(GL13.GL_TEXTURE0);
    GL11.glBindTexture(GL11.GL_TEXTURE_2D, sceneColorID);
    GL13.glActiveTexture(GL13.GL_TEXTURE1);
    GL11.glBindTexture(GL11.GL_TEXTURE_2D, sceneDepthID);

    waterShader.setUniform("u_SceneColor", 0);
    waterShader.setUniform("u_SceneDepth", 1);
    waterShader.setUniform("u_ReflectionTexture", 0);

    if (waterNormal != null) waterShader.bindWaterNormal(waterNormal);

    waterShader.setMatrices(projMatrix, viewMatrix);
    waterShader.setUniform("u_ViewProjection", new Matrix4f(projMatrix).mul(viewMatrix));
    waterShader.setUniform("u_Model", new Matrix4f().translate(0, 0, 0));
    waterShader.setScreenSize(window.getWidth(), window.getHeight());

    // 更新水体：高强度下雨 (0.9)
    waterShader.updateUniforms(
        camPosCached,
        sunDir,
        lightIntensity,
        new Color(0.01, 0.05, 0.12, 1.0),
        new Color(0.1, 0.45, 0.6, 1.0),
        time,
        0.9
    );
    waterShader.setSplashes(null);

    GL11.glDisable(GL11.GL_BLEND); // 水面主要靠折射
    GL11.glDepthMask(false);
    waterEntity.getMesh().render();

    // --- 3. 渲染 GPU 雨丝 (核心新增) ---
    GL11.glEnable(GL11.GL_BLEND);
    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE); // 加法混合模式渲染雨丝

    rainShader.bind();
    rainShader.setUniform("u_ViewProjection", new Matrix4f(projMatrix).mul(viewMatrix));
    rainShader.setUniform("u_ViewPos", camPosCached);
    rainShader.setUniform("u_Time", time);
    rainShader.setUniform("u_Wind", new Vector2f(-0.5, -0.2)); // 侧风效果
    rainShader.setUniform("u_SunDir", sunDir);
    rainShader.setUniform("u_SunIntensity", lightIntensity);
    rainShader.setUniform("u_AmbientSkyColor", new Vector3f(0.2, 0.3, 0.4));

    rainMesh.render();

    // 状态清理
    GL11.glDepthMask(true);
    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    GL11.glEnable(GL11.GL_CULL_FACE);
}

/**
 * 生成 GPU 雨丝顶点数据 (JS 实现)
 */
function generateRainData(count) {
    let vertices = [];
    for (let i = 0; i < count; i++) {
        // 随机分布位置
        let rx = Math.random() * 100 - 50;
        let ry = Math.random() * 50;
        let rz = Math.random() * 100 - 50;
        let speed = 40.0 + Math.random() * 40.0;

        // 每个雨丝由 2 个三角形(4个顶点)组成的垂直平面
        // 结构：px, py, pz, nx, ny, nz, u, v, rx, ry, rz, speed ... (补齐到20个float)
        addRainVertex(vertices, -0.015,  1.5, 0, 0, 0, rx, ry, rz, speed, 0, 0);
        addRainVertex(vertices, -0.015, -1.5, 0, 0, 1, rx, ry, rz, speed, 0, 1);
        addRainVertex(vertices,  0.015, -1.5, 0, 1, 1, rx, ry, rz, speed, 1, 1);
        addRainVertex(vertices,  0.015,  1.5, 0, 1, 0, rx, ry, rz, speed, 1, 0);
    }

    // 生成索引索引
    let res = new Float32Array(count * 6 * 20);
    let ptr = 0;
    for (let i = 0; i < count; i++) {
        let base = i * 4;
        let indices = [base, base + 1, base + 2, base + 2, base + 3, base];
        for (let idx of indices) {
            for (let v = 0; v < 20; v++) {
                res[ptr++] = vertices[idx * 20 + v];
            }
        }
    }
    return res;
}

function addRainVertex(arr, px, py, pz, u, v, rx, ry, rz, speed, tx, ty) {
    arr.push(px, py, pz); // 局部偏移
    arr.push(0, 0, 1);    // 法线
    arr.push(u, v);       // UV
    arr.push(1, 1, 1, 1); // 颜色
    arr.push(rx, ry, rz, speed); // 实例属性: 随机位置和速度 (对应 aBoneIds 槽位)
    arr.push(0, 0, 0, 0); // 填充
}