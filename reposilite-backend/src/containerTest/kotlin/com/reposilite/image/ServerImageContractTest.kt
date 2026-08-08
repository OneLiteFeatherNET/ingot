/*
 * Copyright (c) 2023 dzikoysk
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

package com.reposilite.image

import java.nio.file.Path
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.startupcheck.OneShotStartupCheckStrategy

/**
 * The server image has to stay a drop-in replacement for the upstream Reposilite image:
 * pointing an existing deployment at it must cost one changed image reference and nothing
 * else. See the backwards compatibility section in CLAUDE.md.
 *
 * Every case here stands for a deployment that used to work and would break silently. None
 * of them is visible in a build: the regressions that prompted these tests all produced an
 * image that built cleanly, started cleanly, and served artifacts.
 */
class ServerImageContractTest {

    @Test
    @DisplayName("the jar is reachable under its upstream name")
    fun `jar is reachable under its upstream name`() {
        // A deployment that overrides entrypoint or command names the jar itself.
        val resolved = readlink("/app/reposilite.jar")

        assertThat(resolved).isEqualTo("/app/ingot.jar")
    }

    @Test
    @DisplayName("uid 1000 is free, so PUID=1000 can work")
    fun `uid 1000 is free`() {
        // The base image ships an `ubuntu` account holding uid and gid 1000, which is the
        // first id a desktop Linux hands out and therefore the most common PUID there is.
        // Occupied, the entrypoint dies on "id already in use" before the server starts.
        val accounts = readFromImage("/etc/passwd").lines().filter { it.isNotBlank() }

        assertThat(accounts.map { it.split(":").getOrNull(2) })
            .describedAs("some account in %s holds uid 1000", accounts)
            .doesNotContain("1000")
    }

    @Test
    @DisplayName("a root start adopts a foreign-owned volume and honours PUID, PGID and REPOSILITE_OPTS")
    fun `root start adopts a foreign owned volume`(@TempDir directory: Path) {
        // This is the upgrade path: an existing data directory, owned by whoever owned it
        // before, handed over unchanged. Only the entrypoint's root path can take it over,
        // which is why the image deliberately declares no USER.
        DataDirectory(directory, ImageUnderTest.server).use { data ->
            data.chownTo(4242, 4242)

            // The exposed port is the one REPOSILITE_OPTS asks for, and it is not the image
            // default. Answering at all therefore proves the pre-Ingot spelling of the
            // variable was read: had it been ignored, the server would be on 8080.
            serverContainer(
                image = ImageUnderTest.server,
                data = data,
                port = REQUESTED_PORT,
                environment = mapOf(
                    "PUID" to "1000",
                    "PGID" to "1000",
                    "REPOSILITE_OPTS" to "--port $REQUESTED_PORT",
                ),
            ).use { server ->
                server.start()

                assertThat(data.ownerOf("configuration.cdn"))
                    .describedAs("PUID and PGID were read and then ignored")
                    .isEqualTo("1000:1000")
            }
        }
    }

    @Test
    @DisplayName("the server still serves the dashboard itself")
    fun `server still serves the dashboard itself`(@TempDir directory: Path) {
        // Splitting the dashboard into its own image must not have turned the default
        // deployment into one that needs a second container to show anything.
        DataDirectory(directory, ImageUnderTest.server).use { data ->
            serverContainer(ImageUnderTest.server, data).use { server ->
                server.start()
                val page = Http.get(server.baseUrl())

                assertThat(page.body).contains("<div id=\"app\"")
                assertThat(page.body)
                    .describedAs("an unsubstituted placeholder means the asset URLs cannot resolve")
                    .doesNotContain("{{REPOSILITE.")

                // Follow the bundle the page asks for rather than assuming its name.
                val bundle = BUNDLE_REFERENCE.find(page.body)?.groupValues?.get(1)
                assertThat(bundle).describedAs("the dashboard references no bundle").isNotNull()
                assertThat(Http.get(server.baseUrl() + bundle).isSuccessful).isTrue()
            }
        }
    }

    @Test
    @DisplayName("an unprivileged start still works")
    fun `unprivileged start still works`(@TempDir directory: Path) {
        // Not part of the upstream contract, but the reason the image ships the account,
        // and the setup recommended for a volume that is already owned correctly.
        DataDirectory(directory, ImageUnderTest.server).use { data ->
            data.chownTo(977, 977)

            serverContainer(ImageUnderTest.server, data, runAs = "977:977")
                .withCreateContainerCmdModifier { it.hostConfig?.withSecurityOpts(listOf("no-new-privileges:true")) }
                .use { server ->
                    server.start()

                    assertThat(Http.get(server.baseUrl()).isSuccessful).isTrue()
                }
        }
    }

    private fun readlink(path: String): String = runInImage("readlink", "-f", path).trim()

    private fun readFromImage(path: String): String = runInImage("cat", path)

    /**
     * Runs one command in the image and returns what it printed.
     *
     * Output rather than an exit code: a one-shot container that exits non-zero is torn
     * down before its state can be inspected, so asking for the code is a race, while what
     * it printed is kept in the logs either way.
     */
    private fun runInImage(vararg command: String): String =
        GenericContainer(ImageUnderTest.server)
            .withCreateContainerCmdModifier { it.withUser("0:0").withEntrypoint(command.first()) }
            .withCommand(*command.drop(1).toTypedArray())
            .withStartupCheckStrategy(OneShotStartupCheckStrategy())
            .use { container ->
                container.start()
                container.logs
            }

    private companion object {
        const val REQUESTED_PORT = 8123
        val BUNDLE_REFERENCE = Regex("""src="([^"]*index-[^"]*\.js)"""")
    }
}
