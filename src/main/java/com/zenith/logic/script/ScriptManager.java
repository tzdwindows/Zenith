package com.zenith.logic.script;

import com.zenith.asset.AssetResource;
import com.zenith.common.utils.InternalLogger;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

import java.io.IOException;

public class ScriptManager implements AutoCloseable {
    private final Context context;
    private final Value bindings;

    private final ScriptRegistration registration ;

    public ScriptManager() {
        HostAccess hostAccess = HostAccess.newBuilder(HostAccess.ALL)
                .targetTypeMapping(Double.class, Float.class,
                        (v) -> true,
                        (v) -> v.floatValue())
                .build();
        this.context = Context.newBuilder("js")
                .allowHostAccess(hostAccess)
                .allowHostClassLookup(className -> true)
                .option("js.ecmascript-version", "2022")
                .build();

        this.bindings = context.getBindings("js");
        this.registration = new ScriptRegistration(this);
        InternalLogger.info("GraalJS 脚本引擎初始化完成。");
    }

    /**
     * 注册一个 Java 方法到 JS 全局上下文中。
     *
     * @param name 在 JS 中调用的名称
     * @param function Java 实现，可以是一个 Lambda 表达式、方法引用或实现了 FunctionalInterface 的对象
     *
     * 示例:
     * registerFunction("log", (String msg) -> System.out.println(msg));
     * registerFunction("spawn", myWorld::spawnEntity);
     */
    public void registerFunction(String name, Object function) {
        if (function == null) {
            InternalLogger.warn("尝试注册空的脚本函数: " + name);
            return;
        }
        bindings.putMember(name, function);
    }

    /**
     * 注册一个 Java 类到 JS 上下文，使其可以在 JS 里被实例化。
     * @param name 在 JS 中使用的类名
     * @param clazz Java 类对象
     */
    public void registerClass(String name, Class<?> clazz) {
        Value type = context.eval("js", "Java.type('" + clazz.getName() + "')");
        bindings.putMember(name, type);
    }

    /**
     * 注入全局变量（如 Player, World 对象）
     */
    public void setVariable(String name, Object value) {
        bindings.putMember(name, value);
    }

    public Value execute(AssetResource resource) {
        try (resource) {
            String code = resource.readAsString();
            String fileName = resource.getLocation().getPath();

            Source source = Source.newBuilder("js", code, fileName)
                    .mimeType("application/javascript")
                    .build();

            return context.eval(source);
        } catch (IOException e) {
            InternalLogger.error("无法从资源加载脚本: " + resource.getLocation(), e);
            return null;
        } catch (Exception e) {
            InternalLogger.error("脚本执行异常: " + resource.getLocation(), e);
            throw e;
        }
    }

    public Value getGlobal(String name) {
        return bindings.getMember(name);
    }

    public ScriptRegistration getRegistration() {
        return registration;
    }

    @Override
    public void close() {
        if (context != null) {
            context.close();
        }
    }
}