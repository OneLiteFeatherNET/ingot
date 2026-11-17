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
import org.jline.terminal.Attributes
import org.jline.terminal.Attributes.ControlChar
import org.jline.terminal.Size
import org.jline.terminal.impl.DumbTerminal
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.PrintStream
import java.net.http.HttpClient
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

private const val CTRL_C = 0x03
private const val CTRL_D = 0x04
private const val SCREEN_COLUMNS = 120
private const val SCREEN_ROWS = 40

private const val LISTING = """{"commands":[{"name":"status","aliases":[]},{"name":"stop","aliases":["shutdown"]}]}"""

/** The console answers `stop` without `confirm=true` with 409, and runs everything else at once. */
private fun console(request: StubIngot.Request): Pair<Int, String> =
    if (request.body == "stop" && request.query?.contains("confirm=true") != true) {
        409 to """{"command":"stop","reason":"stops the server"}"""
    } else {
        answer(0, text = "ran ${request.body}")
    }

private fun stubWithListing(listingStatus: Int = 200): StubIngot = StubIngot { request ->
    if (request.method == "GET" && request.path == "/api/console/commands") {
        listingStatus to (if (listingStatus == 200) LISTING else "")
    } else {
        console(request)
    }
}

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
 * returns without waiting for more input.
 */
private fun runScripted(stub: StubIngot, keys: String): Session {
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
        val client = IngotClient(stub.url, "ci-secret", Duration.ofSeconds(5), HttpClient.newHttpClient())
        val exit = runRepl(
            client = client,
            terminal = terminal,
            context = "test",
            json = false,
            yes = false,
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

private fun StubIngot.executed(): List<StubIngot.Request> =
    requests.filter { it.path == "/api/console/execute" }

internal class ReplTest {

    @Test
    fun `should complete a command name from the server listing when the prefix is unique`() {
        // given: a server whose listing has `status` and `stop`
        stubWithListing().use { stub ->
            // when: the operator types `sta`, presses Tab and Enter
            runScripted(stub, "sta\t\n")

            // then: the completed name is the one sent to the server
            assertThat(stub.executed().map { it.body }).containsExactly("status")
        }
    }

    @Test
    fun `should complete an alias from the server listing`() {
        // given: a server whose listing has `shutdown` as an alias of `stop`
        stubWithListing().use { stub ->
            // when: the operator types `shu`, presses Tab and Enter
            runScripted(stub, "shu\t\n")

            // then: the alias is completed and sent as a command line
            assertThat(stub.executed().map { it.body }).containsExactly("shutdown")
        }
    }

    @Test
    fun `should send no request while a line is being typed`() {
        // given: a server whose listing answers
        stubWithListing().use { stub ->
            // when: the operator types a partial line and cancels it with Ctrl-C
            runScripted(stub, "sta\u0003")

            // then: only the listing was requested, once, and no command was sent
            assertThat(stub.requests.map { it.path }).containsExactly("/api/console/commands")
        }
    }

    @Test
    fun `should discard the current line on Ctrl-C and run the next line only`() {
        // given: a server that answers commands
        stubWithListing().use { stub ->
            // when: a line is typed, cancelled with Ctrl-C, and then another line is entered
            runScripted(stub, "stop\u0003status\n")

            // then: the cancelled line was never sent
            assertThat(stub.executed().map { it.body }).containsExactly("status")
        }
    }

    @Test
    fun `should leave with exit code 0 on Ctrl-D on an empty line without running the rest of the input`() {
        // given: a server that answers commands
        stubWithListing().use { stub ->
            // when: Ctrl-D is pressed on an empty line, followed by a command
            val session = runScripted(stub, "\u0004status\n")

            // then: the REPL exits with 0 and the later command never runs
            assertThat(session.exit).isEqualTo(ExitCode.OK)
            assertThat(stub.executed()).isEmpty()
        }
    }

    @Test
    fun `should ask y or N on the terminal before a dangerous command`() {
        // given: a server that asks for confirmation of `stop`
        stubWithListing().use { stub ->
            // when: the operator enters `stop` and then `n`
            val session = runScripted(stub, "stop\nn\n")

            // then: the question is on the terminal, not in the command output
            assertThat(session.terminalOutput).contains("[y/N]")
            assertThat(session.out).doesNotContain("[y/N]")
        }
    }

    @Test
    fun `should not run a dangerous command when the confirmation is answered no`() {
        // given: a server that asks for confirmation of `stop`
        stubWithListing().use { stub ->
            // when: the operator enters `stop` and answers `n`
            runScripted(stub, "stop\nn\n")

            // then: the command was sent once without confirmation and never with confirm=true
            assertThat(stub.executed().map { it.body }).containsExactly("stop")
            assertThat(stub.executed().none { it.query?.contains("confirm=true") == true }).isTrue()
        }
    }

    @Test
    fun `should run a dangerous command with confirm=true when the confirmation is answered yes`() {
        // given: a server that asks for confirmation of `stop`
        stubWithListing().use { stub ->
            // when: the operator enters `stop` and answers `y`
            runScripted(stub, "stop\ny\n")

            // then: the confirmed command is sent with confirm=true
            val confirmed = stub.executed().last()
            assertThat(confirmed.body).isEqualTo("stop")
            assertThat(confirmed.query).contains("confirm=true")
        }
    }

    @Test
    fun `should answer no and keep the REPL running when Ctrl-C interrupts the confirmation`() {
        // given: a server that asks for confirmation of `stop`
        stubWithListing().use { stub ->
            // when: the operator enters `stop` and presses Ctrl-C at the question
            val session = runScripted(stub, "stop\n\u0003")

            // then: nothing is confirmed and the REPL leaves normally at end of input
            assertThat(stub.executed().none { it.query?.contains("confirm=true") == true }).isTrue()
            assertThat(session.exit).isEqualTo(ExitCode.OK)
        }
    }

    @Test
    fun `should answer no and keep the REPL running when Ctrl-D ends the confirmation`() {
        // given: a server that asks for confirmation of `stop` and answers `status`
        stubWithListing().use { stub ->
            // when: the operator enters `stop`, presses Ctrl-D at the question, and then enters `status`
            val session = runScripted(stub, "stop\n\u0004status\n")

            // then: nothing is confirmed, the later command still runs, and the REPL leaves normally at end of input
            assertThat(stub.executed().none { it.query?.contains("confirm=true") == true }).isTrue()
            assertThat(stub.executed().map { it.body }).containsExactly("stop", "status")
            assertThat(session.exit).isEqualTo(ExitCode.OK)
        }
    }

    @Test
    fun `should print the older server notice once and keep running commands when the listing is absent`() {
        // given: a server without /api/console/commands
        stubWithListing(listingStatus = 404).use { stub ->
            // when: two commands are entered
            val session = runScripted(stub, "status\nstatus\n")

            // then: the notice appears once, both commands run, and the REPL exits with 0
            assertThat(session.err.lines().count { it.contains("no command listing") }).isEqualTo(1)
            assertThat(stub.executed()).hasSize(2)
            assertThat(session.exit).isEqualTo(ExitCode.OK)
        }
    }

}
