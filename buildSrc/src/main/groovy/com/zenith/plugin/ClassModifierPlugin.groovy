package com.zenith.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry
import java.io.File
import java.io.FileOutputStream
import javax.xml.parsers.DocumentBuilderFactory

public class ClassModifierPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        def patchedLibsDir = new File(project.buildDir, "patched-libs")
        def originalCacheDir = new File(project.buildDir, "original-cache")

        def patchTask = project.tasks.register("applyClassRedirection") {
            group = "zenith engine"
            File configFile = project.file("src/main/resources/ClassModifyConfiguration.xml")

            inputs.file configFile
            outputs.dir patchedLibsDir
            outputs.dir originalCacheDir

            inputs.property("configSemanticHash") {
                if (!configFile.exists()) return ""
                return configFile.text.replaceAll("(?s)<!--.*?-->", "").replaceAll("\\s", "").hashCode().toString()
            }

            doLast {
                if (!configFile.exists()) return

                // 解析配置并校验本地源码是否存在
                def factory = DocumentBuilderFactory.newInstance()
                def builder = factory.newDocumentBuilder()
                def doc = builder.parse(configFile)
                def redirectNodes = doc.getElementsByTagName("Redirect")

                List<String> targetsToRemove = []
                for (int i = 0; i < redirectNodes.getLength(); i++) {
                    def node = redirectNodes.item(i)
                    def target = node.getAttributes().getNamedItem("target")?.getNodeValue()
                    if (target) {
                        // 校验逻辑：只有本地 src/main/java 下有对应 .java 文件才剔除 JAR 里的类
                        String sourcePath = target.replace(".class", ".java")
                        File localSource = project.file("src/main/java/${sourcePath}")
                        if (localSource.exists()) {
                            targetsToRemove.add(target)
                        } else {
                            project.logger.lifecycle("[ZenithModifier] 源码缺失回溯: 未发现 ${sourcePath}，将保留 JAR 内原始类。")
                        }
                    }
                }

                // 彻底清理旧补丁，实现删除配置后的“自动恢复”
                if (patchedLibsDir.exists()) patchedLibsDir.listFiles()?.each { it.delete() }
                if (!patchedLibsDir.exists()) patchedLibsDir.mkdirs()
                if (!originalCacheDir.exists()) originalCacheDir.mkdirs()

                project.configurations.compileClasspath.each { File jarFile ->
                    if (!jarFile.name.endsWith(".jar") || jarFile.path.contains("patched-")) return

                    // 备份原始 JAR
                    File cachedOriginal = new File(originalCacheDir, jarFile.name)
                    if (!cachedOriginal.exists()) {
                        project.copy { from jarFile; into originalCacheDir }
                    }

                    boolean needsPatching = false
                    try {
                        new ZipFile(cachedOriginal).withCloseable { zip ->
                            needsPatching = targetsToRemove.any { zip.getEntry(it) != null }
                        }
                    } catch (Exception e) { return }

                    if (needsPatching) {
                        File outputJar = new File(patchedLibsDir, "patched-${jarFile.name}")
                        project.logger.lifecycle("[ZenithModifier] 执行重构: ${jarFile.name}")

                        new ZipOutputStream(new FileOutputStream(outputJar)).withCloseable { zos ->
                            new ZipFile(cachedOriginal).withCloseable { zip ->
                                def entries = zip.entries()
                                while (entries.hasMoreElements()) {
                                    def entry = entries.nextElement()
                                    if (targetsToRemove.contains(entry.name)) {
                                        project.logger.lifecycle("  -> [已剔除] ${entry.name}")
                                    } else {
                                        zos.putNextEntry(new ZipEntry(entry.name))
                                        zos.write(zip.getInputStream(entry).bytes)
                                        zos.closeEntry()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        project.afterEvaluate {
            project.dependencies {
                // 使用延迟加载的文件集合，确保 copyDependencies 能正常识别新生成的补丁
                implementation project.files({
                    return project.fileTree(dir: patchedLibsDir, include: "*.jar").files
                })
            }

            project.tasks.withType(org.gradle.api.tasks.compile.JavaCompile).configureEach {
                dependsOn patchTask
            }
        }
    }
}