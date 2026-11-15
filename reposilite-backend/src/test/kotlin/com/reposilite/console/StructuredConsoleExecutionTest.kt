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

package com.reposilite.console

import com.reposilite.ReposiliteJournalist
import com.reposilite.console.CommandStatus.FAILED
import com.reposilite.console.CommandStatus.SUCCEEDED
import com.reposilite.console.api.ReposiliteCommand
import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.status.FailureFacade
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.io.ByteArrayInputStream
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

internal class StructuredConsoleExecutionTest {

    @Test
    fun `should list the version and the name and aliases of every registered command`() {
        // given: a console with a command that has an alias
        val executor = commandExecutor()
        executor.registerCommand(StructuredProbeCommand(mutableListOf()))

        // when: the command listing is requested
        val listing = executor.listCommands()

        // then: the listing has version 1 and describes the command with its alias
        assertThat(listing.version).`as`("listing format version").isEqualTo(1)
        val probe = listing.commands.single { it.name == "probe" }
        assertThat(probe.aliases).`as`("aliases of probe").containsExactly("pr")
        assertThat(probe.description).`as`("description of probe").isEqualTo("Records that it has been executed")
    }

    @Test
    fun `should list the options and positional parameters of a command`() {
        // given: a console with a command that has an option and a positional parameter
        val executor = commandExecutor()
        executor.registerCommand(StructuredProbeCommand(mutableListOf()))

        // when: the command listing is requested
        val probe = executor.listCommands().commands.single { it.name == "probe" }

        // then: the option names and the parameter label are listed
        assertThat(probe.options.single().names).`as`("option names").containsExactly("-s", "--secret")
        assertThat(probe.parameters.single().label).`as`("parameter label").isEqualTo("<target>")
        assertThat(probe.parameters.single().required).`as`("parameter required flag").isTrue
    }

    @Test
    fun `should list a command as sensitive when its input is sensitive`() {
        // given: a console with a command that hides its arguments
        val executor = commandExecutor()
        executor.registerCommand(StructuredVaultCommand())

        // when: the command listing is requested
        val vault = executor.listCommands().commands.single { it.name == "vault" }

        // then: the command is listed as sensitive
        assertThat(vault.sensitive).`as`("sensitive flag of vault").isTrue
    }

    @Test
    fun `should not list a plugin command as dangerous or sensitive by default`() {
        // given: a console with a command that declares neither flag
        val executor = commandExecutor()
        executor.registerCommand(StructuredProbeCommand(mutableListOf()))

        // when: the command listing is requested
        val probe = executor.listCommands().commands.single { it.name == "probe" }

        // then: both flags are false
        assertThat(probe.dangerous).`as`("dangerous flag of probe").isFalse
        assertThat(probe.sensitive).`as`("sensitive flag of probe").isFalse
    }

    @Test
    fun `should answer exit code 0 and stdout lines for a succeeded command`() {
        // given: a console with a command that succeeds and prints one line
        val executor = commandExecutor()
        executor.registerCommand(StructuredProbeCommand(mutableListOf()))

        // when: the command runs in structured mode
        val execution = run(executor.prepare("probe target").executeStructured(confirmed = false))

        // then: the exit code is 0 and the line is on stdout
        assertThat(execution.status).isEqualTo(SUCCEEDED)
        assertThat(execution.exitCode).`as`("exit code of a succeeded command").isEqualTo(0)
        assertThat(execution.output).containsExactly(OutputLine(stream = "stdout", text = "probe ran"))
    }

    @Test
    fun `should answer exit code 1 and stderr lines for a failed command`() {
        // given: a console with a command that fails
        val executor = commandExecutor()
        executor.registerCommand(StructuredFailingCommand())

        // when: the command runs in structured mode
        val execution = run(executor.prepare("failing").executeStructured(confirmed = false))

        // then: the exit code is 1 and the line is on stderr
        assertThat(execution.status).isEqualTo(FAILED)
        assertThat(execution.exitCode).`as`("exit code of a failed command").isEqualTo(1)
        assertThat(execution.output).containsExactly(OutputLine(stream = "stderr", text = "it failed"))
    }

