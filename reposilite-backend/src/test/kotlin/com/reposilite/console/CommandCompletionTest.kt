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

/** Completion of a command line from the registered commands: names, aliases, options and fixed values (task 4.8). */
internal class CommandCompletionTest {

    private val executor = CommandExecutor(
        journalist = ReposiliteJournalist(InMemoryLogger(), 0, testEnv = true, noColor = true),
        failureFacade = FailureFacade(InMemoryLogger(), Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)),
        source = ByteArrayInputStream(ByteArray(0)),
    ).also {
        it.registerCommand(CompletionTokenCommand())
        it.registerCommand(CompletionTailCommand())
    }

    private fun complete(line: String, cursor: Int = line.length): Completion =
        completeCommand(executor.getCommands().values, line, cursor)

    @Test
    fun `completes the first word from the command names and aliases`() {
        // when: the line holds the start of a command name
        val completion = complete("to")

        // then: the name and the alias that start with it are offered, and the whole word is replaced
        assertThat(completion.candidates).`as`("candidates").containsExactly("token", "tokens")
        assertThat(completion.start).`as`("replace start").isEqualTo(0)
        assertThat(completion.end).`as`("replace end").isEqualTo(2)
    }

    @Test
    fun `offers every command name and alias for an empty line`() {
        // when: the line is empty
        val completion = complete("")

        // then: every name and alias is offered once, sorted
        assertThat(completion.candidates).`as`("candidates").containsExactly("tail", "token", "tokens")
    }

    @Test
    fun `completes the options of a command after a dash`() {
        // when: the word after the command starts with a dash
        val completion = complete("token --")

        // then: the long options of that command are offered, and only the word after the space is replaced
        assertThat(completion.candidates).`as`("candidates").containsExactly("--format", "--verbose")
        assertThat(completion.start).`as`("replace start").isEqualTo(6)
        assertThat(completion.end).`as`("replace end").isEqualTo(8)
    }

    @Test
    fun `completes the fixed values of the positional parameter at the cursor`() {
        // when: the second word of the token command is completed
        val completion = complete("token r")

        // then: the fixed values of its first positional parameter that start with the word are offered
        assertThat(completion.candidates).`as`("candidates").containsExactly("regenerate", "revoke")
    }

    @Test
    fun `completes the fixed values of an option that takes a value`() {
        // when: the word after an option that takes a value is completed
        val completion = complete("token --format ")

        // then: the fixed values of that option are offered, not the positional ones
        assertThat(completion.candidates).`as`("candidates").containsExactly("json", "text")
    }

    @Test
    fun `completes the word before the cursor and ignores the rest of the line`() {
        // when: the cursor stands after "ta" in a longer line
        val completion = complete("ta some argument", cursor = 2)

        // then: the command name is completed and the range ends at the cursor
        assertThat(completion.candidates).`as`("candidates").containsExactly("tail")
        assertThat(completion.end).`as`("replace end").isEqualTo(2)
    }

    @Test
    fun `offers nothing after an unknown command`() {
        // when: the first word names no command
        val completion = complete("unknown ar")

        // then: no candidate is offered
        assertThat(completion.candidates).`as`("candidates").isEmpty()
    }

}

@Command(name = "token", aliases = ["tokens"], description = ["Manages tokens"])
private class CompletionTokenCommand : ReposiliteCommand {

    @Parameters(index = "0", paramLabel = "<action>", completionCandidates = TokenActions::class, description = ["The action"])
    var action: String? = null

    @Option(names = ["--format"], completionCandidates = TokenFormats::class, description = ["The output format"])
    var format: String? = null

    @Option(names = ["-v", "--verbose"], description = ["Print more"])
    var verbose: Boolean = false

    override fun execute(context: CommandContext) {}

}

@Command(name = "tail", description = ["Prints the last lines"])
private class CompletionTailCommand : ReposiliteCommand {

    override fun execute(context: CommandContext) {}

}

private class TokenActions : Iterable<String> {
    override fun iterator(): Iterator<String> = listOf("list", "regenerate", "revoke").iterator()
}

private class TokenFormats : Iterable<String> {
    override fun iterator(): Iterator<String> = listOf("json", "text").iterator()
}
