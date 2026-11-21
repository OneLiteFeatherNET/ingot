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

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

// Fixture modules are written into a temporary directory, so no test reads the real plugin sources.
class PluginTierCheckTest {

    @TempDir
    lateinit var tempDir: Path

    // Writes one fixture module with a single source file and returns the plugins directory that holds it.
    private fun module(name: String, source: String): Path {
        val moduleDir = tempDir.resolve(name)
        moduleDir.resolve("src/main/kotlin/fixture").createDirectories().resolve("Entry.kt").writeText(source)
        moduleDir.resolve("build.gradle.kts").writeText("")
        return tempDir
    }

    private fun violationsOf(pluginsDir: Path): List<String> =
        PluginTierCheck.violations(PluginTierCheck.scan(pluginsDir))

    @Test
    fun optInWithoutExperimentalDeclarationFails() {
        val pluginsDir = module(
            "opted-in",
            """
            @file:OptIn(net.onelitefeather.ingot.storage.ExperimentalIngotStorageApi::class)
            @Plugin(name = "opted-in", tier = PluginTier.COMMUNITY)
            class Entry
            """.trimIndent(),
        )

        val violations = violationsOf(pluginsDir)

        assertThat(violations).describedAs("an opt-in must be declared experimental").hasSize(1)
        assertThat(violations.single()).describedAs("the violation names the module").contains("opted-in")
        assertThat(violations.single()).describedAs("the violation names the missing declaration").contains("EXPERIMENTAL")
    }

    @Test
    fun experimentalDeclarationWithoutOptInFails() {
        val pluginsDir = module(
            "declared-only",
            """
            @Plugin(name = "declared-only", tier = PluginTier.EXPERIMENTAL)
            class Entry
            """.trimIndent(),
        )

        val violations = violationsOf(pluginsDir)

        assertThat(violations).describedAs("an experimental declaration needs an opt-in").hasSize(1)
        assertThat(violations.single()).describedAs("the violation names the module").contains("declared-only")
    }

    @Test
    fun moduleWithNeitherOptInNorExperimentalDeclarationPasses() {
        val pluginsDir = module(
            "community",
            """
            @Plugin(name = "community", tier = PluginTier.COMMUNITY)
            class Entry
            """.trimIndent(),
        )

        assertThat(violationsOf(pluginsDir)).describedAs("a community module needs no evidence").isEmpty()
    }

    @Test
    fun optInWithoutPluginClassFails() {
        val pluginsDir = module(
            "storage-cache",
            """
            @file:OptIn(net.onelitefeather.ingot.storage.ExperimentalIngotStorageApi::class)
            class CacheStorageProvider
            """.trimIndent(),
        )

        val violations = violationsOf(pluginsDir)

        assertThat(violations).describedAs("an opt-in without any plugin class cannot declare its tier").hasSize(1)
        assertThat(violations.single()).contains("storage-cache")
    }

    @Test
    fun moduleWithoutPluginClassAndWithoutOptInPasses() {
        val pluginsDir = module(
            "library",
            """
            class Helper
            """.trimIndent(),
        )

        assertThat(violationsOf(pluginsDir)).describedAs("a module without an opt-in needs no tier").isEmpty()
    }

    @Test
    fun embeddedModuleMayOptInWithoutExperimentalDeclaration() {
        val pluginsDir = module(
            "embedded",
            """
            @Plugin(name = "embedded")
            class Entry {
                @OptIn(net.onelitefeather.ingot.plugin.ExperimentalIngotPluginApi::class)
                fun load() = Unit
            }
            """.trimIndent(),
        )

        val violations = PluginTierCheck.violations(PluginTierCheck.scan(pluginsDir, embeddedModules = setOf("embedded")))

        assertThat(violations)
            .describedAs("a module the server embeds is Core by origin, so its opt-in needs no Experimental declaration")
            .isEmpty()
    }

    @Test
    fun embeddedModulesAreReadFromTheBackendBuildScript() {
        val buildScript = tempDir.resolve("build.gradle.kts")
        buildScript.writeText(
            """
            val embeddedPluginProjects: List<String> = listOf(":reposilite-plugins:cleanup-plugin", ":reposilite-plugins:javadoc-plugin")
            val other = listOf(":reposilite-plugins:groovy-plugin")
            """.trimIndent(),
        )

        assertThat(PluginTierCheck.embeddedModules(buildScript))
            .describedAs("only the modules of embeddedPluginProjects count as embedded")
            .containsExactlyInAnyOrder("cleanup-plugin", "javadoc-plugin")
    }

    @Test
    fun tierFileMapsEachPluginIdToItsLowerCaseTier() {
        val pluginsDir = module(
            "official",
            """
            @Plugin(name = "official-plugin", tier = PluginTier.OFFICIAL)
            class Entry
            """.trimIndent(),
        )

        val json = PluginTierCheck.tierJson(PluginTierCheck.scan(pluginsDir))

        assertThat(json)
            .describedAs("the file maps each plugin id to its tier in lower case")
            .isEqualTo("{\n  \"official-plugin\": \"official\"\n}\n")
    }
}