    @Test
    fun `should answer exit code 2 for an unknown command`() {
        // given: a console without the requested command
        val executor = commandExecutor()

        // when: an unknown command runs in structured mode
        val execution = run(executor.prepare("nosuchcommand").executeStructured(confirmed = false))

        // then: the exit code is 2, as for a usage error
        assertThat(execution.exitCode).`as`("exit code of an unknown command").isEqualTo(2)
        assertThat(execution.status).isEqualTo(FAILED)
    }

    @Test
    fun `should answer exit code 2 for a parse error without echoing its secret`() {
        // given: a command with a repeated option, which picocli rejects
        val executor = commandExecutor()
        executor.registerCommand(StructuredProbeCommand(mutableListOf()))

        // when: the malformed command runs in structured mode
        val execution = run(executor.prepare("probe -s hidden-one -s hidden-two").executeStructured(confirmed = false))

        // then: the exit code is 2 and the output does not contain the secret
        assertThat(execution.exitCode).`as`("exit code of a parse error").isEqualTo(2)
        assertThat(execution.output.joinToString { it.text })
            .doesNotContain("hidden-one")
            .doesNotContain("hidden-two")
    }

    @Test
    fun `should strip ANSI sequences from structured output`() {
        // given: a command that prints coloured output
        val executor = commandExecutor()
        executor.registerCommand(StructuredColouredCommand())

        // when: the command runs in structured mode
        val execution = run(executor.prepare("coloured").executeStructured(confirmed = false))

        // then: the output text is plain
        assertThat(execution.output).containsExactly(OutputLine(stream = "stdout", text = "red text"))
    }

    @Test
    fun `should keep ANSI sequences and the usual response in plain mode`() {
        // given: a console with a command that prints coloured output and one that fails to parse
        val executor = commandExecutor()
        executor.registerCommand(StructuredColouredCommand())
        executor.registerCommand(StructuredProbeCommand(mutableListOf()))

        // when: the commands run in plain mode
        val coloured = executor.prepare("coloured").execute()
        val malformed = executor.prepare("probe -s a -s b").execute()
        val unknown = executor.prepare("nosuchcommand").execute()

        // then: the answers are the ones the plain endpoint has always given
        assertThat(coloured.status).isEqualTo(SUCCEEDED)
        assertThat(coloured.response).containsExactly("\u001B[31mred\u001B[0m text")
        assertThat(malformed.status).isEqualTo(FAILED)
        assertThat(malformed.response).containsExactly("Invalid arguments for probe")
        assertThat(unknown.response).containsExactly("Unknown command nosuchcommand")
    }

    @Test
    fun `should hold a dangerous command for confirmation and run nothing`() {
        // given: a console with a dangerous command that records its execution
        val executions = mutableListOf<String>()
        val executor = commandExecutor()
        executor.registerCommand(StructuredDangerousCommand(executions))

        // when: the command runs in structured mode without confirmation
        val answer = executor.prepare("revoke ci").executeStructured(confirmed = false)

        // then: the command is held, reports its reason, and did not run
        assertThat(answer).isInstanceOf(ConfirmationNeeded::class.java)
        val needed = answer as ConfirmationNeeded
        assertThat(needed.command).isEqualTo("revoke")
        assertThat(needed.reason).isEqualTo("Revoke a token")
        assertThat(executions).`as`("executions without confirmation").isEmpty()
    }

    @Test
    fun `should run a dangerous command once it is confirmed`() {
        // given: a console with a dangerous command that records its execution
        val executions = mutableListOf<String>()
        val executor = commandExecutor()
        executor.registerCommand(StructuredDangerousCommand(executions))

        // when: the command runs in structured mode with confirmation
        val execution = run(executor.prepare("revoke ci").executeStructured(confirmed = true))

        // then: the command ran
        assertThat(execution.exitCode).isEqualTo(0)
        assertThat(executions).containsExactly("revoke ci")
    }

