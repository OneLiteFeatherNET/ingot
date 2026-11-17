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
package net.onelitefeather.ingot.cli

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.nio.file.Path

private class Run(val exit: Int, val out: String, val err: String)

private fun run(
    args: List<String>,
    url: String,
    stdin: String = "",
    interactive: Boolean = false,
    config: Path,
    token: String? = "ci:ci-secret",
): Run {
    val out = ByteArrayOutputStream()
    val err = ByteArrayOutputStream()
    val env = buildMap {
        put("INGOT_CONFIG", config.toString())
        if (token != null) put("INGOT_TOKEN", token)
    }
    val exit = runCli(
        args = listOf("--url", url) + args,
        env = env,
        stdin = ByteArrayInputStream(stdin.toByteArray(StandardCharsets.UTF_8)),
        out = PrintStream(out, true, StandardCharsets.UTF_8),
        err = PrintStream(err, true, StandardCharsets.UTF_8),
        interactive = interactive,
    )
    return Run(exit, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8))
}

internal class ExecModeTest {

    @TempDir
    lateinit var dir: Path

    private fun config(): Path = dir.resolve("absent.yaml")

    @Test
    fun `should exit 0 and print stdout lines of a succeeded command`() {
        // given: a server that answers success with one stdout line
        StubIngot { answer(0, text = "Ingot status") }.use { stub ->
            // when: the command runs
            val result = run(listOf("exec", "status"), stub.url, config = config())

            // then: the exit code is 0 and the line is on stdout only
            assertThat(result.exit).isEqualTo(0)
            assertThat(result.out).contains("Ingot status")
            assertThat(result.err).isEmpty()
        }
    }

    @Test
    fun `should exit 1 and print stderr lines of a failed command on stderr`() {
        // given: a server that answers a failed command with a stderr line
        StubIngot { answer(1, status = "FAILED", stream = "stderr", text = "it failed") }.use { stub ->
            // when: the command runs
            val result = run(listOf("exec", "level", "nonsense"), stub.url, config = config())

            // then: the exit code is 1 and the line is on stderr
            assertThat(result.exit).isEqualTo(1)
            assertThat(result.err).contains("it failed")
            assertThat(result.out).doesNotContain("it failed")
        }
    }

    @Test
    fun `should exit 2 for an unknown command the server reports`() {
        // given: a server that reports an unknown command with exit code 2
        StubIngot { answer(2, status = "FAILED", stream = "stderr", text = "Unknown command nosuch") }.use { stub ->
            // when / then: the exit code is 2
            assertThat(run(listOf("exec", "nosuch"), stub.url, config = config()).exit).isEqualTo(2)
        }
    }

    @Test
    fun `should not prompt in a pipe and exit 3 for a dangerous command without yes`() {
        // given: a server that holds a dangerous command until it is confirmed
        StubIngot { request ->
            if (request.query?.contains("confirm=true") == true) answer(0) else 409 to """{"error":"confirmationRequired","command":"token-revoke","reason":"Revoke token"}"""
        }.use { stub ->
            // when: the command runs in a pipe, which is not interactive
            val result = run(listOf("exec", "token-revoke", "ci"), stub.url, config = config())

            // then: nothing is confirmed, the exit code is 3, and the command was sent only once and unconfirmed
            assertThat(result.exit).isEqualTo(3)
            assertThat(stub.requests).hasSize(1)
            assertThat(stub.requests.single().query).doesNotContain("confirm=true")
        }
    }

    @Test
    fun `should confirm a dangerous command with yes`() {
        // given: a server that holds a dangerous command until it is confirmed
        StubIngot { request ->
            if (request.query?.contains("confirm=true") == true) answer(0) else 409 to """{"error":"confirmationRequired","command":"token-revoke","reason":"Revoke token"}"""
        }.use { stub ->
            // when: the command runs with --yes
            val result = run(listOf("exec", "--yes", "token-revoke", "ci"), stub.url, config = config())

            // then: the command is confirmed and succeeds
            assertThat(result.exit).isEqualTo(0)
            assertThat(stub.requests.last().query).contains("confirm=true")
        }
    }

