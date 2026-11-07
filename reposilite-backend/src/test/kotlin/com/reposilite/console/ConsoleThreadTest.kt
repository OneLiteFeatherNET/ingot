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
package com.reposilite.console

import com.reposilite.ReposiliteJournalist
import com.reposilite.console.CommandStatus.FAILED
import com.reposilite.console.api.ReposiliteCommand
import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.status.FailureFacade
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

internal class ConsoleThreadTest {

    @Test
    fun `should keep processing commands after a malformed command`() {
        // given: a console whose input holds a command picocli cannot parse, followed by a valid command
        val executions = mutableListOf<String>()
        val input = "probe -s first-secret -s second-secret\nprobe\n"
        val executor = commandExecutor(byteStream(input))
        executor.registerCommand(ProbeCommand(executions))
        val consoleThread = ConsoleThread(executor, byteStream(input), failureFacade())

        // when: the console thread reads the whole input
        assertThatCode { consoleThread.run() }
            .`as`("a malformed command must not terminate the console thread")
            .doesNotThrowAnyException()

        // then: the command after the malformed one is still executed
        assertThat(executions)
            .`as`("command typed after the malformed one")
            .containsExactly("probe")
    }

    @Test
    fun `should redact a malformed command that picocli cannot parse`() {
        // given: a command with a secret that picocli rejects
        val executor = commandExecutor(byteStream(""))
        executor.registerCommand(ProbeCommand(mutableListOf()))

        // when: the command is prepared for logging
        val preparedCommand = executor.prepare("probe -s hidden-one -s hidden-two")

        // then: the logged form hides the arguments
        assertThat(preparedCommand.redactedCommand)
            .`as`("logged form of a malformed command")
            .isEqualTo("probe <redacted>")
    }

    @Test
    fun `should report a malformed command as failed without echoing its secret`() {
        // given: a command with a secret that picocli rejects
        val executor = commandExecutor(byteStream(""))
        executor.registerCommand(ProbeCommand(mutableListOf()))

        // when: the command is executed
        val result = executor.prepare("probe -s hidden-one -s hidden-two").execute()

        // then: the execution is reported as a failure and the response does not contain the secret
        assertThat(result.status)
            .`as`("status of a malformed command")
            .isEqualTo(FAILED)
        assertThat(result.response.joinToString())
            .`as`("response of a malformed command")
            .doesNotContain("hidden-one")
            .doesNotContain("hidden-two")
    }

    private fun byteStream(input: String): InputStream =
        ByteArrayInputStream(input.toByteArray())

    private fun failureFacade() =
        FailureFacade(InMemoryLogger(), Clock.fixed(Instant.EPOCH, ZoneOffset.UTC))

    private fun commandExecutor(input: InputStream) =
        CommandExecutor(
            journalist = ReposiliteJournalist(InMemoryLogger(), 0, testEnv = true, noColor = false),
            failureFacade = failureFacade(),
            source = input
        )

}

@Command(name = "probe", description = ["Records that it has been executed"])
private class ProbeCommand(private val executions: MutableList<String>) : ReposiliteCommand {

    @Option(names = ["-s", "--secret"], description = ["Secret of the probe"], required = false)
    var secret: String? = null

    override val sensitiveInput: Boolean
        get() = secret != null

    override fun execute(context: CommandContext) {
        executions.add("probe")
    }

}