    @Test
    fun `should ask for confirmation only for the dangerous form of a command`() {
        // given: a console with a command that is dangerous only for one of its actions
        val executions = mutableListOf<String>()
        val executor = commandExecutor()
        executor.registerCommand(StructuredCacheLikeCommand(executions))

        // when: the command is prepared with its harmless and its dangerous action
        val harmless = executor.prepare("cache stats")
        val dangerous = executor.prepare("cache purge")

        // then: only the purge action is dangerous
        assertThat(harmless.isDangerous()).`as`("stats is dangerous").isFalse
        assertThat(dangerous.isDangerous()).`as`("purge is dangerous").isTrue
        assertThat(run(harmless.executeStructured(confirmed = false)).exitCode).isEqualTo(0)
    }

    @Test
    fun `should never ask for confirmation in plain mode`() {
        // given: a console with a dangerous command that records its execution
        val executions = mutableListOf<String>()
        val executor = commandExecutor()
        executor.registerCommand(StructuredDangerousCommand(executions))

        // when: the dangerous command runs in plain mode
        val response = executor.prepare("revoke ci").execute()

        // then: it runs at once, as it always did
        assertThat(response.status).isEqualTo(SUCCEEDED)
        assertThat(executions).containsExactly("revoke ci")
    }

    private fun run(answer: StructuredAnswer): StructuredExecution =
        (answer as? StructuredRun)?.execution
            ?: throw AssertionError("expected the command to run, but it was held for confirmation: $answer")

    private fun commandExecutor() =
        CommandExecutor(
            journalist = ReposiliteJournalist(InMemoryLogger(), 0, testEnv = true, noColor = false),
            failureFacade = FailureFacade(InMemoryLogger(), Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)),
            source = ByteArrayInputStream(ByteArray(0))
        )

}

@Command(name = "probe", aliases = ["pr"], description = ["Records that it has been executed"])
private class StructuredProbeCommand(private val executions: MutableList<String>) : ReposiliteCommand {

    @Parameters(index = "0", paramLabel = "<target>", description = ["Target of the probe"])
    var target: String? = null

    @Option(names = ["-s", "--secret"], description = ["Secret of the probe"], required = false)
    var secret: String? = null

    override val sensitiveInput: Boolean
        get() = secret != null

    override fun execute(context: CommandContext) {
        executions.add("probe")
        context.append("probe ran")
    }

}

@Command(name = "vault", description = ["Keeps a secret"])
private class StructuredVaultCommand : ReposiliteCommand {

    override val sensitiveInput: Boolean
        get() = true

    override fun execute(context: CommandContext) {
        context.append("vault")
    }

}

@Command(name = "failing", description = ["Always fails"])
private class StructuredFailingCommand : ReposiliteCommand {

    override fun execute(context: CommandContext) {
        context.status = FAILED
        context.append("it failed")
    }

}

@Command(name = "coloured", description = ["Prints colour"])
private class StructuredColouredCommand : ReposiliteCommand {

    override fun execute(context: CommandContext) {
        context.append("\u001B[31mred\u001B[0m text")
    }

}

@Command(name = "revoke", description = ["Revoke a token"])
private class StructuredDangerousCommand(private val executions: MutableList<String>) : ReposiliteCommand {

    @Parameters(index = "0", paramLabel = "<name>", description = ["Name of the token"])
    var name: String? = null

    override val dangerous: Boolean
        get() = true

    override fun execute(context: CommandContext) {
        executions.add("revoke $name")
        context.append("revoked $name")
    }

}

@Command(name = "cache", description = ["Operate on caches"])
private class StructuredCacheLikeCommand(private val executions: MutableList<String>) : ReposiliteCommand {

    @Parameters(index = "0", paramLabel = "<action>", description = ["Action"])
    var action: String? = null

    override val dangerous: Boolean
        get() = true

    override fun isDangerous(arguments: List<String>): Boolean =
        action == "purge"

    override fun execute(context: CommandContext) {
        executions.add("cache $action")
    }

}
