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
package net.onelitefeather.ingot.cli

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit

/**
 * Runs the shaded jar the way a user and the image do, as `java -jar ingot-cli-<version>.jar`, in a process of its own
 * with standard input, output and error as pipes, so no terminal is attached. It checks the entry point, the manifest and
 * the dependencies the jar carries, which the in-process tests cannot see. The jar comes from the `jarSmokeTest` task,
 * which builds it first and names it in the system property `ingot.cli.jar`.
 */
@Tag("jar")
internal class ShadedJarSmokeTest {

    @TempDir
    lateinit var dir: Path

    private class Finished(val exit: Int, val out: String, val err: String)

    private fun jar(): Path {
        val path = System.getProperty("ingot.cli.jar")
        assertThat(path).describedAs("the system property ingot.cli.jar names the shaded jar; run the jarSmokeTest task").isNotBlank()
        return Paths.get(path).also { assertThat(it).describedAs("the shaded jar").isRegularFile() }
    }

    /** Runs the jar with [args] and [env] on top of an environment that holds no INGOT_ variable of the machine. */
    private fun runJar(args: List<String>, env: Map<String, String> = emptyMap()): Finished {
        val java = ProcessHandle.current().info().command().orElse("java")
        val out = dir.resolve("out.txt")
        val err = dir.resolve("err.txt")
        val process = ProcessBuilder(listOf(java, "-jar", jar().toString()) + args)
            .directory(dir.toFile())
            .redirectInput(ProcessBuilder.Redirect.PIPE)
            .redirectOutput(out.toFile())
            .redirectError(err.toFile())
            .apply {
                environment().keys.removeIf { it.startsWith("INGOT_") || it == "NO_COLOR" }
                environment()["INGOT_CONFIG"] = dir.resolve("absent.yaml").toString()
                environment()["XDG_STATE_HOME"] = dir.resolve("state").toString()
                environment().putAll(env)
            }
            .start()
        process.outputStream.close()
        val finished = process.waitFor(60, TimeUnit.SECONDS)
        if (!finished) process.destroyForcibly()
        assertThat(finished).describedAs("the jar exits on its own").isTrue()
        return Finished(process.exitValue(), Files.readString(out, StandardCharsets.UTF_8), Files.readString(err, StandardCharsets.UTF_8))
    }

    @Test
    fun `runs exec status against a server and prints its output`() {
        StubIngot { answer(exitCode = 0, text = "Ingot status line") }.use { server ->
            val run = runJar(listOf("exec", "status"), mapOf("INGOT_URL" to server.url, "INGOT_TOKEN" to "ci:ci-secret"))

            assertThat(run.exit).describedAs("exit code of exec status, stderr: ${run.err}").isEqualTo(ExitCode.OK)
            assertThat(run.out).describedAs("stdout holds the command output").isEqualTo("Ingot status line\n")
            assertThat(server.requests.map { it.path }).describedAs("the requests the jar made").containsExactly("/api/console/execute")
            assertThat(server.requests.single().body).describedAs("the command line sent").isEqualTo("status")
        }
    }

    @Test
    fun `does not ask for a confirmation in a pipe and exits with 3`() {
        val confirmationRequired = 409 to """{"error":"confirmationRequired","command":"stop","reason":"Shutdown server"}"""
        StubIngot { confirmationRequired }.use { server ->
            val run = runJar(listOf("exec", "stop"), mapOf("INGOT_URL" to server.url, "INGOT_TOKEN" to "ci:ci-secret"))

            assertThat(run.exit).describedAs("exit code of an unconfirmed command, stderr: ${run.err}").isEqualTo(ExitCode.CONFIRMATION_REQUIRED)
            assertThat(run.err).describedAs("no prompt without a terminal").doesNotContain("[y/N]")
            assertThat(server.requests).describedAs("the command is not sent again with a confirmation").hasSize(1)
        }
    }

    @Test
    fun `prints the usage and exits with 2 when started without words and without a terminal`() {
        val run = runJar(emptyList(), mapOf("INGOT_URL" to "http://127.0.0.1:9", "INGOT_TOKEN" to "ci:ci-secret"))

        assertThat(run.exit).describedAs("exit code without a terminal, stderr: ${run.err}").isEqualTo(ExitCode.USAGE)
        assertThat(run.err).describedAs("the usage on stderr").contains("Usage: ingot")
        assertThat(run.out).describedAs("nothing on stdout").isEmpty()
    }

}
