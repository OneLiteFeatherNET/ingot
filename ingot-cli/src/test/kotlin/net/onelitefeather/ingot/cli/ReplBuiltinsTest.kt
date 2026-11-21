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

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.jline.terminal.Attributes
import org.jline.terminal.Attributes.ControlChar
import org.jline.terminal.Size
import org.jline.terminal.Terminal
import org.jline.terminal.impl.DumbTerminal
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.PrintStream
import java.net.http.HttpClient
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

private val json = ObjectMapper()

/** A protocol 1 console socket that answers every command with one stdout line naming [name]. */
private fun namedSocket(name: String): StubSocket = StubSocket { text, peer ->
    val frame = json.readTree(text)
    val id = frame.path("id").asText()
    when (frame.path("type").asText()) {
        "hello" -> peer.send("""{"type":"welcome","protocol":1,"boot":"b-1","seq":0,"replay":null}""")
        "execute" -> peer.send(
            """{"type":"result","id":"$id","status":"SUCCEEDED","exitCode":0,""" +
                """"output":[{"stream":"stdout","text":"ran ${frame.path("command").asText()} on $name"}],"data":null,"node":"$name","confirmed":false}"""
        )
    }
}

private fun StubSocket.commands(): List<String> =
    frames.map { json.readTree(it) }.filter { it.path("type").asText() == "execute" }.map { it.path("command").asText() }

/** Serves the scripted keys, then holds the terminal open until released, so the REPL never sees end of input early. */
private class Keyboard(keys: String) : InputStream() {

    private val script = ByteArrayInputStream((keys + "\u0004").toByteArray(StandardCharsets.UTF_8))
    private val released = CountDownLatch(1)

    override fun read(): Int = script.read().takeIf { it >= 0 } ?: endOfInput()

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        script.read(buffer, offset, length).takeIf { it >= 0 } ?: endOfInput()

    private fun endOfInput(): Int {
        released.await(10, TimeUnit.SECONDS)
        return -1
    }

    fun release() = released.countDown()

}

private class Outcome(val exit: Int, val out: String, val err: String)

/** Runs [block] on a scripted terminal that types [keys] and ends with Ctrl-D, and collects both output streams. */
private fun scripted(keys: String, block: (Terminal, PrintStream, PrintStream) -> Int): Outcome {
    val out = ByteArrayOutputStream()
    val err = ByteArrayOutputStream()
    val keyboard = Keyboard(keys)
    val terminal = DumbTerminal("scripted", "xterm-256color", keyboard, ByteArrayOutputStream(), StandardCharsets.UTF_8)
    terminal.attributes = Attributes(terminal.attributes).apply {
        setControlChar(ControlChar.VINTR, 0x03)
        setControlChar(ControlChar.VEOF, 0x04)
        setLocalFlag(Attributes.LocalFlag.ISIG, true)
    }
    terminal.size = Size(120, 40)
    try {
        val exit = block(terminal, PrintStream(out, true, StandardCharsets.UTF_8), PrintStream(err, true, StandardCharsets.UTF_8))
        return Outcome(exit, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8))
    } finally {
        keyboard.release()
        terminal.close()
    }
}

/** The REPL on one socket, in the context `test`, with text output. */
private fun repl(socket: StubSocket, keys: String): Outcome = scripted(keys) { terminal, out, err ->
    val timeout = Duration.ofSeconds(5)
    val client = IngotClient(socket.url, "ci:ci-secret", timeout, HttpClient.newHttpClient())
    val connector = ConsoleConnector { resume, onLog -> ConsoleSocket.open(socket.url, "ci:ci-secret", "ingot-cli/test", HttpClient.newHttpClient(), timeout, onLog, resume) }
    runRepl(client, connector, terminal, "test", json = false, yes = false, out = out, err = err)
}

internal class ReplBuiltinsTest {

    @TempDir
    lateinit var dir: Path

    @Test
    fun `should print one JSON object per command after output json`() {
        namedSocket("a").use { socket ->
            val outcome = repl(socket, ":output json\nstatus\n")

            val line = json.readTree(outcome.out.lines().first { it.isNotBlank() })
            assertThat(line.path("command").asText()).describedAs("command of the JSON line").isEqualTo("status")
            assertThat(line.path("node").asText()).describedAs("node of the JSON line").isEqualTo("a")
        }
    }

    @Test
    fun `should print text again after output text`() {
        namedSocket("a").use { socket ->
            val outcome = repl(socket, ":output json\n:output text\nstatus\n")

            assertThat(outcome.out.lines()).describedAs("command output").contains("ran status on a")
        }
    }

    @Test
    fun `should refuse an output format it does not know and send nothing`() {
        namedSocket("a").use { socket ->
            val outcome = repl(socket, ":output yaml\n")

            assertThat(outcome.err).describedAs("message").contains(":output text|json")
            assertThat(socket.commands()).describedAs("commands").isEmpty()
        }
    }

    @Test
    fun `should name the current context for context without a name`() {
        namedSocket("a").use { socket ->
            val outcome = repl(socket, ":context\n")

            assertThat(outcome.err).describedAs("message").contains("test")
            assertThat(socket.commands()).describedAs("commands").isEmpty()
        }
    }

    @Test
    fun `should list every builtin for an unknown builtin`() {
        namedSocket("a").use { socket ->
            val outcome = repl(socket, ":nope\n")

            assertThat(outcome.err).describedAs("message").contains(":context", ":output", ":quit")
        }
    }

    @Test
    fun `should run the next lines in the context that context names`() {
        namedSocket("a").use { first ->
            namedSocket("b").use { second ->
                val outcome = cli(contexts(first, second), "status\n:context b\nstatus\n")

                assertThat(first.commands()).describedAs("commands in context a").containsExactly("status")
                assertThat(second.commands()).describedAs("commands in context b").containsExactly("status")
                assertThat(outcome.out.lines()).describedAs("command output").contains("ran status on a", "ran status on b")
                assertThat(outcome.exit).describedAs("exit code").isEqualTo(ExitCode.OK)
            }
        }
    }

    @Test
    fun `should stay in the current context when context names one the config file does not have`() {
        namedSocket("a").use { first ->
            namedSocket("b").use { second ->
                val outcome = cli(contexts(first, second), ":context nope\nstatus\n")

                assertThat(outcome.err).describedAs("message").contains("nope")
                assertThat(first.commands()).describedAs("commands in context a").containsExactly("status")
                assertThat(second.frames).describedAs("frames in context b").isEmpty()
            }
        }
    }

    /** A config file whose current context `a` points at [first] and whose context `b` points at [second]. */
    private fun contexts(first: StubSocket, second: StubSocket): Path =
        Files.writeString(
            dir.resolve("config.yaml"),
            "current: a\ncontexts:\n  - name: a\n    url: ${first.url}\n  - name: b\n    url: ${second.url}\n",
        )

    private fun cli(config: Path, keys: String): Outcome = scripted(keys) { terminal, out, err ->
        runCli(
            args = emptyList(),
            env = mapOf("INGOT_CONFIG" to config.toString(), "INGOT_TOKEN" to "ci:ci-secret"),
            stdin = ByteArrayInputStream(ByteArray(0)),
            out = out,
            err = err,
            interactive = true,
            terminal = { terminal },
        )
    }

}
