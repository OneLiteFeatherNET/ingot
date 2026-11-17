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

package ingot.tiers

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

// A Core declaration and the jars the Dockerfile copies into /app/bundled-plugins have to match. The Dockerfile and the
// fixture modules live in a temporary directory, so no test reads the real image definition.
class CoreBundledJarTest {

    @TempDir
    lateinit var tempDir: Path

    private val pluginsDir: Path get() = tempDir.resolve("reposilite-plugins")
    private val dockerfile: Path get() = tempDir.resolve("Dockerfile")

    private fun module(name: String, tier: String) {
        val moduleDir = pluginsDir.resolve(name)
        moduleDir.resolve("src/main/kotlin/fixture").createDirectories().resolve("Entry.kt").writeText(
            """
            @Plugin(name = "$name", tier = PluginTier.$tier)
            class Entry
            """.trimIndent(),
        )
        moduleDir.resolve("build.gradle.kts").writeText("")
    }

    private fun dockerfileCopies(vararg jars: String) {
        dockerfile.writeText(
            jars.joinToString(separator = "\n") { jar ->
                "COPY --from=build --chown=reposilite:reposilite /home/ingot-build/reposilite-test/workspace/plugins/$jar /app/bundled-plugins/$jar"
            } + "\n",
        )
    }

    private fun violations(): List<String> = PluginTierCheck.bundledViolations(pluginsDir, dockerfile)

    @Test
    fun coreDeclarationWithoutBundledJarFails() {
        module("core-fixture", "CORE")
        dockerfileCopies()

        val violations = violations()

        assertThat(violations).describedAs("a Core declaration needs a jar in the image").hasSize(1)
        assertThat(violations.single()).describedAs("the violation names the module").contains("core-fixture")
    }

    @Test
    fun bundledJarWithoutCoreOrExperimentalDeclarationFails() {
        module("official-fixture", "OFFICIAL")
        dockerfileCopies("official-fixture.jar")

        val violations = violations()

        assertThat(violations).describedAs("a bundled jar must declare Core or Experimental").hasSize(1)
        assertThat(violations.single()).describedAs("the violation names the bundled jar").contains("official-fixture.jar")
    }

    @Test
    fun bundledJarWithCoreDeclarationPasses() {
        module("core-fixture", "CORE")
        dockerfileCopies("core-fixture.jar")

        assertThat(violations()).describedAs("a Core declaration that matches a bundled jar").isEmpty()
    }

    @Test
    fun bundledJarWithExperimentalDeclarationPasses() {
        module("experimental-fixture", "EXPERIMENTAL")
        dockerfileCopies("experimental-fixture.jar")

        assertThat(violations()).describedAs("an Experimental bundled jar needs no Core declaration").isEmpty()
    }
}
