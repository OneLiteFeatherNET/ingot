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

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.jline.terminal.Attributes
import org.jline.terminal.Attributes.ControlChar
import org.jline.terminal.Size
import org.jline.terminal.impl.DumbTerminal
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.PrintStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.http.HttpClient
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

private const val CTRL_C = 0x03
private const val CTRL_D = 0x04
private const val SCREEN_COLUMNS = 120
private const val SCREEN_ROWS = 40

private val mapper = ObjectMapper()

private const val WELCOME = """{"type":"welcome","protocol":1,"boot":"b-1","seq":7,"replay":null}"""
private val NAMES = listOf("status", "stop", "shutdown")

private fun result(id: String, exitCode: Int, text: String, stream: String = "stdout") =
    """{"type":"result","id":"$id","status":"${if (exitCode == 0) "SUCCEEDED" else "FAILED"}","exitCode":$exitCode,""" +
        """"output":[{"stream":"$stream","text":"$text"}],"data":null,"node":"ingot-0","confirmed":false}"""

/**
 * A console socket in protocol 1. `stop` asks for confirmation, `fail` answers on stderr, `boom` drops the connection, and
 * every other command runs at once after one log line. Completion answers the names that start with the line.
 */
private fun consoleSocket(): StubSocket = StubSocket { text, peer ->
    val frame = mapper.readTree(text)
    val id = frame.path("id").asText()
    when (frame.path("type").asText()) {
        "hello" -> peer.send(WELCOME)
        "complete" -> {
            val line = frame.path("line").asText()
            val candidates = NAMES.filter { it.startsWith(line) }.joinToString(",") { "\"$it\"" }
            peer.send("""{"type":"complete","id":"$id","replace":{"start":0,"end":${line.length}},"candidates":[$candidates]}""")
        }
        "execute" -> when (val command = frame.path("command").asText()) {
            "stop" -> peer.send("""{"type":"confirm","id":"$id","command":"stop","reason":"stops the server"}""")
            "fail" -> peer.send(result(id, 1, "it failed", stream = "stderr"))
            "boom" -> peer.close(1011)
            else -> {
                peer.send("""{"type":"log","seq":8,"node":"ingot-0","level":"INFO","text":"server log line","dropped":0}""")
                peer.send(result(id, 0, "ran $command"))
            }
        }
        "confirm" -> peer.send(
            if (frame.path("accept").asBoolean()) result(id, 0, "ran stop") else result(id, 3, "stop did not run", stream = "stderr")
        )
    }
}

/** A server that refuses the hello the way [refusal] says and then closes the socket. */
private fun refusingSocket(refusal: String): StubSocket = StubSocket { _, peer ->
    peer.send(refusal)
    peer.close(1008)
}

/** The HTTP side, used only to tell an older server from a refused token. */
private fun httpWithListing(status: Int): StubIngot = StubIngot { status to (if (status == 200) """{"commands":[]}""" else "") }

private class Session(val exit: Int, val out: String, val err: String, val terminalOutput: String)

/**
 * The keyboard of the scripted terminal. It serves the scripted keys and then holds the terminal open until the test
 * releases it, so the terminal never reaches end of input while the REPL still has keys to read. The release has a
 * bound only as a safety net: a REPL that asks for more keys than the script gives fails instead of hanging.
 */
private class ScriptedKeyboard(keys: ByteArray) : InputStream() {

    private val script = ByteArrayInputStream(keys)
    private val released = CountDownLatch(1)

    override fun read(): Int {
        val next = script.read()
        if (next >= 0) return next
        released.await(SAFETY_NET_SECONDS, TimeUnit.SECONDS)
        return -1
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val count = script.read(buffer, offset, length)
        if (count >= 0) return count
        released.await(SAFETY_NET_SECONDS, TimeUnit.SECONDS)
        return -1
    }

    fun release() {
        released.countDown()
    }

    private companion object {
        const val SAFETY_NET_SECONDS = 10L
    }

}

/**
 * Runs the REPL on a JLine terminal whose keyboard is [keys] and whose screen is a buffer. The terminal type is
 * `xterm-256color`, because JLine binds Tab to completion only for a type with key bindings; a `dumb` type has none,
 * and so no completion. Every script ends with Ctrl-D on an empty line, which is how an operator leaves, so the REPL
 * returns without waiting for more input. The console socket is at [socketUrl], and [http] answers the HTTP side.
 */
