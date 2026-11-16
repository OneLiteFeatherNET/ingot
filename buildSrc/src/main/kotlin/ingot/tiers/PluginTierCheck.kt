/*
 * Copyright (c) 2023 dzikoysk
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

package ingot.tiers

import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.walk

// The support tiers, as the @Plugin annotation spells them.
enum class Tier { CORE, OFFICIAL, COMMUNITY, EXPERIMENTAL }

// What the check reads from one module under reposilite-plugins/. pluginId and declared are null when no source of the
// module carries a @Plugin annotation. experimentalOptIn is true when a source opts in to an ExperimentalIngot API.
data class ModuleCheck(
    val module: String,
    val pluginId: String?,
    val declared: Tier?,
    val experimentalOptIn: Boolean,
) {

    fun violations(): List<String> = buildList {
        val tierOfModule = declared?.name ?: "no @Plugin class, so no tier"
        if (experimentalOptIn && declared != Tier.EXPERIMENTAL) {
            add("$module opts in to an ExperimentalIngot API but declares $tierOfModule. Declare tier = PluginTier.EXPERIMENTAL.")
        }
        if (declared == Tier.EXPERIMENTAL && !experimentalOptIn) {
            add("$module declares EXPERIMENTAL but opts in to no ExperimentalIngot API. Remove the tier or the declaration.")
        }
    }
}

// The tier check reads the plugin sources as text, so it needs no compiled classes. The parsing is deliberately simple:
// - an opt-in is an OptIn(...) call whose arguments name an ExperimentalIngot type, or an @ExperimentalIngot... annotation
// - the declaration is the first @Plugin annotation of src/main, and its tier argument; without one the tier is COMMUNITY
// Comments are not stripped. A commented-out @Plugin or OptIn counts, so do not leave one behind.
object PluginTierCheck {

    private val optInCall = Regex("""\bOptIn\s*\(([^)]*)\)""")
    private val experimentalAnnotation = Regex("""@ExperimentalIngot\w*""")
    private val pluginAnnotation = Regex("""@Plugin\b(?:\s*\(([^)]*)\))?""")
    private val nameArgument = Regex("""\bname\s*=\s*"([^"]*)"""")
    private val tierArgument = Regex("""\btier\s*=\s*(?:PluginTier\.)?(\w+)""")

    // Every module of the plugins directory is a subdirectory with a build script, sorted by name.
    fun scan(pluginsDir: Path): List<ModuleCheck> {
        if (!pluginsDir.isDirectory()) return emptyList()
        return pluginsDir.listDirectoryEntries()
            .filter { it.isDirectory() && it.resolve("build.gradle.kts").exists() }
            .sortedBy { it.name }
            .map(::scanModule)
    }

    fun violations(checks: List<ModuleCheck>): List<String> = checks.flatMap { it.violations() }

    // An Official module needs a contractTest source set and a workflow that runs its contractTest task. The workflow
    // files are read as text, so the task has to be named by its Gradle path, as the CI job spells it.
    fun evidenceViolations(pluginsDir: Path, workflowsDir: Path): List<String> {
        val official = scan(pluginsDir).filter { it.declared == Tier.OFFICIAL }
        if (official.isEmpty()) return emptyList()

        val workflows = workflowsDir.takeIf { it.isDirectory() }
            ?.listDirectoryEntries()
            ?.filter { it.isRegularFile() && (it.extension == "yml" || it.extension == "yaml") }
            ?.joinToString(separator = "\n") { it.readText() }
            .orEmpty()

        return official.flatMap { check ->
            buildList {
                if (!pluginsDir.resolve(check.module).resolve("src/contractTest").isDirectory()) {
                    add("${check.module} declares OFFICIAL but has no contractTest source set. Add src/contractTest with the contract tests of the plugin.")
                }
                val task = Regex(":reposilite-plugins:${Regex.escape(check.module)}:contractTest(?!\\w)")
                if (!task.containsMatchIn(workflows)) {
                    add("${check.module} declares OFFICIAL but no workflow runs :reposilite-plugins:${check.module}:contractTest.")
                }
            }
        }
    }

    // The plugin id and the tier of every module that has a @Plugin class, keyed by plugin id, in lower case.
    fun tierJson(checks: List<ModuleCheck>): String {
        val entries = checks
            .mapNotNull { check -> check.pluginId?.let { id -> check.declared?.let { tier -> id to tier.name.lowercase() } } }
            .sortedBy { it.first }
        if (entries.isEmpty()) return "{}\n"
        return entries.joinToString(prefix = "{\n", postfix = "\n}\n", separator = ",\n") { (id, tier) ->
            "  \"${id.replace("\\", "\\\\").replace("\"", "\\\"")}\": \"$tier\""
        }
    }

    private fun scanModule(moduleDir: Path): ModuleCheck {
        val sources = moduleDir.resolve("src/main")
            .takeIf { it.isDirectory() }
            ?.walk()
            ?.filter { it.isRegularFile() && (it.extension == "kt" || it.extension == "java") }
            ?.sortedBy { it.toString() }
            ?.map { it.readText() }
            ?.toList()
            .orEmpty()

        val optIn = sources.any(::opensInExperimentalIngot)
        val annotation = sources.firstNotNullOfOrNull { pluginAnnotation.find(it) }
        val arguments = annotation?.groupValues?.get(1).orEmpty()
        val pluginId = nameArgument.find(arguments)?.groupValues?.get(1)
        val declared = annotation?.let {
            val token = tierArgument.find(arguments)?.groupValues?.get(1) ?: return@let Tier.COMMUNITY
            Tier.entries.firstOrNull { it.name == token }
                ?: error("${moduleDir.name}: unknown tier '$token' in the @Plugin annotation")
        }
        return ModuleCheck(moduleDir.name, pluginId, declared, optIn)
    }

    private fun opensInExperimentalIngot(source: String): Boolean =
        optInCall.findAll(source).any { it.groupValues[1].contains("ExperimentalIngot") } ||
            experimentalAnnotation.containsMatchIn(source)
}
