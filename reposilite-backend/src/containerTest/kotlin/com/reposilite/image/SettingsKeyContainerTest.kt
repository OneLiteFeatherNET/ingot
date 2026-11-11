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
import java.nio.file.Path
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * The settings key on the image. A first start on a data directory adopts the key, a restart with the same key on the same directory
 * starts again, and a second instance with another key stops with the key identifier in its output. A key of the wrong length stops
 * the start with one line that names the variable. The keys are fixed, so the runs repeat.
 */
class SettingsKeyContainerTest {

    @TempDir
    lateinit var tempDir: Path

    private val key = Base64.getEncoder().encodeToString(ByteArray(32) { it.toByte() })
    private val otherKey = Base64.getEncoder().encodeToString(ByteArray(32) { (it + 100).toByte() })

    @Test
    @DisplayName("a key of 16 bytes stops the start with exit code 1 and one line naming the variable")
    fun `a key of the wrong length stops the start with one line naming the variable`() {
        val short = Base64.getEncoder().encodeToString(ByteArray(16) { 0x41 })
        val (exitCode, output) = runDocker("-e", "INGOT_SETTINGS_KEY=$short")

        assertThat(exitCode).describedAs("exit code, output was:\n%s", output).isEqualTo(1)
        assertThat(output.lines().filter { "INGOT_SETTINGS_KEY" in it })
            .describedAs("error lines naming the variable, output was:\n%s", output)
            .hasSize(1)
        assertThat(output).describedAs("output must not carry a stack trace").doesNotContain("Exception in thread")
        assertThat(output).describedAs("output must not carry the value").doesNotContain(short)
    }

    @Test
    @DisplayName("a restart with the same key on the same data directory starts and answers the health endpoint")
    fun `a restart with the same key on the same data directory starts again`() {
        DataDirectory(tempDir, ImageUnderTest.server).use { data ->
            serverContainer(ImageUnderTest.server, data = data, environment = mapOf("INGOT_SETTINGS_KEY" to key))
                .startOrExplain()
                .use { assertHealthy(it.baseUrl()) }

            serverContainer(ImageUnderTest.server, data = data, environment = mapOf("INGOT_SETTINGS_KEY" to key))
                .startOrExplain()
                .use { assertHealthy(it.baseUrl()) }
        }
    }

    @Test
    @DisplayName("a second instance with another key stops with exit code 1 and names the stored key identifier")
    fun `a second instance with another key stops and names the key identifier`() {
        DataDirectory(tempDir, ImageUnderTest.server).use { data ->
            serverContainer(ImageUnderTest.server, data = data, environment = mapOf("INGOT_SETTINGS_KEY" to key))
                .startOrExplain()
                .use { assertHealthy(it.baseUrl()) }

            val (exitCode, output) = runDocker(
                "-v", "${data.path}:/app/data",
                "-e", "INGOT_SETTINGS_KEY=$otherKey",
            )

            assertThat(exitCode).describedAs("exit code of the foreign key start, output was:\n%s", output).isEqualTo(1)
            assertThat(output).describedAs("output names the key identifier").contains("key id")
            assertThat(output).describedAs("output must not carry a key").doesNotContain(key).doesNotContain(otherKey)
        }
    }

    private fun assertHealthy(baseUrl: String) {
        val health = Http.get(baseUrl + HEALTH_PATH)
        assertThat(health.status).describedAs("status of %s", HEALTH_PATH).isEqualTo(200)
    }

    /** Runs the image with docker directly, so a refused start, which never reaches a wait strategy, is still read. */
    private fun runDocker(vararg options: String): Pair<Int, String> {
        val process = ProcessBuilder(listOf("docker", "run", "--rm") + options + ImageUnderTest.server.toString())
            .redirectErrorStream(true)
            .start()
        val finished = process.waitFor(3, TimeUnit.MINUTES)
        val output = process.inputStream.bufferedReader().readText()
        if (!finished) process.destroyForcibly()
        assertThat(finished).describedAs("the refused start ended within three minutes").isTrue()
        return process.exitValue() to output
    }

    private companion object {
        const val HEALTH_PATH = "/api/status/health"
    }

}