private fun runScripted(socketUrl: String, http: StubIngot, keys: String, yes: Boolean = false): Session {
    val out = ByteArrayOutputStream()
    val err = ByteArrayOutputStream()
    val screen = ByteArrayOutputStream()
    val keyboard = ScriptedKeyboard((keys + "\u0004").toByteArray(StandardCharsets.UTF_8))
    val terminal = DumbTerminal("scripted", "xterm-256color", keyboard, screen, StandardCharsets.UTF_8)
    terminal.attributes = Attributes(terminal.attributes).apply {
        setControlChar(ControlChar.VINTR, CTRL_C)
        setControlChar(ControlChar.VEOF, CTRL_D)
        setLocalFlag(Attributes.LocalFlag.ISIG, true)
    }
    terminal.size = Size(SCREEN_COLUMNS, SCREEN_ROWS)
    try {
        val timeout = Duration.ofSeconds(5)
        val client = IngotClient(http.url, "ci:ci-secret", timeout, HttpClient.newHttpClient())
        val connector = ConsoleConnector { onLog ->
            ConsoleSocket.open(socketUrl, "ci:ci-secret", "ingot-cli/test", HttpClient.newHttpClient(), timeout, onLog)
        }
        val exit = runRepl(
            client = client,
            connector = connector,
            terminal = terminal,
            context = "test",
            json = false,
            yes = yes,
            out = PrintStream(out, true, StandardCharsets.UTF_8),
            err = PrintStream(err, true, StandardCharsets.UTF_8),
        )
        return Session(
            exit = exit,
            out = out.toString(StandardCharsets.UTF_8),
            err = err.toString(StandardCharsets.UTF_8),
            terminalOutput = screen.toString(StandardCharsets.UTF_8),
        )
    } finally {
        keyboard.release()
        terminal.close()
    }
}

/** Runs [keys] against a protocol 1 socket and an HTTP side that records every request. */
private fun session(keys: String, yes: Boolean = false, check: (Session, StubSocket, StubIngot) -> Unit) {
    consoleSocket().use { socket ->
        httpWithListing(200).use { http ->
            check(runScripted(socket.url, http, keys, yes), socket, http)
        }
    }
}

private fun StubSocket.typed(type: String): List<JsonNode> =
    frames.map { mapper.readTree(it) }.filter { it.path("type").asText() == type }

private fun StubSocket.executed(): List<String> =
    typed("execute").map { it.path("command").asText() }

private fun StubSocket.accepts(): List<Boolean> =
    typed("confirm").map { it.path("accept").asBoolean() }

internal class ReplTest {

    @Test
    fun `should open the session with a hello that names protocol 1 and carries the token`() {
        session("status\n") { _, socket, _ ->
            val hello = mapper.readTree(socket.frames.first())
            assertThat(hello.path("type").asText()).describedAs("first frame").isEqualTo("hello")
            assertThat(hello.path("protocols").map { it.asInt() }).describedAs("protocols").containsExactly(1)
            assertThat(hello.path("token").asText()).describedAs("token").isEqualTo("ci:ci-secret")
        }
    }

    @Test
    fun `should run each line over the socket and send no HTTP request`() {
        session("status\nstatus\n") { session, socket, http ->
            assertThat(socket.executed()).describedAs("commands on the socket").containsExactly("status", "status")
            assertThat(http.requests).describedAs("HTTP requests during a session").isEmpty()
            assertThat(session.out.lines()).describedAs("command output").contains("ran status")
        }
    }

    @Test
    fun `should print the stderr lines of a result on the error stream`() {
        session("fail\n") { session, _, _ ->
            assertThat(session.err).describedAs("stderr").contains("it failed")
            assertThat(session.out).describedAs("stdout").doesNotContain("it failed")
        }
    }

    @Test
    fun `should show server log lines on the terminal and not in the command output`() {
        session("status\n") { session, _, _ ->
            assertThat(session.terminalOutput).describedAs("terminal").contains("server log line")
            assertThat(session.out).describedAs("command output").doesNotContain("server log line")
        }
    }

    @Test
    fun `should send one complete frame on Tab and run the completed name`() {
        session("sta\t\n") { _, socket, _ ->
            assertThat(socket.typed("complete")).describedAs("complete frames").hasSize(1)
            assertThat(socket.executed()).describedAs("the completed name").containsExactly("status")
        }
    }

    @Test
    fun `should send no frame while a line is being typed`() {
        session("sta\u0003") { _, socket, http ->
            assertThat(socket.frames.map { mapper.readTree(it).path("type").asText() }).describedAs("frames").containsExactly("hello")
            assertThat(http.requests).describedAs("HTTP requests").isEmpty()
        }
    }

    @Test
    fun `should discard the current line on Ctrl-C and run the next line only`() {
        session("stop\u0003status\n") { _, socket, _ ->
            assertThat(socket.executed()).describedAs("commands").containsExactly("status")
        }
    }

