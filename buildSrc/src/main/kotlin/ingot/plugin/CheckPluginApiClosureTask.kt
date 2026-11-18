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

package ingot.plugin

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction
import java.util.zip.ZipFile

// Fails the build when a class of the slim plugin API jar refers to a new Ingot type that the jar does not hold. It has no
// outputs, so Gradle runs it on every invocation.
abstract class CheckPluginApiClosureTask : DefaultTask() {

    @get:InputFile
    abstract val artifact: RegularFileProperty

    @get:InputFile
    abstract val allowlist: RegularFileProperty

    @TaskAction
    fun check() {
        val classes = ZipFile(artifact.get().asFile).use { zip ->
            zip.entries().asSequence()
                .filter { !it.isDirectory && it.name.endsWith(".class") }
                .associate { entry -> entry.name.removeSuffix(".class") to zip.getInputStream(entry).use { it.readBytes() } }
        }
        val names = PluginApiAllowlist.parse(allowlist.get().asFile.readText())

        val violations = PluginApiClosure.violations(classes, names)
        if (violations.isNotEmpty()) {
            throw GradleException(violations.joinToString(separator = "\n", prefix = "Plugin API closure check failed:\n") { "  - $it" })
        }
        logger.lifecycle("Plugin API closure holds for ${names.size} listed classes.")
    }
}
