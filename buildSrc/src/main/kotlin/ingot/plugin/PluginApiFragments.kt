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

import java.io.File
import java.util.zip.ZipFile
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.jvm.tasks.Jar

// The build step of a plugin module that documents its routes and settings (see openspec change add-plugin-api-fragments, D4).
// The OpenAPI annotation processor of the backend's version writes the module's document and schemas into the kapt class output.
// The step renames the document to META-INF/ingot/openapi/<plugin id>.json, which the server merges into its own document, and
// removes the two processor indexes, which would otherwise shadow the server's on a shared class loader.
object PluginApiFragments {

    const val FRAGMENT_DIRECTORY = "META-INF/ingot/openapi/"
    const val PROCESSOR_INDEX = "openapi-plugin/.index"
    const val PROCESSOR_DOCUMENT = "openapi-plugin/openapi-default.json"
    const val SCHEMA_INDEX = "json-schemes/index"

    private const val PROCESSOR_GROUP = "io.javalin.community.openapi"
    private const val PROCESSOR_NAME = "openapi-annotation-processor"
    private const val BACKEND = ":reposilite-backend"

    // Called from the build.gradle.kts of a plugin module of this repository.
    fun apply(project: Project, pluginId: String) {
        require(pluginId.isNotBlank() && '/' !in pluginId) { "Plugin id '$pluginId' cannot name a file under $FRAGMENT_DIRECTORY" }

        project.pluginManager.apply("org.jetbrains.kotlin.kapt")
        project.dependencies.add("kapt", "$PROCESSOR_GROUP:$PROCESSOR_NAME:${processorVersion(project)}")
        // As in the backend: the test source sets keep no generated document of their own.
        project.configurations.matching { it.name in setOf("kaptTest", "kaptIntegration", "kaptTestFixtures") }.configureEach {
            exclude(mapOf("group" to PROCESSOR_GROUP, "module" to PROCESSOR_NAME))
        }

        val kaptClasses = project.layout.buildDirectory.dir("tmp/kapt3/classes/main")
        project.tasks.matching { it.name == "kaptKotlin" }.configureEach {
            doLast { relocate(kaptClasses.get().asFile, pluginId) }
        }

        val verify = project.tasks.register("verifyPluginApiFragments", VerifyPluginApiFragmentsTask::class.java) {
            group = "verification"
            description = "Checks that the plugin jar holds only its own api document fragment and no processor index."
            this.pluginId.set(pluginId)
            jar.set(project.tasks.named("jar", Jar::class.java).flatMap { it.archiveFile })
        }
        project.tasks.named("check") { dependsOn(verify) }
    }

    // The version the backend declares for the processor, so a plugin module generates the same document format as the server.
    private fun processorVersion(project: Project): String {
        project.evaluationDependsOn(BACKEND)
        return project.project(BACKEND).configurations.getByName("kapt").dependencies
            .firstOrNull { it.group == PROCESSOR_GROUP && it.name == PROCESSOR_NAME }
            ?.version
            ?: throw GradleException("$BACKEND declares no kapt dependency on $PROCESSOR_GROUP:$PROCESSOR_NAME")
    }

    // Rewrites the processor output in place: the document under the plugin id, no processor index, no schema index.
    fun relocate(output: File, pluginId: String) {
        val document = output.resolve(PROCESSOR_DOCUMENT)
        if (document.isFile) {
            val fragment = output.resolve("$FRAGMENT_DIRECTORY$pluginId.json")
            fragment.parentFile.mkdirs()
            document.copyTo(fragment, overwrite = true)
        }
        output.resolve(PROCESSOR_DOCUMENT.substringBefore('/')).deleteRecursively()
        output.resolve(SCHEMA_INDEX).delete()
        // A module without @JsonSchema classes would otherwise keep an empty json-schemes/ directory in its jar.
        output.resolve(SCHEMA_INDEX.substringBefore('/')).takeIf { it.list()?.isEmpty() == true }?.delete()
    }

    // One message per entry of the jar that breaks the layout. An empty list means the jar is fine, also when it has no fragment.
    fun violations(jar: File, pluginId: String): List<String> {
        val expected = "$FRAGMENT_DIRECTORY$pluginId.json"
        return ZipFile(jar).use { zip ->
            zip.entries().asSequence().map { it.name }.sorted().mapNotNull { name ->
                when {
                    name == PROCESSOR_INDEX || name == SCHEMA_INDEX ->
                        "${jar.name} holds $name, which would shadow the server's index; the build step removes it"
                    name.startsWith(FRAGMENT_DIRECTORY) && name.endsWith(".json") && name != expected ->
                        "${jar.name} holds $name, but the server reads only $expected for plugin id '$pluginId'"
                    else -> null
                }
            }.toList()
        }
    }
}

// Runs PluginApiFragments.violations over the jar of a plugin module, as part of check.
abstract class VerifyPluginApiFragmentsTask : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val jar: RegularFileProperty

    @get:Input
    abstract val pluginId: Property<String>

    @TaskAction
    fun verify() {
        val violations = PluginApiFragments.violations(jar.get().asFile, pluginId.get())
        if (violations.isNotEmpty()) {
            throw GradleException(violations.joinToString(separator = "\n", prefix = "Plugin api fragment check failed:\n") { "  - $it" })
        }
    }
}
