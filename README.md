# Zenith Engine

Zenith Engine 是一个基于 Java 的高性能游戏引擎，提供完整的 2D/3D 渲染、音频处理、UI 系统、实体组件架构和脚本支持。

## 目录

- [项目概述](#项目概述)
- [核心特性](#核心特性)
- [技术栈](#技术栈)
- [系统要求](#系统要求)
- [快速开始](#快速开始)
- [项目结构](#项目结构)
- [构建说明](#构建说明)
- [API 文档](#api-文档)
- [依赖管理](#依赖管理)
- [许可证](#许可证)

## 项目概述

Zenith Engine 采用现代化的游戏引擎架构设计，基于 OpenGL 渲染后端，集成了先进的物理模拟、动态脚本系统和嵌入式 Web 浏览器功能。引擎采用组件化设计模式，提供灵活的扩展机制和高效的资源管理系统。

## 核心特性

### 渲染系统
- 基于 OpenGL 的高性能渲染管线
- PBR（基于物理的渲染）材质系统
- 动态光照系统（最多 8 个动态光源）
- 视锥剔除和批处理优化
- 异步资源加载与进度显示
- 多渲染通道支持

### 音频系统
- 基于 OpenAL 的 3D 音频引擎
- 多分路混音器（音乐、音效、环境音、语音）
- 实时音量控制和音频效果（混响、音调变换）
- 空间音频定位和多普勒效应

### UI 系统
- 基于批处理的即时模式 UI 渲染
- 丰富的 UI 组件库（按钮、面板、文本、图像）
- 事件驱动的交互系统
- 支持 JCEF 嵌入式网页浏览器
- 输入法（IME）完整支持

### 实体组件系统
- 灵活的实体-组件架构
- 层级化的实体关系管理
- 多碰撞箱支持和部位伤害判定
- 状态机驱动的 AI 系统
- 基于反射的事件总线

### 脚本系统
- 基于 GraalJS 的 JavaScript 脚本支持
- ECMAScript 2022 标准兼容
- 热重载和自动更新检测
- 完整的 Java-JavaScript 互操作
- 安全的沙箱执行环境

### 开发工具
- 内置调试信息覆盖层
- 实时性能统计（Draw Calls、顶点数、三角形数）
- 详细的日志记录系统
- 跨平台支持（Windows、Linux、macOS）

## 技术栈

| 类别 | 技术 | 版本 |
|------|------|------|
| 编程语言 | Java | 17+ |
| 渲染后端 | OpenGL | 4.5+ |
| 窗口系统 | GLFW | 3.3.3 |
| 数学库 | JOML | 1.10.5 |
| 音频系统 | OpenAL | - |
| 物理引擎 | PhysX JNI | 2.7.2 |
| 脚本引擎 | GraalJS | 23.0.1 |
| UI 框架 | ImGui | 1.89.0 |
| Web 集成 | JCEF | 143.0.14 |
| 模型加载 | Assimp | 3.3.3 |
| 日志系统 | SLF4J + Logback | 2.0.9 / 1.4.14 |
| JSON 处理 | Gson | 2.10.1 |
| 构建工具 | Gradle | 8.x |

## 系统要求

### 最低配置
- **操作系统**: Windows 10 / Linux (Ubuntu 20.04+) / macOS 10.15+
- **Java**: JDK 17 或更高版本
- **显卡**: 支持 OpenGL 4.5 的 GPU
- **内存**: 4 GB RAM
- **存储**: 500 MB 可用空间

### 推荐配置
- **操作系统**: Windows 11 / Linux (Ubuntu 22.04+) / macOS 12+
- **Java**: JDK 21 LTS
- **显卡**: 支持 OpenGL 4.6 的独立显卡
- **内存**: 8 GB RAM
- **存储**: 1 GB 可用空间

## 快速开始

### 克隆仓库

```bash
git clone https://github.com/your-organization/zenith-engine.git
cd zenith-engine
```

### 构建项目

```bash
# Windows
gradlew.bat build

# Linux/macOS
./gradlew build
```

构建产物将输出到 `build/libs` 目录。

### 运行示例

```bash
# 运行测试程序
gradlew run

# 或直接执行 JAR
java -jar build/libs/zenith-engine-1.0-SNAPSHOT.jar
```

### 创建自定义引擎

```java
public class MyGame extends ZenithEngine {
    
    public MyGame(Window window) {
        super(window);
    }
    
    @Override
    protected void asyncLoad() {
        // 在后台线程加载资源
        setLoadingProgress(0.5f);
        // 加载纹理、模型等资源
    }
    
    @Override
    protected void init() {
        // 在主线程初始化游戏逻辑
        createEntities();
        setupLights();
    }
    
    @Override
    protected void update(float deltaTime) {
        // 每帧更新游戏逻辑
        handleInput();
        updateEntities(deltaTime);
    }
    
    public static void main(String[] args) {
        GLWindow window = new GLWindow("My Game", 1280, 720);
        MyGame game = new MyGame(window);
        game.start();
    }
}
```

## 项目结构

```
Zenith/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/zenith/
│   │   │       ├── animation/      # 动画系统
│   │   │       ├── asset/          # 资产管理
│   │   │       ├── audio/          # 音频系统
│   │   │       ├── common/         # 通用工具类
│   │   │       ├── core/           # 核心引擎
│   │   │       ├── logic/          # 游戏逻辑
│   │   │       │   ├── component/  # 组件系统
│   │   │       │   ├── event/      # 事件系统
│   │   │       │   ├── scene/      # 场景管理
│   │   │       │   ├── script/     # 脚本系统
│   │   │       │   └── state/      # 状态机
│   │   │       ├── render/         # 渲染系统
│   │   │       │   └── backend/    # 渲染后端
│   │   │       └── ui/             # UI 系统
│   │   │           ├── component/  # UI 组件
│   │   │           ├── event/      # UI 事件
│   │   │           ├── layout/     # 布局系统
│   │   │           └── render/     # UI 渲染
│   │   └── resources/              # 资源文件
│   └── test/                       # 测试代码
├── buildSrc/                       # Gradle 插件
├── ShaderCache/                    # 着色器缓存
├── TextureCache/                   # 纹理缓存
├── jcef-bundle/                    # JCEF 运行时
├── build.gradle                    # 构建配置
├── settings.gradle                 # 项目设置
├── API_DOCUMENTATION.md            # API 文档
└── DEPENDENCIES.md                 # 依赖清单
```

## 构建说明

### 构建任务

```bash
# 编译项目
gradlew compileJava

# 运行测试
gradlew test

# 打包并发布
gradlew build

# 生成依赖文档
gradlew generateDependencyDoc

# 清理构建产物
gradlew clean
```

### 依赖重构

Zenith Engine 使用自定义的 Gradle 插件进行依赖重构，以解决第三方库的类冲突问题。构建过程中会自动：

1. 分析依赖树中的类冲突
2. 根据 `ClassModifyConfiguration.xml` 移除冲突类
3. 生成补丁版本的 JAR 文件
4. 输出到 `build/libs` 目录

详细依赖信息请参考 `DEPENDENCIES.md`。

### 跨平台构建

引擎支持跨平台构建，Gradle 会自动检测操作系统并下载对应的原生库：

- **Windows**: `natives-windows`
- **Linux**: `natives-linux`
- **macOS (Intel)**: `natives-macos`
- **macOS (Apple Silicon)**: `natives-macos-arm64`

## API 文档

完整的 API 文档请参考 [API_DOCUMENTATION.md](API_DOCUMENTATION.md)，包含：

- 核心引擎 API
- 渲染系统 API
- 音频系统 API
- UI 系统 API
- 实体组件系统 API
- 事件系统 API
- 脚本系统 API
- 资源管理 API
- 设计模式和最佳实践

### 关键 API 示例

#### 创建实体

```java
Entity player = new PlayerEntity("Player");
player.addComponent(new MeshComponent(mesh, shader));
player.addComponent(new BoxCollider(0.5f, 1.8f, 0.5f));
player.getTransform().setPosition(0, 0, 0);
```

#### 事件订阅

```java
public class HealthListener {
    @Subscribe(priority = EventPriority.HIGH)
    public void onDamage(EntityDamagedEvent event) {
        System.out.println("Entity took damage: " + event.getDamage());
    }
}

SystemEventBus.getInstance().register(new HealthListener());
```

#### 音频播放

```java
AudioManager audioManager = new AudioManager();
audioManager.init();

int bufferId = AudioRegistry.getInstance().loadAudio("sounds/explosion.wav");
AudioSource source = new AudioSource(bufferId, false, false);
source.setGroup(AudioGroup.SFX, audioManager.getMixer());
source.setPosition(playerPosition);
source.play();
```

#### UI 创建

```java
UIScreen screen = new UIScreen();

UIButton button = new UIButton(100, 100, 200, 50, atlas, "button")
    .setLabel("Start Game")
    .setOnClick(() -> startGame())
    .setTooltip("Click to start");

screen.addComponent(button);
setUIScreen(screen);
```

## 依赖管理

### 主要依赖

| 依赖 | 用途 | 许可证 |
|------|------|--------|
| LWJGL 3 | OpenGL/OpenAL/GLFW 绑定 | BSD-3-Clause |
| JOML | 数学运算库 | MIT |
| GraalJS | JavaScript 脚本引擎 | Universal FOSS Exception |
| JCEF | Chromium 嵌入式框架 | BSD-3-Clause |
| PhysX JNI | 物理引擎绑定 | BSD-3-Clause |
| ImGui | 即时模式 GUI | MIT |
| Gson | JSON 序列化 | Apache-2.0 |
| SLF4J + Logback | 日志框架 | MIT / EPL-1.0 |

完整的依赖清单和许可证信息请参考 [DEPENDENCIES.md](DEPENDENCIES.md)。

### 依赖冲突解决

Zenith Engine 使用自定义的依赖重构机制来处理类冲突：

1. **检测阶段**: 扫描所有运行时依赖的类文件
2. **分析阶段**: 对比 `ClassModifyConfiguration.xml` 中的冲突规则
3. **重构阶段**: 从原始 JAR 中移除冲突类，生成补丁版本
4. **部署阶段**: 优先使用补丁版本，回退到原始版本

这种机制确保了多个第三方库可以安全共存，避免了常见的 `NoSuchMethodError` 和 `ClassNotFoundException` 问题。

## 性能优化

### 渲染优化
- 自动批处理相同材质的绘制调用
- 视锥剔除减少无效渲染
- 异步资源加载避免主线程阻塞
- 着色器和纹理缓存减少重复加载

### 内存管理
- 对象池模式减少 GC 压力
- 延迟加载非关键资源
- 智能资源引用计数和自动释放
- 纹理压缩减少显存占用

### CPU 优化
- 组件系统的数据局部性优化
- 事件总线的优先级队列减少不必要调用
- 脚本引擎的多实例隔离避免全局锁
- 音频混合器的增量更新减少计算开销

## 贡献指南

我们欢迎社区贡献！请遵循以下流程：

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/amazing-feature`)
3. 提交更改 (`git commit -m 'Add some amazing feature'`)
4. 推送到分支 (`git push origin feature/amazing-feature`)
5. 开启 Pull Request

### 代码规范
- 遵循 Java 命名约定
- 编写清晰的注释和文档
- 添加单元测试覆盖新功能
- 确保构建通过且无警告

## 问题反馈

如遇到问题，请在 GitHub Issues 中报告，并提供：

- 操作系统和 Java 版本
- 完整的错误日志
- 重现问题的步骤
- 相关的代码片段

## 许可证

Zenith Engine 采用 [MIT License](LICENSE) 开源协议。

第三方库的许可证请参考 [DEPENDENCIES.md](DEPENDENCIES.md)。

## 致谢

感谢以下开源项目的支持：

- LWJGL 团队提供的优秀 Java 绑定
- JOML 提供的高性能数学库
- GraalVM 团队的脚本引擎实现
- CEF 社区的浏览器集成方案
- 所有贡献者和用户

## 联系方式

- 项目主页: [GitHub Repository](https://github.com/your-organization/zenith-engine)
- 问题追踪: [GitHub Issues](https://github.com/your-organization/zenith-engine/issues)
- 文档: [API Documentation](API_DOCUMENTATION.md)

---

**版本**: 1.0-SNAPSHOT  
**最后更新**: 2026年5月  
**维护者**: tzdwindows 7
