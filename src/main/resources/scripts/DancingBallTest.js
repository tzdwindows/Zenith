/**
 * Zenith World System - 3D 物理动感小球 (物理 PBR & 摄像机聚光头灯版)
 */

var sphereMesh = null;
var basicShader = null;
var time = 0.0;

// 缓存数学对象
var modelMatrix = new Matrix4f();
var viewMatrix = new Matrix4f();
var projMatrix = new Matrix4f();
var viewProjMatrix = new Matrix4f();

// 动画配置
const BOUNCE_SPEED = 3.5;
const BOUNCE_HEIGHT = 15.0;
const RADIUS = 3.0;

function init() {
    Log.info("JS: 正在初始化 PBR 物理场景...");
    // 初始相机位置
    camera.getTransform().getPosition().set(0, 15, 50);
    camera.getTransform().setRotation(-10, 0, 0);

    try {
        let layout = MeshUtils.createStandardLayout();
        // 生成一个精细的球体
        let sphereData = MeshUtils.generateSphereData(RADIUS, 48, 48);
        sphereMesh = new GLMesh(sphereData.length / 20, layout);
        sphereMesh.updateVertices(sphereData);
        Log.info("JS: 高精度球体 Mesh 创建成功。");
    } catch (e) {
        Log.error("JS: Mesh 初始化失败: " + e);
    }
}

function update(deltaTime) {
    time += deltaTime;
}

function renderScene() {
    // 1. 环境准备：更深邃的背景色（利于观察灯光效果）
    GL11.glClearColor(0.02, 0.02, 0.03, 1.0);
    GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

    if (basicShader == null) {
        basicShader = GLShaderRegistry.get(GLShaderRegistry.STANDARD);
    }
    if (sphereMesh == null || basicShader == null) return;

    // 获取摄像机数据
    let camPos = camera.getTransform().getPosition();
    let camForward = camera.getForward(); // 使用我们在 Java 中新增的方法

    // 2. 准备矩阵
    viewMatrix.set(camera.getViewMatrix());
    projMatrix.set(camera.getProjection().getMatrix());
    viewProjMatrix.set(projMatrix).mul(viewMatrix);

    // 3. 物理模拟 (弹性形变逻辑保持不变)
    let sinVal = Math.sin(time * BOUNCE_SPEED);
    let cosVal = Math.cos(time * BOUNCE_SPEED);
    let yPos, scaleY = 1.0, scaleXZ = 1.0;

    if (sinVal >= 0) {
        yPos = sinVal * BOUNCE_HEIGHT + RADIUS;
        let stretch = Math.abs(cosVal) * 0.2;
        scaleY = 1.0 + stretch;
        scaleXZ = 1.0 - (stretch * 0.5);
    } else {
        let compression = Math.abs(sinVal);
        yPos = RADIUS - (compression * RADIUS * 0.6);
        scaleY = 1.0 - (compression * 0.5);
        scaleXZ = 1.0 + (compression * 0.3);
    }

    // 4. 应用模型变换
    modelMatrix.identity();
    modelMatrix.translate(0, yPos, 0);
    modelMatrix.scale(scaleXZ, scaleY, scaleXZ);
    modelMatrix.rotateY(time * 0.5);

    // 5. --- 核心灯光系统设置 ---
    basicShader.bind();
    basicShader.clearLights();

    // A. 模拟太阳光 (平行光)，提供基础可见度
    let sunDir = new Vector3f(-0.5, -1.0, -0.5);
    let sunColor = new Color(1.0, 1.0, 0.9, 1.0);
    let sunLight = GLLight.createDirectional(sunDir, sunColor, 0.8);
    sunLight.setAmbientStrength(0.05); // 极弱的环境光，增加阴影深度
    basicShader.addLight(sunLight);

    // B. 摄像机头灯 (聚光灯 - Spotlight)
    // 这是最真实的手电筒模拟
    let headLight = new GLLight();
    headLight.setType(2); // 2 = TYPE_SPOT
    headLight.setPosition(camPos);
    headLight.setDirection(camForward);
    headLight.setColor(new Color(1.0, 0.95, 0.8, 1.0)); // 偏暖色的白光
    headLight.setIntensity(40.0);    // PBR 强度
    headLight.setRange(150.0);       // 光照距离 150 单位
    headLight.setSpotAngle(15.0, 25.0); // 内圆锥 15度，外圆锥 25度（产生边缘软化）
    basicShader.addLight(headLight);

    // 6. --- 材质与 PBR 渲染 ---
    // 基础颜色 (紫色)，利用 Alpha 通道设置 PBR 金属度 (Metallic)
    // 提示：我们在 Shader 中将 u_TextColor.a 作为金属度
    let ballColor = new Color(0.6, 0.2, 0.9, 0.8); // 0.8 的金属度，很有质感

    basicShader.setup(viewProjMatrix, modelMatrix, ballColor);

    // 应用所有灯光并传入相机位置用于计算镜面高光 (Specular)
    basicShader.applyLights(camPos);

    basicShader.setUseTexture(false);

    // 7. --- 高级自发光 (Emissive) ---
    // 让球体在暗处看起来在发微弱的紫色光，增加科幻感
    // 参数：开启, 颜色向量, 强度
    let emissiveCol = new Vector3f(0.4, 0.1, 0.6);
    basicShader.setEmissive(true, emissiveCol, 1.2);

    // 8. 执行绘制
    sphereMesh.render();

    // 渲染完成后关闭自发光，防止干扰后续渲染
    basicShader.setEmissive(false);
}

function renderAfterOpaqueScene() {}
function onBufferToScreen(realDeltaTime, screenShader) {}