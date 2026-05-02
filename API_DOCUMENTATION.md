# Zenith 引擎 API 文档

##  目录

- [核心引擎](#核心引擎)
- [渲染系统](#渲染系统)
- [音频系统](#音频系统)
- [UI系统](#ui系统)
- [实体组件系统](#实体组件系统)
- [事件系统](#事件系统)
- [脚本系统](#脚本系统)
- [资源管理](#资源管理)

---

## 核心引擎

### ZenithEngine
**位置**: `com.zenith.core.ZenithEngine`

Zenith引擎的核心抽象类，负责管理整个游戏循环、渲染、输入和UI系统。

#### 主要功能
- **游戏循环管理**: 处理主循环、帧率控制和异步加载
- **渲染协调**: 管理3D场景和UI的渲染流程
- **输入处理**: 键盘、鼠标事件的捕获和分发
- **相机控制**: 第一人称相机系统，支持锁定/自由视角切换
- **调试工具**: 内置调试信息显示和ESC菜单

#### 关键方法
```java
// 启动引擎
public void start()

// 在主线程执行任务（用于多线程安全）
public void runOnMainThread(Runnable task)

// 设置加载进度 (0.0 - 1.0)
public void setLoadingProgress(float progress)

// 切换鼠标锁定模式
protected void setCursorMode(boolean locked)

// 设置当前UI屏幕
protected void setUIScreen(UIScreen screen)
```

#### 生命周期钩子
```java
// 异步加载阶段（在后台线程执行）
protected abstract void asyncLoad()

// 初始化阶段（在主线程执行）
protected abstract void init()

// 每帧更新逻辑
protected abstract void update(float deltaTime)
```

#### 配置属性
- `cameraSpeed`: 相机移动速度（默认20.0）
- `sprintMultiplier`: 冲刺速度倍率（默认4.0）
- `mouseSensitivity`: 鼠标灵敏度（默认0.12）
- `showDebug`: 是否显示调试信息

---

### Window
**位置**: `com.zenith.render.Window`

窗口接口，定义了渲染引擎与操作系统窗口的交互契约。

#### 接口定义
```java
public interface Window {
    void update();
    boolean shouldClose();
    int getWidth();
    int getHeight();
    long getNativeHandle();
    long getHandle();
    void setEventListener(WindowEventListener listener);
    void dispose();
}
```

#### WindowEventListener
窗口事件监听器接口，处理各种输入事件：
```java
interface WindowEventListener {
    void onKey(int key, int scancode, int action, int mods);
    void onChar(int codepoint);
    void onCursorPos(double xpos, double ypos);
    void onMouseButton(int button, int action, int mods);
    void onScroll(double xoffset, double yoffset);
    void onResize(int width, int height);
}
```

---

### Camera
**位置**: `com.zenith.render.Camera`

相机抽象类，管理观察者的视角和投影。

#### 核心功能
- **视图矩阵生成**: 将世界坐标转换为相机空间
- **投影控制**: 支持透视和正交投影
- **空间变换**: 位置、旋转、缩放管理

#### 关键方法
```java
// 获取视图矩阵
public abstract Matrix4f getViewMatrix()

// 让相机看向目标点
public abstract void lookAt(Vector3f target, Vector3f up)

// 获取投影矩阵
public Matrix4f getProjectionMatrix()

// 获取方向向量
public Vector3f getForward()
public Vector3f getUp()
public Vector3f getRight()

// 访问变换和投影组件
public Transform getTransform()
public Projection getProjection()
```

---

### RayTracingProvider
**位置**: `com.zenith.core.RayTracingProvider`

光线追踪提供者接口，用于实现高级渲染效果。

#### 接口定义
```java
public interface RayTracingProvider {
    void init(int width, int height);
    void buildAccelerationStructures(List<Mesh> meshes);
    void trace(SceneFramebuffer fbo, Camera camera);
    void dispose();
}
```

---

## 渲染系统

### Renderer
**位置**: `com.zenith.render.Renderer`

渲染器抽象基类，负责提交和绘制3D对象。

#### 核心方法
```java
// 设置视图投影矩阵
public abstract void setViewProjection(Matrix4f matrix)

// 提交网格进行渲染
public abstract void submit(Mesh mesh, Material material, Transform transform)

// 刷新渲染队列，执行GPU指令
public abstract void flush()

// 重置统计信息
public void resetStats()
```

#### 统计数据
```java
public int getDrawCalls()      // 绘制调用次数
public int getVertexCount()    // 顶点数量
public int getTriangleCount()  // 三角形数量
```

---

### Mesh
**位置**: `com.zenith.render.Mesh`

网格抽象类，表示3D几何体。

#### 核心方法
```java
// 执行绘制指令
public abstract void render()

// 释放资源
public abstract void dispose()

// 获取顶点数量
public int getVertexCount()
```

---

### Material
**位置**: `com.zenith.render.Material`

材质类，封装着色器及其参数。

#### 核心功能
- 管理着色器程序
- 存储纹理引用
- 处理颜色属性
- 支持文本渲染

#### 关键方法
```java
// 应用材质到GPU
public abstract void apply()

// 设置纹理
public void setTexture(String name, Texture texture)

// 设置颜色
public void setColor(Color color)

// 设置字体
public void setFont(Font font)

// 释放资源
public abstract void dispose()

// 获取器
public Shader getShader()
public Texture getTexture(String name)
public Color getColor()
public Font getFont()
```

---

### Shader
**位置**: `com.zenith.render.Shader`

着色器抽象类，管理GPU着色器程序。

#### Uniform设置接口
```java
public abstract void bind()
public abstract void unbind()

// 设置统一变量
public abstract void setUniform(String name, Color color)
public abstract void setUniform(String name, Vector2f vector)
public abstract void setUniform(String name, Vector3f vector)
public abstract void setUniform(String name, Vector4f vector)
public abstract void setUniform(String name, Matrix4f matrix)
public abstract void setUniform(String name, float value)
public abstract void setUniform(String name, boolean value)

// 查询uniform
public abstract boolean hasUniform(String name)
public abstract float getUniformFloat(String name)

public abstract void dispose()
```

---

### Texture
**位置**: `com.zenith.render.Texture`

纹理抽象类，管理GPU纹理资源。

#### 核心方法
```java
// 从资源加载纹理数据
public abstract void load(AssetResource resource) throws IOException

// 绑定到纹理单元
public abstract void bind(int slot)
public abstract void unbind()

// 释放资源
public abstract void dispose()

// 获取属性
public int getWidth()
public int getHeight()
public String getSourceName()
public abstract int getId()
```

---

### Renderable
**位置**: `com.zenith.render.Renderable`

可渲染对象接口，任何需要渲染的物体都应实现此接口。

#### 接口定义
```java
public interface Renderable {
    Mesh getMesh();              // 获取几何网格
    Material getMaterial();      // 获取材质
    Transform getTransform();    // 获取变换信息
}
```

---

### ResourceManager
**位置**: `com.zenith.render.ResourceManager`

资源管理器抽象类，负责加载和管理纹理、着色器等资源。

#### 核心方法
```java
// 加载纹理
public abstract Texture loadTexture(String path)

// 加载着色器
public abstract Shader loadShader(String name, String vertPath, String fragPath)

// 卸载所有资源
public static void unloadAll()
```

---

### LightManager
**位置**: `com.zenith.render.backend.opengl.LightManager`

灯光管理器，处理场景中的光源。

#### 核心功能
- 最多支持8个动态光源
- 支持多种灯光类型（点光源、聚光灯等）
- 自动将灯光数据传递给着色器

#### 关键方法
```java
// 获取单例实例
public static LightManager get()

// 清空所有灯光
public void clear()

// 添加灯光
public void addLight(GLLight light)

// 应用灯光到着色器
public void apply(GLShader shader, Vector3f viewPos)
```

---

### Viewport
**位置**: `com.zenith.render.Viewport`

视口类，定义渲染区域和宽高比计算。

#### 构造函数
```java
public Viewport(int width, int height)
public Viewport(int x, int y, int width, int height)
```

#### 核心方法
```java
// 更新视口尺寸
public void set(int x, int y, int width, int height)

// 获取属性
public int getX(), getY()
public int getWidth(), getHeight()
public float getAspectRatio()
```

---

## 音频系统

### AudioManager
**位置**: `com.zenith.audio.AudioManager`

音频管理器，负责OpenAL设备的初始化和生命周期管理。

#### 核心功能
- OpenAL设备初始化
- 音频上下文管理
- 声源注册和追踪
- 分路音量控制
- 3D音频监听者同步

#### 关键方法
```java
// 初始化音频系统
public void init()

// 注册声源（自动响应混音器变化）
public void registerSource(AudioSource source)

// 取消注册声源
public void unregisterSource(AudioSource source)

// 设置分路音量
public void setGroupVolume(AudioGroup group, float volume)

// 更新监听者位置（与相机同步）
public void updateListener(Camera camera)

// 清理资源
public void cleanup()

// 获取混音器
public AudioMixer getMixer()
```

---

### AudioSource
**位置**: `com.zenith.audio.AudioSource`

声源类，封装OpenAL Source功能。

#### 构造函数
```java
public AudioSource(int bufferId, boolean loop, boolean relative)
```

#### 核心方法
```java
// 设置音频分路
public void setGroup(AudioGroup group, AudioMixer mixer)

// 设置局部音量（受混音器影响）
public void setGain(float gain, AudioMixer mixer)

// 设置3D位置
public void setPosition(Vector3f pos)

// 设置速度（用于多普勒效应）
public void setVelocity(Vector3f vel)

// 设置音调
public void setPitch(float pitch)

// 播放控制
public void play()
public void pause()
public void stop()
public boolean isPlaying()

// 添加音效槽位和滤波器
public void addEffectSlot(int slotId)
public void addFilter(int filterId)

// 清理资源
public void cleanup()
```

#### 属性
- `group`: 音频分路（默认SFX）
- `localGain`: 局部音量增益（0.0-1.0）

---

### AudioMixer
**位置**: `com.zenith.audio.AudioMixer`

音频混音器，管理不同音频分路的音量。

#### 核心功能
- 主音量控制
- 分路音量管理（音乐、音效、环境音等）
- 实时音量混合计算

#### 关键方法
```java
// 设置分路音量
public void setVolume(AudioGroup group, float volume)

// 获取分路输出增益
public float getOutputGain(AudioGroup group)
```

---

### AudioGroup
**位置**: `com.zenith.audio.AudioGroup`

音频分路枚举，定义不同的音频类别。

#### 预定义分路
- `MASTER`: 主音量
- `MUSIC`: 背景音乐
- `SFX`: 音效
- `AMBIENT`: 环境音
- `VOICE`: 语音

---

### AudioRegistry
**位置**: `com.zenith.audio.AudioRegistry`

音频注册表，管理音频资源的加载和缓存。

#### 核心方法
```java
// 加载音频文件
public int loadAudio(String path)

// 获取音频缓冲区ID
public int getBufferId(String path)

// 清理资源
public void cleanup()
```

---

### AudioEffect
**位置**: `com.zenith.audio.AudioEffect`

音频效果基类，用于实现各种音频处理效果。

#### 子类
- `ReverbEffect`: 混响效果
- `PitchShifterEffect`: 音调变换效果

---

## UI系统

### UIScreen
**位置**: `com.zenith.ui.UIScreen`

UI屏幕类，管理一组UI组件的容器。

#### 核心功能
- 组件管理和渲染
- 事件分发（鼠标、键盘）
- 模态支持
- 可见性控制

#### 关键方法
```java
// 添加组件
public void addComponent(UIComponent component)

// 更新所有组件
public void update(float deltaTime)

// 渲染所有组件
public void render(UIRenderContext ctx)

// 事件处理
public boolean onMouseMove(float mx, float my)
public boolean onMouseButton(int button, int action, float mx, float my)
public boolean onKey(int key, int scancode, int action, int mods)
public boolean onChar(int codepoint)
public boolean onScroll(double xoffset, double yoffset)

// 可见性控制
public void setVisible(boolean visible)
public boolean isVisible()

// 是否为模态屏幕（拦截底层事件）
public boolean isModal()
```

---

### UIComponent
**位置**: `com.zenith.ui.component.UIComponent`

UI组件抽象基类，所有UI元素的父类。

#### 构造函数
```java
public UIComponent(float x, float y, float width, float height)
```

#### 核心方法
```java
// 渲染组件（由框架调用）
public final void render(UIRenderContext ctx)

// 子类实现的具体渲染逻辑
protected abstract void onRender(UIRenderContext ctx)

// 更新逻辑
public void update(float deltaTime)

// 事件处理
public boolean onMouseMove(float mx, float my)
public boolean onMouseButton(int button, int action, float mx, float my)
public boolean onKey(int key, int scancode, int action, int mods)
public boolean onChar(int codepoint)

// 边界和可见性
public Rectf getBounds()
public void setVisible(boolean visible)
public boolean isVisible()

// 父子关系
public UIComponent getParent()
```

#### 属性
- `bounds`: 组件的位置和尺寸
- `visible`: 可见性标志
- `backgroundColor`: 背景颜色（默认透明）
- `parent`: 父组件引用

---

### UIButton
**位置**: `com.zenith.ui.component.UIButton`

按钮组件，支持点击、悬停、按下状态。

#### 构造函数
```java
public UIButton(float x, float y, float width, float height,
                TextureAtlas atlas, String spriteName)
```

#### 链式配置方法
```java
// 设置边框
public UIButton setBorderEnabled(boolean show)

// 设置背景扩展（解决贴图填充问题）
public UIButton setBgExpand(float expandX, float expandY)

// 设置标签文本
public UIButton setLabel(String text)

// 设置点击回调
public UIButton setOnClick(Runnable action)

// 设置提示文本
public UIButton setTooltip(String text)

// 设置按下时的缩放比例
public UIButton setPressedScale(float scale)

// 设置不同状态的颜色
public UIButton setColors(Color normal, Color hover, Color pressed)
```

#### 外观配置
- `normalColor`: 正常状态颜色
- `hoverColor`: 悬停状态颜色
- `pressedColor`: 按下状态颜色
- `borderColor`: 边框颜色
- `borderWidth`: 边框宽度
- `showBorder`: 是否显示边框

---

### HTMLComponent
**位置**: `com.zenith.ui.component.HTMLComponent`

HTML组件，基于JCEF嵌入网页浏览器。

#### 构造函数
```java
public HTMLComponent(CefClient client, String url, 
                     float x, float y, float width, float height)
```

#### 核心功能
- 完整的网页浏览支持
- 鼠标和键盘交互
- 输入法支持（IME）
- 拖拽和多键同时按下支持
- 新窗口拦截

#### 关键方法
```java
// 导航到URL
public void navigateTo(String url)

// 执行JavaScript
public void executeJavaScript(String script)

// 调整大小
public void resize(int width, int height)

// 获取底层浏览器实例
public CefBrowser getBrowser()
```

---

### UITextComponent
**位置**: `com.zenith.ui.component.UITextComponent`

文本组件，用于显示文字。

#### 构造函数
```java
public UITextComponent(float x, float y, String text)
```

#### 配置方法
```java
// 设置文本内容
public UITextComponent setText(String text)

// 设置字体大小
public UITextComponent setFontSize(float size)

// 设置文本颜色
public UITextComponent setColor(Color color)

// 设置对齐方式
public UITextComponent setAlignment(TextAlignment alignment)
```

---

### UIImageComponent
**位置**: `com.zenith.ui.component.UIImageComponent`

图像组件，用于显示图片。

#### 构造函数
```java
public UIImageComponent(float x, float y, float width, float height,
                        TextureAtlas atlas, String spriteName)
```

#### 配置方法
```java
// 设置图像
public void setImage(TextureAtlas atlas, String spriteName)

// 设置颜色叠加
public void setColor(Color color)

// 设置旋转角度
public void setRotation(float degrees)
```

---

### UIContainer
**位置**: `com.zenith.ui.component.UIContainer`

容器组件，可以包含多个子组件。

#### 核心方法
```java
// 添加子组件
public void addChild(UIComponent child)

// 移除子组件
public void removeChild(UIComponent child)

// 获取所有子组件
public List<UIComponent> getChildren()
```

---

### UIPanel
**位置**: `com.zenith.ui.component.UIPanel`

面板组件，带背景和边框的容器。

#### 特性
- 可配置的背景颜色
- 可选的边框
- 支持子组件布局
- 圆角支持

---

### UIButtonListener
**位置**: `com.zenith.ui.event.UIButtonListener`

按钮事件监听器接口。

#### 接口定义
```java
public interface UIButtonListener {
    void onHoverEnter(UIButton button);   // 鼠标进入
    void onHoverExit(UIButton button);    // 鼠标离开
    void onPress(UIButton button);        // 按下
    void onRelease(UIButton button);      // 释放
    void onClick(UIButton button);        // 点击（按下并释放）
}
```

---

### UIRenderContext
**位置**: `com.zenith.ui.render.UIRenderContext`

UI渲染上下文，提供绘图API。

#### 核心方法
```java
// 开始/结束渲染批次
public void begin(int width, int height)
public void end()

// 变换栈操作
public void pushTransform()
public void popTransform()
public void pushTransform(float x, float y)
public void translate(float x, float y)
public void scale(float sx, float sy)
public void rotate(float angle)

// 绘图命令
public void drawRect(Rectf rect, Color color)
public void drawRectOutline(float x, float y, float w, float h, 
                            float borderWidth, Color color)
public void drawSprite(TextureAtlas atlas, String spriteName,
                       float x, float y, float w, float h, Color color)
public void drawText(String text, float x, float y, Color color)
public void drawTooltip(String text, float x, float y)

// 字体设置
public void setFont(Font font)
public Font getFont()
```

---

## 实体组件系统

### Entity
**位置**: `com.zenith.logic.scene.entity.Entity`

实体基类，游戏世界中所有对象的基礎。

#### 核心功能
- 唯一标识符（UUID）
- 空间变换（位置、旋转、缩放）
- 组件系统
- 层级关系（父子实体）
- 碰撞检测
- AI控制
- 生命值和伤害系统

#### 构造函数
```java
public Entity(String name)
```

#### 生命周期方法
```java
// 创建时调用
public abstract void onCreate()

// 每帧更新
public void onUpdate(float deltaTime)

// 销毁时调用
public void onDestroy()
```

#### 组件管理
```java
// 添加组件
public <T extends Component> T addComponent(T component)

// 获取组件
public <T extends Component> T getComponent(Class<T> componentClass)

// 移除组件
public void removeComponent(Component component)

// 检查是否有组件
public boolean hasComponent(Class<? extends Component> componentClass)
```

#### 碰撞箱管理
```java
// 设置主碰撞箱
public void setCollider(Collider collider)

// 添加碰撞箱（用于部位伤害）
public void addCollider(Collider collider)

// 获取碰撞箱
public Collider getCollider()
public List<Collider> getColliders()
public boolean hasCollider()
```

#### 层级关系
```java
// 设置父实体
public void setParent(Entity parent)

// 添加子实体
public void addChild(Entity child)

// 获取父子关系
public Entity getParent()
public List<Entity> getChildren()
```

#### 属性和状态
```java
// 基本属性
public String getId()
public String getName()
public void setName(String name)
public boolean isActive()
public void setActive(boolean active)

// 变换
public Transform getTransform()

// 渲染
public RenderComponent getRenderComponent()
public boolean isRenderable()

// AI
public AIController getAI()
public boolean hasAI()

// 生命值
public EntityStats getStats()
```

#### 战斗系统
```java
// 受到伤害
public void takeDamage(float damage, Entity attacker, Collider hitCollider)

// 治疗
public void heal(float amount)

// 死亡
protected void onDeath(Entity killer)
```

---

### Component
**位置**: `com.zenith.logic.component.Component`

组件基类，所有组件的父类。

#### 生命周期方法
```java
// 创建时调用
public abstract void onCreate()

// 每帧更新
public abstract void onUpdate(float deltaTime)

// 销毁时调用
public abstract void onDestroy()
```

#### 属性访问
```java
public Entity getOwner()           // 获取宿主实体
public boolean isEnabled()         // 是否启用
public void setEnabled(boolean enabled)
public Transform getTransform()    // 获取宿主的变换
```

---

### RenderComponent
**位置**: `com.zenith.logic.component.RenderComponent`

渲染组件基类，连接逻辑实体和渲染管线。

#### 核心方法
```java
// 渲染实现（由子类实现）
public abstract void render(Matrix4f viewMatrix, Matrix4f projectionMatrix)

// 可见性控制
public boolean isVisible()
public void setVisible(boolean visible)
```

---

### MeshComponent
**位置**: `com.zenith.logic.component.MeshComponent`

网格渲染组件，用于渲染3D模型。

#### 构造函数
```java
public MeshComponent(GLMesh mesh, GLShader shader)
```

#### 功能
- 自动从实体Transform提取模型矩阵
- 上传MVP矩阵到着色器
- 执行网格渲染

---

### Collider
**位置**: `com.zenith.logic.component.Collider`

碰撞箱基类，定义实体的碰撞体积。

#### 核心方法
```java
// 碰撞检测
public boolean intersects(Collider other)

// 获取包围盒
public BoundingBox getBoundingBox()

// 设置偏移（相对于实体位置）
public void setOffset(Vector3f offset)

// 设置名称（用于部位识别）
public void setName(String name)
public String getName()
```

---

### BoxCollider
**位置**: `com.zenith.logic.component.BoxCollider`

盒形碰撞箱实现。

#### 构造函数
```java
public BoxCollider(float width, float height, float depth)
```

---

### AIController
**位置**: `com.zenith.logic.component.AIController`

AI控制器组件，管理实体的AI行为。

#### 核心方法
```java
// 注册AI状态
public void addState(String name, AIState state)

// 切换状态
public void switchState(String name)

// 受到伤害时的反应
public void onDamaged(Entity attacker, float damage)
```

---

### AIState
**位置**: `com.zenith.logic.state.AIState`

AI状态基类，用于实现状态机。

#### 生命周期方法
```java
// 进入状态时调用
public abstract void onEnter()

// 状态激活期间每帧调用
public abstract void onUpdate(float deltaTime)

// 退出状态时调用
public abstract void onExit()
```

#### 属性
```java
public Entity getOwner()  // 获取所属实体
```

---

## 事件系统

### EventBus
**位置**: `com.zenith.logic.event.EventBus`

事件总线，实现发布-订阅模式。

#### 核心方法
```java
// 注册监听器
public void register(Object object)

// 发布事件
public void post(Event event)
```

#### 使用示例
```java
// 定义监听器
public class MyListener {
    @Subscribe(priority = EventPriority.NORMAL)
    public void onEntityDamaged(EntityDamagedEvent event) {
        // 处理事件
    }
}

// 注册和使用
EventBus bus = new EventBus();
bus.register(new MyListener());
bus.post(new EntityDamagedEvent(entity, damage, attacker));
```

---

### Event
**位置**: `com.zenith.logic.event.Event`

事件基类，所有事件的父类。

#### 核心方法
```java
// 获取事件名称
public String getName()

// 是否可取消
public boolean isCancelable()
```

---

### CancelableEvent
**位置**: `com.zenith.logic.event.CancelableEvent`

可取消的事件基类。

#### 核心方法
```java
// 取消事件
public void cancel()

// 检查是否已取消
public boolean isCanceled()

@Override
public boolean isCancelable() { return true; }
```

---

### Subscribe
**位置**: `com.zenith.logic.event.Subscribe`

订阅注解，标记事件处理方法。

#### 属性
```java
EventPriority priority() default EventPriority.NORMAL;
```

---

### EventPriority
**位置**: `com.zenith.logic.event.EventPriority`

事件优先级枚举。

#### 优先级级别
- `HIGHEST`: 最高优先级
- `HIGH`: 高优先级
- `NORMAL`: 普通优先级
- `LOW`: 低优先级
- `LOWEST`: 最低优先级

---

### SystemEventBus
**位置**: `com.zenith.logic.event.SystemEventBus`

系统级事件总线单例。

#### 使用方法
```java
SystemEventBus.getInstance().post(event);
SystemEventBus.getInstance().register(listener);
```

---

## 脚本系统

### ScriptManager
**位置**: `com.zenith.logic.script.ScriptManager`

脚本管理器，基于GraalJS实现JavaScript脚本支持。

#### 核心功能
- JavaScript代码执行
- 热重载支持（文件修改自动检测）
- Java-JavaScript互操作
- 函数和类注册

#### 构造函数
```java
public ScriptManager()
```

#### 脚本执行
```java
// 执行脚本（支持自动重载）
public Value execute(AssetResource resource)

// 强制重载脚本
public void forceReload(AssetResource resource)

// 检查是否已注册
public boolean hasClass(String name)
```

#### Java-JavaScript桥接
```java
// 注册Java函数
public void registerFunction(String name, Object function)

// 注册Java类
public void registerClass(String name, Class<?> clazz)

// 设置全局变量
public void setVariable(String name, Object value)

// 获取全局变量
public Value getGlobal(String name)
```

#### 资源管理
```java
// 关闭脚本引擎
@Override
public void close()

// 获取所有实例
public static List<ScriptManager> getInstances()
```

#### 配置特性
- ECMAScript 2022标准支持
- 完整的Java主机访问权限
- Double到Float的自动类型转换
- 堆栈跟踪限制（10层）

---

### ScriptRegistration
**位置**: `com.zenith.logic.script.ScriptRegistration`

脚本注册助手，简化Java API暴露给JavaScript的过程。

#### 核心方法
```java
// 注册常用引擎API
public void registerEngineAPIs()

// 注册数学库
public void registerMathLibrary()

// 注册日志系统
public void registerLogger()
```

---

## 资源管理

### AssetResource
**位置**: `com.zenith.asset.AssetResource`

资产资源接口，统一的资源访问抽象。

#### 核心方法
```java
// 读取为字符串
public String readAsString() throws IOException

// 读取为字节数组
public byte[] readAsBytes() throws IOException

// 获取资源位置
public URI getLocation()

// 获取最后修改时间
public long getLastModified()

// 关闭资源
public void close() throws IOException
```

---

### Transform
**位置**: `com.zenith.common.math.Transform`

变换类，管理位置、旋转、缩放。

#### 核心方法
```java
// 位置操作
public Vector3f getPosition()
public void setPosition(Vector3f pos)
public void setPosition(float x, float y, float z)

// 旋转操作
public Quaternionf getRotation()
public void setRotation(Quaternionf rot)
public void rotate(float angle, Vector3f axis)

// 缩放操作
public Vector3f getScale()
public void setScale(Vector3f scale)
public void setScale(float x, float y, float z)

// 获取变换矩阵
public void getTransformationMatrix(Matrix4f dest)

// 方向向量
public Vector3f getForward()
public Vector3f getUp()
public Vector3f getRight()
```

---

### Color
**位置**: `com.zenith.common.math.Color`

颜色类，表示RGBA颜色值。

#### 构造函数
```java
public Color(float r, float g, float b, float a)
public Color(int rgba)  // 打包的整数颜色
```

#### 预定义颜色
```java
Color.WHITE
Color.BLACK
Color.RED
Color.GREEN
Color.BLUE
Color.TRANSPARENT
// ... 等等
```

#### 核心方法
```java
// 获取分量
public float getR(), getG(), getB(), getA()

// 设置分量
public void setR(float r), setG(float g), etc.

// 颜色运算
public Color multiply(Color other)
public Color add(Color other)
public Color interpolate(Color other, float t)
```

---

### Rectf
**位置**: `com.zenith.common.math.Rectf`

矩形类，表示浮点数矩形区域。

#### 构造函数
```java
public Rectf(float x, float y, float width, float height)
```

#### 核心方法
```java
// 属性访问
public float getX(), getY(), getWidth(), getHeight()

// 包含检测
public boolean contains(float x, float y)
public boolean contains(Rectf other)

// 相交检测
public boolean intersects(Rectf other)
```

---

## 高级特性

### HTMLLogger
**位置**: `com.zenith.common.utils.HTMLLogger`

HTML日志记录器，将日志输出到HTML组件。

#### 核心方法
```java
// 记录日志
public static void log(String message)
public static void info(String message)
public static void warn(String message)
public static void error(String message)

// 清除日志
public static void clear()
```

---

### Win32IMEHelper
**位置**: `com.zenith.common.utils.Win32IMEHelper`

Windows输入法帮助类，支持中文输入。

#### 核心功能
- IME上下文管理
- 输入法状态跟踪
- 候选词窗口处理

---

### InternalLogger
**位置**: `com.zenith.common.utils.InternalLogger`

内部日志系统。

#### 日志级别
```java
InternalLogger.debug(String message)
InternalLogger.info(String message)
InternalLogger.warn(String message)
InternalLogger.error(String message)
```

---

## 设计模式和最佳实践

### 1. 组件系统
Zenith使用实体-组件模式（ECS的变体），推荐做法：
```java
// 创建实体
Entity player = new PlayerEntity("Player");

// 添加组件
player.addComponent(new MeshComponent(mesh, shader));
player.addComponent(new BoxCollider(1.0f, 2.0f, 1.0f));
player.addComponent(new AIController());

// 访问组件
MeshComponent render = player.getComponent(MeshComponent.class);
Collider collider = player.getCollider();
```

### 2. 事件驱动
使用事件系统解耦模块：
```java
// 定义事件
public class PlayerHealthChanged extends Event {
    private final float newHealth;
    // ...
}

// 发布事件
SystemEventBus.getInstance().post(new PlayerHealthChanged(health));

// 订阅事件
@Subscribe
public void onHealthChanged(PlayerHealthChanged event) {
    updateHealthBar(event.getNewHealth());
}
```

### 3. 资源管理
正确管理资源生命周期：
```java
// 加载资源
Texture texture = resourceManager.loadTexture("textures/player.png");
Shader shader = resourceManager.loadShader("pbr", "shaders/pbr.vert", "shaders/pbr.frag");

// 使用资源
Material material = new GLMaterial(shader);
material.setTexture("diffuse", texture);

// 清理资源（通常在引擎关闭时）
ResourceManager.unloadAll();
```

### 4. 音频分路
使用音频分路独立控制音量：
```java
// 设置不同分路的音量
audioManager.setGroupVolume(AudioGroup.MUSIC, 0.5f);
audioManager.setGroupVolume(AudioGroup.SFX, 0.8f);
audioManager.setGroupVolume(AudioGroup.AMBIENT, 0.3f);

// 创建声源并指定分路
AudioSource source = new AudioSource(bufferId, false, false);
source.setGroup(AudioGroup.SFX, audioManager.getMixer());
source.setGain(1.0f, audioManager.getMixer());
```

### 5. UI构建
构建UI界面的推荐方式：
```java
// 创建屏幕
UIScreen mainScreen = new UIScreen();

// 创建按钮
UIButton button = new UIButton(100, 100, 200, 50, atlas, "button_sprite")
    .setLabel("Click Me")
    .setOnClick(() -> System.out.println("Clicked!"))
    .setTooltip("This is a button")
    .setBorderEnabled(true);

// 添加到屏幕
mainScreen.addComponent(button);

// 设置为当前屏幕
setUIScreen(mainScreen);
```

### 6. 异步加载
使用异步加载避免卡顿：
```java
@Override
protected void asyncLoad() {
    // 在后台线程加载资源
    setLoadingProgress(0.1f);
    Texture texture = loadTexture("large_texture.png");
    
    setLoadingProgress(0.5f);
    Model model = loadModel("complex_model.obj");
    
    setLoadingProgress(0.9f);
    // 需要在主线程执行的OpenGL操作
    runOnMainThread(() -> {
        setupGLResources();
    });
    
    setLoadingProgress(1.0f);
}

@Override
protected void init() {
    // 在主线程初始化
    createEntities();
    setupLights();
}
```

---

## 常见问题

### Q: 如何创建自定义实体？
```java
public class PlayerEntity extends Entity {
    public PlayerEntity(String name) {
        super(name);
    }
    
    @Override
    public void onCreate() {
        // 添加组件
        addComponent(new MeshComponent(playerMesh, playerShader));
        addComponent(new BoxCollider(0.5f, 1.8f, 0.5f));
        
        // 设置初始位置
        getTransform().setPosition(0, 0, 0);
    }
}
```

### Q: 如何实现自定义AI状态？
```java
public class PatrolState extends AIState {
    private List<Vector3f> waypoints;
    private int currentWaypoint;
    
    @Override
    public void onEnter() {
        currentWaypoint = 0;
    }
    
    @Override
    public void onUpdate(float deltaTime) {
        // 巡逻逻辑
        Vector3f target = waypoints.get(currentWaypoint);
        moveTowards(target, deltaTime);
        
        if (reachedTarget(target)) {
            currentWaypoint = (currentWaypoint + 1) % waypoints.size();
        }
    }
    
    @Override
    public void onExit() {
        // 清理工作
    }
}

// 使用
AIController ai = entity.getAI();
ai.addState("patrol", new PatrolState());
ai.switchState("patrol");
```

### Q: 如何处理窗口大小变化？
Zenith引擎自动处理窗口大小变化，但如果你需要自定义响应：
```java
@Override
public void onResize(int width, int height) {
    // 更新视口
    glViewport(0, 0, width, height);
    
    // 更新相机投影
    camera.getProjection().updateSize(width, height);
    
    // 更新FBO
    sceneFBO.resize(width, height);
}
```

### Q: 如何在JavaScript中调用Java方法？
```java
// Java端注册
scriptManager.registerFunction("log", (String message) -> {
    InternalLogger.info("From JS: " + message);
});

scriptManager.registerClass("Vector3", Vector3f.class);

// JavaScript端使用
log("Hello from JavaScript!");
let vec = new Vector3(1.0, 2.0, 3.0);
```

---

## 性能优化建议

1. **批处理渲染**: 使用相同的材质和着色器的对象会被自动批处理
2. **视锥剔除**: 引擎自动进行视锥剔除，确保正确设置包围盒
3. **资源复用**: 共享Mesh和Material实例，避免重复创建
4. **对象池**: 对频繁创建销毁的对象使用对象池
5. **LOD系统**: 为远距离物体使用低精度模型
6. **纹理压缩**: 使用压缩纹理格式减少显存占用
7. **音频流式传输**: 大型音频文件使用流式加载

---

## 版本信息

- **引擎名称**: Zenith Engine
- **渲染后端**: OpenGL
- **脚本引擎**: GraalJS (ECMAScript 2022)
- **音频系统**: OpenAL
- **Web集成**: JCEF (Java Chromium Embedded Framework)
- **数学库**: JOML (Java OpenGL Math Library)
- **窗口系统**: GLFW

---

## 支持与贡献

如需报告问题或贡献代码，请参考项目的CONTRIBUTING.md文件。

---

*文档最后更新: 2026年5月*
