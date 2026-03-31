package com.zenith.logic.script;

import com.zenith.common.utils.InternalLogger;
import com.zenith.common.utils.MeshUtils;
import com.zenith.common.utils.ScriptLogger;
import com.zenith.core.ZenithEngine;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;

import java.util.List;

public class ScriptRegistration {
    public ScriptRegistration(ScriptManager manager) {
        manager.setVariable("Log", new ScriptLogger());
        manager.setVariable("console", new ScriptLogger());
        manager.registerClass("MeshUtils", MeshUtils.class);
        registerAllInPackage(manager, "com.zenith.audio");
        registerAllInPackage(manager, "com.zenith.common.math");
        registerAllInPackage(manager, "com.zenith.render.backend");
        registerAllInPackage(manager, "com.zenith.render.backend.opengl");
        registerAllInPackage(manager, "com.zenith.render.backend.opengl.buffer");
        registerAllInPackage(manager, "com.zenith.render.backend.opengl.shader");
        registerAllInPackage(manager, "com.zenith.render.backend.opengl.texture");
        registerAllInPackage(manager, "com.zenith.ui");
        registerAllInPackage(manager, "com.zenith.ui.component");
        registerAllInPackage(manager, "com.zenith.ui.event");
        registerAllInPackage(manager, "com.zenith.ui.layout");
        registerAllInPackage(manager, "com.zenith.ui.render");
        registerAllInPackage(manager, "com.zenith.ui.skin");
        registerAllInPackage(manager, "com.zenith.asset");
        registerPackages(manager, List.of("org.lwjgl", "org.joml"));
        manager.registerClass("ZenithEngine", ZenithEngine.class);
    }

    /**
     * 扫描指定包下的所有类并注册到脚本引擎
     */
    private void registerAllInPackage(ScriptManager manager, String packageName) {
        InternalLogger.debug("正在扫描并注册脚本类库: " + packageName);
        try (ScanResult scanResult = new ClassGraph()
                .acceptPackages(packageName)
                .enableClassInfo()
                .scan()) {

            for (ClassInfo classInfo : scanResult.getAllClasses()) {
                Class<?> clazz = classInfo.loadClass();
                String simpleName = clazz.getSimpleName();
                if (!simpleName.isEmpty() && !simpleName.contains("$")) {
                    manager.registerClass(simpleName, clazz);
                    InternalLogger.debug("  -> 已注册类: " + simpleName + " [" + clazz.getName() + "]");
                }
            }
        } catch (Exception e) {
            InternalLogger.error("脚本包扫描失败: " + packageName, e);
        }
    }

    /**
     * 一次性扫描多个包并注册，极大提升启动性能
     */
    private void registerPackages(ScriptManager manager, List<String> packageNames) {
        InternalLogger.debug("正在执行全量包扫描: " + packageNames);
        long startTime = System.currentTimeMillis();
        try (ScanResult scanResult = new ClassGraph()
                .acceptPackages(packageNames.toArray(new String[0]))
                .rejectPackages("org.lwjgl.system.libc", "org.lwjgl.system.linux", "org.lwjgl.system.windows")
                .enableClassInfo()
                .scan()) {
            int count = 0;
            for (ClassInfo classInfo : scanResult.getAllClasses()) {
                if (classInfo.isAnonymousInnerClass() || classInfo.isSynthetic() || !classInfo.isPublic()) {
                    continue;
                }
                String simpleName = classInfo.getSimpleName();
                if (simpleName.isEmpty() || simpleName.contains("$")) continue;
                Class<?> clazz = classInfo.loadClass();
                if (simpleName.equals("Math") || simpleName.equals("String") || simpleName.equals("Object")) {
                    simpleName = "J" + simpleName;
                }
                try {
                    manager.registerClass(simpleName, clazz);
                    count++;
                } catch (Exception e) {
                }
            }
            long endTime = System.currentTimeMillis();
            InternalLogger.debug("成功注册了 " + count + " 个类，耗时: " + (endTime - startTime) + "ms");
        } catch (Exception e) {
            InternalLogger.error("全量包扫描失败", e);
        }
    }
}