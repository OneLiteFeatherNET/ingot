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

package net.onelitefeather.ingot.plugin.schema

import com.reposilite.configuration.shared.api.Doc
import com.reposilite.configuration.shared.api.SharedSettings
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

/** The settings class a fixture plugin jar carries. Its bytes are copied into the jar, so it is loaded again from there. */
@Doc(title = "Fixture plugin", description = "Settings of a plugin loaded from a fixture jar")
data class FixturePluginSettings(val endpoint: String = "") : SharedSettings

/**
 * Writes a plugin jar that holds [FixturePluginSettings] and, when given, its schema under `json-schemes/<class name>`, and opens
 * it in a class loader of its own that defines the settings class itself, as the loader of a plugin does.
 */
internal object PluginSettingsFixtureJar {

    private val settingsClass = FixturePluginSettings::class.java

    fun write(directory: Path, schema: String?): Path {
        val file = directory.resolve("fixture-plugin.jar")
        val classBytes = settingsClass.getResourceAsStream("${settingsClass.simpleName}.class")!!.use { it.readBytes() }
        JarOutputStream(Files.newOutputStream(file)).use { out ->
            out.putNextEntry(JarEntry(settingsClass.name.replace('.', '/') + ".class"))
            out.write(classBytes)
            out.closeEntry()
            if (schema != null) {
                out.putNextEntry(JarEntry("json-schemes/${settingsClass.name}"))
                out.write(schema.toByteArray())
                out.closeEntry()
            }
        }
        return file
    }

    fun open(jar: Path): URLClassLoader =
        ChildFirstClassLoader(jar, settingsClass.classLoader)

    fun settingsType(loader: ClassLoader): Class<out SharedSettings> =
        loader.loadClass(settingsClass.name).asSubclass(SharedSettings::class.java)

    /** Defines the classes of its jar before asking the parent, so the settings class differs from the one on the test classpath. */
    private class ChildFirstClassLoader(jar: Path, parent: ClassLoader) : URLClassLoader(arrayOf(jar.toUri().toURL()), parent) {
        override fun loadClass(name: String, resolve: Boolean): Class<*> =
            synchronized(getClassLoadingLock(name)) {
                findLoadedClass(name)
                    ?: try {
                        findClass(name)
                    } catch (exception: ClassNotFoundException) {
                        super.loadClass(name, resolve)
                    }
            }
    }
}
