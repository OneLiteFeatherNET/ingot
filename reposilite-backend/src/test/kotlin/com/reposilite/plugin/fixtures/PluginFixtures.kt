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

import com.reposilite.plugin.api.Facade
import com.reposilite.plugin.api.Plugin
import com.reposilite.plugin.api.ReposilitePlugin
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.io.path.outputStream
import kotlin.reflect.KClass

@Plugin(name = "fixture")
class FixturePluginOne : ReposilitePlugin() {
    override fun initialize(): Facade? = null
}

@Plugin(name = "fixture")
class FixturePluginTwo : ReposilitePlugin() {
    override fun initialize(): Facade? = null
}

@Plugin(name = "other-fixture")
class OtherFixturePlugin : ReposilitePlugin() {
    override fun initialize(): Facade? = null
}

/** Packs one plugin class and its service declaration into a jar inside [directory], so the test owns every byte it loads. */
internal object PluginJarFixture {

    fun write(directory: Path, fileName: String, pluginClass: KClass<out ReposilitePlugin>): Path {
        val jar = directory.resolve(fileName)
        val classEntry = pluginClass.java.name.replace('.', '/') + ".class"
        JarOutputStream(jar.outputStream()).use { out ->
            out.putNextEntry(JarEntry("META-INF/services/${ReposilitePlugin::class.java.name}"))
            out.write("${pluginClass.java.name}\n".toByteArray())
            out.closeEntry()

            out.putNextEntry(JarEntry(classEntry))
            requireNotNull(pluginClass.java.classLoader.getResourceAsStream(classEntry)) { "Missing class bytes for $classEntry" }
                .use { it.copyTo(out) }
            out.closeEntry()
        }
        return jar
    }

}
