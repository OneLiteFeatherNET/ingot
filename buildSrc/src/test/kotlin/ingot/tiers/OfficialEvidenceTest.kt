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

// An Official declaration needs a contractTest source set and a workflow that runs the module by its Gradle path.
// Fixture modules and workflows live in a temporary directory, so no test reads the real repository.
class OfficialEvidenceTest {

    @TempDir
    lateinit var tempDir: Path

    private val pluginsDir: Path get() = tempDir.resolve("reposilite-plugins")
    private val workflowsDir: Path get() = tempDir.resolve(".github/workflows")

    private fun officialModule(name: String, withContractTest: Boolean) {
        val moduleDir = pluginsDir.resolve(name)
        moduleDir.resolve("src/main/kotlin/fixture").createDirectories().resolve("Entry.kt").writeText(
            """
            @Plugin(name = "$name", tier = PluginTier.OFFICIAL)
            class Entry
            """.trimIndent(),
        )
        moduleDir.resolve("src/test/kotlin/fixture").createDirectories().resolve("EntryTest.kt").writeText("")
        moduleDir.resolve("build.gradle.kts").writeText("")
        if (withContractTest) {
            moduleDir.resolve("src/contractTest/kotlin/fixture").createDirectories().resolve("ContractTest.kt").writeText("")
        }
    }

    private fun workflowRunning(vararg gradlePaths: String) {
        workflowsDir.createDirectories().resolve("ci.yml").writeText(
            gradlePaths.joinToString(separator = "\n") { "          run: ./gradlew $it" },
        )
    }

    @Test
    fun officialModuleWithoutContractTestSourceSetFails() {
        officialModule("official-fixture", withContractTest = false)
        workflowRunning(":reposilite-plugins:official-fixture:contractTest")

        val violations = PluginTierCheck.evidenceViolations(pluginsDir, workflowsDir)

        assertThat(violations).describedAs("an Official module needs a contract test").hasSize(1)
        assertThat(violations.single()).describedAs("the violation names the module").contains("official-fixture")
        assertThat(violations.single()).describedAs("the violation names the missing contract tests").contains("contractTest")
    }

    @Test
    fun officialModuleNotRunByAnyWorkflowFails() {
        officialModule("unnamed-fixture", withContractTest = true)
        workflowRunning(":reposilite-plugins:some-other-module:contractTest")

        val violations = PluginTierCheck.evidenceViolations(pluginsDir, workflowsDir)

        assertThat(violations).describedAs("an Official module needs a workflow that runs it").hasSize(1)
        assertThat(violations.single()).describedAs("the violation names the module").contains("unnamed-fixture")
        assertThat(violations.single()).describedAs("the violation names the missing workflow").contains("workflow")
    }

    @Test
    fun officialModuleRunOnlyByUnitTestsFails() {
        officialModule("unit-only-fixture", withContractTest = true)
        workflowRunning(":reposilite-plugins:unit-only-fixture:test")

        val violations = PluginTierCheck.evidenceViolations(pluginsDir, workflowsDir)

        assertThat(violations).describedAs("the unit tests are not the contract tests").hasSize(1)
        assertThat(violations.single()).describedAs("the violation names the missing contractTest task").contains("contractTest")
    }

    @Test
    fun officialModuleWithContractTestAndWorkflowPasses() {
        officialModule("complete-fixture", withContractTest = true)
        workflowRunning(":reposilite-plugins:complete-fixture:contractTest")

        assertThat(PluginTierCheck.evidenceViolations(pluginsDir, workflowsDir))
            .describedAs("a module with both pieces of evidence passes")
            .isEmpty()
    }
}
