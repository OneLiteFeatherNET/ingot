/*
 * Copyright (c) 2026 OneLiteFeather
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.reposilite.plugin.fixtures

import com.reposilite.plugin.api.ReposilitePlugin
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import javax.tools.ToolProvider
import kotlin.io.path.createDirectories
import kotlin.io.path.outputStream
import kotlin.io.path.readBytes
import kotlin.io.path.writeText

/**
 * Builds the jars of the embedded-plugin tests inside the temporary directory of one test. A plugin class is compiled at test time,
 * so its name exists in no class path of the test run and the loader has to read it from the jar it is packed in.
 */
internal object EmbeddedPluginFixture {

    /**
     * Compiles a plugin class named [className] with the id [pluginId] and packs it into the jar [directory]/[fileName]. A [version]
     * is written as `Implementation-Version` of the manifest, and a [tier] (a name of `PluginTier`) as the tier of the annotation.
     * Without a version the jar has no manifest, as before.
     */
    fun compileJar(
        directory: Path,
        fileName: String,
        className: String,
        pluginId: String,
        version: String? = null,
        tier: String? = null,
    ): Path {
        val packageName = className.substringBeforeLast('.')
        val simpleName = className.substringAfterLast('.')
        val source = directory.resolve("src").resolve("$simpleName.java")
        source.parent.createDirectories()
        val tierArgument = tier?.let { ", tier = com.reposilite.plugin.api.PluginTier.$it" }.orEmpty()
        source.writeText(
            """
            package $packageName;

            @com.reposilite.plugin.api.Plugin(name = "$pluginId"$tierArgument)
            public class $simpleName extends com.reposilite.plugin.api.ReposilitePlugin {
                @Override
                public com.reposilite.plugin.api.Facade initialize() { return null; }
            }
            """.trimIndent()
        )

        val classes = directory.resolve("classes").createDirectories()
        val errors = ByteArrayOutputStream()
        val compiler = checkNotNull(ToolProvider.getSystemJavaCompiler()) { "The embedded fixture needs a JDK compiler" }
        val code = compiler.run(
            null, null, errors,
            "-classpath", System.getProperty("java.class.path"),
            "-d", classes.toString(),
            source.toString(),
        )
        check(code == 0) { "The embedded fixture did not compile: ${errors.toString(UTF_8)}" }

        val jar = directory.resolve(fileName)
        val manifest = version?.let { value ->
            Manifest().apply {
                mainAttributes.putValue("Manifest-Version", "1.0")
                mainAttributes.putValue("Implementation-Version", value)
            }
        }
        val stream = if (manifest == null) JarOutputStream(jar.outputStream()) else JarOutputStream(jar.outputStream(), manifest)
        stream.use { out ->
            out.putNextEntry(JarEntry("META-INF/services/${ReposilitePlugin::class.java.name}"))
            out.write("$className\n".toByteArray())
            out.closeEntry()

            Files.walk(classes).use { paths ->
                paths.filter { Files.isRegularFile(it) }.sorted().forEach { file ->
                    out.putNextEntry(JarEntry(classes.relativize(file).joinToString("/")))
                    out.write(file.readBytes())
                    out.closeEntry()
                }
            }
        }
        return jar
    }

    /** Writes a server jar into [directory] that holds exactly the given entries, by their names. */
    fun serverJar(directory: Path, entries: Map<String, ByteArray>): Path {
        val jar = directory.resolve("ingot.jar").also { it.parent.createDirectories() }
        JarOutputStream(jar.outputStream()).use { out ->
            entries.forEach { (name, bytes) ->
                out.putNextEntry(JarEntry(name))
                out.write(bytes)
                out.closeEntry()
            }
        }
        return jar
    }

}

/** A logger that keeps every line in memory, so a test asserts the start log without reading the console. */
internal class CapturedLog {
    private val buffer = ByteArrayOutputStream()
    private val stream = java.io.PrintStream(buffer, true, UTF_8)
    val logger: com.reposilite.journalist.Logger = com.reposilite.journalist.backend.PrintStreamLogger(
        stream, stream, com.reposilite.journalist.Channel.ALL, true
    )

    val lines: List<String>
        get() {
            stream.flush()
            return buffer.toString(UTF_8).lines().filter { it.isNotEmpty() }
        }
}
