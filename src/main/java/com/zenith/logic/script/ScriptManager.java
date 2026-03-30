package com.zenith.logic.script;

import com.zenith.asset.AssetResource;
import com.zenith.common.utils.InternalLogger;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ScriptManager implements AutoCloseable {
    private static final List<ScriptManager> instances = new java.util.ArrayList<>();
    private final Context context;
    private final Value bindings;
    private final ScriptRegistration registration;
    private final Map<String, CachedSource> scriptCache = new ConcurrentHashMap<>();

    private static class CachedSource {
        Source source;
        long lastModified;

        CachedSource(Source source, long lastModified) {
            this.source = source;
            this.lastModified = lastModified;
        }
    }

    public ScriptManager() {
        HostAccess hostAccess = HostAccess.newBuilder(HostAccess.ALL)
                .targetTypeMapping(Double.class, Float.class,
                        (v) -> true,
                        Double::floatValue)
                .build();
        this.context = Context.newBuilder("js")
                .allowHostAccess(hostAccess)
                .allowHostClassLookup(className -> true)
                .option("js.ecmascript-version", "2022")
                .build();

        this.bindings = context.getBindings("js");
        this.registration = new ScriptRegistration(this);
        instances.add(this);
        InternalLogger.info("GraalJS 脚本引擎初始化完成。");
    }

    /**
     * 执行脚本：如果文件被修改，则自动重载并重新评估。
     */
    public Value execute(AssetResource resource) {
        String path = resource.getLocation().getPath();
        long currentLastModified = resource.getLastModified();

        try (resource) {
            CachedSource cached = scriptCache.get(path);
            if (cached == null || cached.lastModified < currentLastModified) {
                if (cached != null) {
                    InternalLogger.info("检测到脚本更新，正在重载: " + path);
                }
                String code = resource.readAsString();
                Source source = Source.newBuilder("js", code, path)
                        .mimeType("application/javascript")
                        .build();
                cached = new CachedSource(source, currentLastModified);
                scriptCache.put(path, cached);
            }
            return context.eval(cached.source);
        } catch (IOException e) {
            InternalLogger.error("无法从资源加载脚本: " + path, e);
            return null;
        } catch (Exception e) {
            InternalLogger.error("脚本执行异常: " + path, e);
            throw e;
        }
    }

    /**
     * 强制重载某个脚本
     */
    public void forceReload(AssetResource resource) {
        String path = resource.getLocation().getPath();
        scriptCache.remove(path);
        execute(resource);
    }

    public void registerFunction(String name, Object function) {
        if (function == null) {
            InternalLogger.warn("尝试注册空的脚本函数: " + name);
            return;
        }
        bindings.putMember(name, function);
    }

    public void registerClass(String name, Class<?> clazz) {
        Value type = context.eval("js", "Java.type('" + clazz.getName() + "')");
        bindings.putMember(name, type);
    }

    public void setVariable(String name, Object value) {
        bindings.putMember(name, value);
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
        scriptCache.clear();
    }

    public static List<ScriptManager> getInstances() {
        return instances;
    }
}