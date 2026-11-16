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

package com.reposilite.plugin.migration

import com.reposilite.plugin.contract.PluginContractServer
import com.reposilite.token.ExportService
import com.reposilite.token.Route
import com.reposilite.token.RoutePermission.WRITE
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * The contract of the migration plugin: a fresh server that loads the shipped jar of this module and finds a legacy
 * `tokens.dat` in its working directory exports the tokens with their routes. The jar path comes from the `contractTest`
 * task, which builds the shadow jar first.
 */
internal class MigrationPluginContractTest {

    @TempDir
    lateinit var workingDirectory: Path

    @Test
    fun `should export the token of a legacy tokens dat file`() {
        // given: a legacy tokens.dat in the working directory, written before the server starts
        val plugin = checkNotNull(System.getProperty(PLUGIN_JAR_PROPERTY)) { "$PLUGIN_JAR_PROPERTY is not set, run the contractTest task" }
        val fixture = checkNotNull(javaClass.getResourceAsStream(TOKENS_FIXTURE)) { "Missing test resource $TOKENS_FIXTURE" }

        PluginContractServer(workingDirectory, listOf(Path.of(plugin))).start { directory ->
            fixture.use { Files.copy(it, directory.resolve("tokens.dat")) }
        }.use {
            // when: the server has started, so the plugin has run during its initialisation
            // then: the plugin has exported the token of the fixture with its route
            val exported = workingDirectory.resolve("tokens.json")
            assertThat(exported)
                .describedAs("tokens.json written by the migration plugin, the plugin did not run or did not export")
                .exists()

            val tokens = checkNotNull(ExportService().importFromFile(exported).orNull()) { "tokens.json must be readable by the server" }

            assertThat(tokens.map { it.accessToken.name })
                .describedAs("token names exported from tokens.dat")
                .contains("private")
            assertThat(tokens.first { it.accessToken.name == "private" }.routes)
                .describedAs("routes of token 'private' exported from tokens.dat")
                .contains(Route("/private", WRITE))
        }
    }

    private companion object {
        const val PLUGIN_JAR_PROPERTY = "ingot.contract.plugin"
        const val TOKENS_FIXTURE = "/tokens.dat"
    }

}