    @Test
    fun `should exit 4 when the server refuses the token and keep the secret out of every stream`() {
        // given: a server that refuses the token
        StubIngot { 401 to """{"status":401,"message":"Unauthorized"}""" }.use { stub ->
            // when: the command runs with a token
            val result = run(listOf("exec", "status"), stub.url, config = config(), token = "ci:ci-secret")

            // then: the exit code is 4 and the secret appears on no stream
            assertThat(result.exit).isEqualTo(4)
            assertThat(result.out + result.err).doesNotContain("ci-secret")
            assertThat(stub.requests.single().authorization).isEqualTo("Basic " + java.util.Base64.getEncoder().encodeToString("ci:ci-secret".toByteArray()))
        }
    }

    @Test
    fun `should exit 6 when the server has no structured console API`() {
        // given: an older server that does not know the structured answer
        StubIngot { 404 to "" }.use { stub ->
            // when / then: the exit code is 6
            assertThat(run(listOf("exec", "status"), stub.url, config = config()).exit).isEqualTo(6)
        }
    }

    @Test
    fun `should exit 5 when the server cannot be reached`() {
        // given: a port where nothing listens
        val port = StubIngot { answer(0) }.use { stub -> java.net.URI(stub.url).port }

        // when / then: the exit code is 5
        assertThat(run(listOf("exec", "status"), "http://127.0.0.1:$port", config = config()).exit).isEqualTo(5)
    }

    @Test
    fun `should refuse an argument with a space before sending anything`() {
        // given: a server that would answer
        StubIngot { answer(0) }.use { stub ->
            // when: an argument contains a space
            val result = run(listOf("exec", "status", "two words"), stub.url, config = config())

            // then: the exit code is 2 and no request was sent
            assertThat(result.exit).isEqualTo(2)
            assertThat(stub.requests).isEmpty()
        }
    }

    @Test
    fun `should run the commands of a script on stdin and skip comments and blank lines`() {
        // given: a server that answers each command
        StubIngot { answer(0) }.use { stub ->
            // when: the script is read from standard input
            val result = run(listOf("exec", "-"), stub.url, stdin = "# maintenance\n\nstatus\nhelp\n", config = config())

            // then: the two commands ran in order and the exit code is 0
            assertThat(result.exit).isEqualTo(0)
            assertThat(stub.requests.map { it.body }).containsExactly("status", "help")
        }
    }

    @Test
    fun `should stop at the first failing command of a script unless keep going is given`() {
        // given: a script whose first command fails
        val script = "fail\nstatus\n"
        StubIngot { request -> if (request.body == "fail") answer(1, status = "FAILED", stream = "stderr") else answer(0) }.use { stub ->
            // when: the script runs without --keep-going
            val stopped = run(listOf("exec", "-"), stub.url, stdin = script, config = config())

            // then: the second command was not sent
            assertThat(stopped.exit).isEqualTo(1)
            assertThat(stub.requests.map { it.body }).containsExactly("fail")
        }
        StubIngot { request -> if (request.body == "fail") answer(1, status = "FAILED", stream = "stderr") else answer(0) }.use { stub ->
            // when: the script runs with --keep-going
            val kept = run(listOf("exec", "--keep-going", "-"), stub.url, stdin = script, config = config())

            // then: both commands ran and the exit code is the highest one
            assertThat(kept.exit).isEqualTo(1)
            assertThat(stub.requests.map { it.body }).containsExactly("fail", "status")
        }
    }

    @Test
    fun `should print one JSON object per command with --output json`() {
        // given: a server that answers success on a node
        StubIngot { answer(0, text = "Ingot status") }.use { stub ->
            // when: the command runs with JSON output
            val result = run(listOf("exec", "--output", "json", "status"), stub.url, config = config())

            // then: the output is one JSON object with the exit code and the node
            assertThat(result.exit).isEqualTo(0)
            val line = result.out.trim()
            assertThat(line).startsWith("{").endsWith("}").contains("\"exitCode\":0").contains("\"node\":\"node-1\"")
            assertThat(line.lines()).hasSize(1)
        }
    }

}