    @Test
    fun `should leave with exit code 0 on Ctrl-D on an empty line without running the rest of the input`() {
        session("\u0004status\n") { session, socket, _ ->
            assertThat(session.exit).describedAs("exit code").isEqualTo(ExitCode.OK)
            assertThat(socket.executed()).describedAs("commands").isEmpty()
        }
    }

    @Test
    fun `should ask y or N on the terminal and send accept false when the answer is no`() {
        session("stop\nn\n") { session, socket, _ ->
            assertThat(session.terminalOutput).describedAs("terminal").contains("[y/N]")
            assertThat(session.out).describedAs("command output").doesNotContain("[y/N]")
            assertThat(socket.accepts()).describedAs("accept").containsExactly(false)
        }
    }

    @Test
    fun `should send accept false when the confirmation is answered with Enter alone`() {
        session("stop\n\n") { _, socket, _ ->
            assertThat(socket.accepts()).describedAs("accept").containsExactly(false)
        }
    }

    @Test
    fun `should send accept true when the confirmation is answered yes`() {
        session("stop\ny\n") { session, socket, _ ->
            assertThat(socket.accepts()).describedAs("accept").containsExactly(true)
            assertThat(session.out).describedAs("command output").contains("ran stop")
        }
    }

    @Test
    fun `should accept the confirmation without asking when --yes is set`() {
        session("stop\n", yes = true) { session, socket, _ ->
            assertThat(socket.accepts()).describedAs("accept").containsExactly(true)
            assertThat(session.terminalOutput).describedAs("terminal").doesNotContain("[y/N]")
        }
    }

    @Test
    fun `should answer no and keep the REPL running when Ctrl-C or Ctrl-D ends the confirmation`() {
        session("stop\n\u0003stop\n\u0004status\n") { session, socket, _ ->
            assertThat(socket.accepts()).describedAs("accept").containsExactly(false, false)
            assertThat(socket.executed()).describedAs("commands").containsExactly("stop", "stop", "status")
            assertThat(session.exit).describedAs("exit code").isEqualTo(ExitCode.OK)
        }
    }

    @Test
    fun `should report a command as unknown when the connection drops and not send it again`() {
        session("boom\nstatus\n") { session, socket, _ ->
            assertThat(socket.executed()).describedAs("commands sent").containsExactly("boom")
            assertThat(session.err).describedAs("message").contains("may or may not have run")
            assertThat(session.exit).describedAs("exit code").isEqualTo(ExitCode.UNREACHABLE)
        }
    }

    @Test
    fun `should exit with 6 and run nothing when a legacy server refuses the hello and has no command listing`() {
        refusingSocket("""{"status":401,"message":"missing credentials"}""").use { socket ->
            httpWithListing(404).use { http ->
                val session = runScripted(socket.url, http, "status\n")

                assertThat(session.exit).describedAs("exit code").isEqualTo(ExitCode.SERVER_TOO_OLD)
                assertThat(session.err).describedAs("message").contains("ingot exec")
                assertThat(socket.executed()).describedAs("commands").isEmpty()
            }
        }
    }

    @Test
    fun `should exit with 4 when a legacy refusal comes from a server with a command listing`() {
        refusingSocket("""{"status":401,"message":"missing credentials"}""").use { socket ->
            httpWithListing(200).use { http ->
                val session = runScripted(socket.url, http, "status\n")

                assertThat(session.exit).describedAs("exit code").isEqualTo(ExitCode.DENIED)
                assertThat(session.err + session.out).describedAs("no secret in a message").doesNotContain("ci-secret")
            }
        }
    }

    @Test
    fun `should exit with 4 when a version 1 server refuses the token`() {
        refusingSocket("""{"type":"error","code":"unauthorized","message":"invalid credentials"}""").use { socket ->
            httpWithListing(200).use { http ->
                val session = runScripted(socket.url, http, "status\n")

                assertThat(session.exit).describedAs("exit code").isEqualTo(ExitCode.DENIED)
                assertThat(http.requests).describedAs("a typed refusal needs no listing").isEmpty()
            }
        }
    }

    @Test
    fun `should exit with 5 when the console socket cannot be reached`() {
        httpWithListing(200).use { http ->
            val unused = ServerSocket(0, 0, InetAddress.getLoopbackAddress()).use { it.localPort }
            val session = runScripted("http://127.0.0.1:$unused", http, "status\n")

            assertThat(session.exit).describedAs("exit code").isEqualTo(ExitCode.UNREACHABLE)
        }
    }

}
