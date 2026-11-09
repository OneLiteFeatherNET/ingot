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

package com.reposilite.image

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.testcontainers.containers.BindMode
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * A local setting read from a file, as a container platform mounts a secret: `INGOT_LOCAL_<KEY>_FILE` names the file, and the
 * server reads its content as the value. A missing file stops the start with one line, and a file with the value answers on it.
 */
class FileValuesContainerTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    @DisplayName("a missing file named by INGOT_LOCAL_KEYPASSWORD_FILE stops the start with exit code 1 and one line naming the variable")
    fun `a missing secret file stops the start with exit code 1 and one line naming the variable`() {
        // The container is run with docker directly: a refused start never reaches a wait strategy, and testcontainers
        // forgets a container whose start failed, so its exit code and logs are read from the docker CLI instead.
        val process = ProcessBuilder(
            "docker", "run", "--rm",
            "-e", "INGOT_LOCAL_KEYPASSWORD_FILE=/run/secrets/missing",
            ImageUnderTest.server.toString(),
        )
            .redirectErrorStream(true)
            .start()
        val finished = process.waitFor(3, TimeUnit.MINUTES)
        val output = process.inputStream.bufferedReader().readText()
        if (!finished) process.destroyForcibly()

        assertThat(finished).describedAs("the refused start ended within three minutes").isTrue()
        assertThat(process.exitValue()).describedAs("exit code of the refused start, output was:\n%s", output).isEqualTo(1)
        assertThat(output.lines().filter { "INGOT_LOCAL_KEYPASSWORD_FILE" in it })
            .describedAs("error lines naming the variable, output was:\n%s", output)
            .hasSize(1)
        assertThat(output).describedAs("output must not carry a stack trace").doesNotContain("Exception in thread")
    }

    @Test
    @DisplayName("INGOT_LOCAL_PORT_FILE naming a file with 8181 makes the server answer on port 8181")
    fun `a port read from a mounted file makes the server answer on that port`() {
        // The secret is written to the host and mounted read-only at the path the variable names, as an orchestrator does.
        val secret = tempDir.resolve("port").also { Files.writeString(it, "8181\n") }

        serverContainer(
            image = ImageUnderTest.server,
            port = FILE_PORT,
            environment = mapOf("INGOT_LOCAL_PORT_FILE" to "/run/secrets/port"),
        )
            .withFileSystemBind(secret.toString(), "/run/secrets/port", BindMode.READ_ONLY)
            .startOrExplain()
            .use { server ->
                val health = Http.get(server.baseUrl(FILE_PORT) + HEALTH_PATH)
                assertThat(health.status)
                    .describedAs("status of %s on port %d with the port read from a file", HEALTH_PATH, FILE_PORT)
                    .isEqualTo(200)
            }
    }

    private companion object {
        const val HEALTH_PATH = "/api/status/health"
        const val FILE_PORT = 8181
    }

}
