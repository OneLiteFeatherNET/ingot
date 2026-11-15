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
import com.reposilite.console.api.ExecutionResponse
import com.reposilite.console.api.ReposiliteCommand
import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.status.FailureFacade
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.io.ByteArrayInputStream
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicBoolean

internal class CommandExecutorConcurrencyTest {

    @Test
    fun `should give a parked command the arguments it was parsed with when another call runs meanwhile`() {
        // given: a command that parks in its execution, after its arguments were parsed, until it is released
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val executor = commandExecutor()
        executor.registerCommand(ValueProbe(entered, release))
        val pool = Executors.newFixedThreadPool(2)

        try {
            val first = pool.submit<ExecutionResponse> { executor.execute("probe --value first") }
            assertThat(entered.await(FIXTURE_TIMEOUT_SECONDS, SECONDS))
                .`as`("first call reached the parked execution")
                .isTrue()

            // when: a second call runs while the first one is parked, and the first one is released afterwards
            val second = pool.submit<ExecutionResponse> { executor.execute("probe --value second") }
            runCatching { second.get(OVERLAP_WINDOW_MILLIS, MILLISECONDS) }
            release.countDown()

            // then: the first call still echoes its own argument
            assertThat(first.get(FIXTURE_TIMEOUT_SECONDS, SECONDS).response)
                .`as`("output of the first call, which was parsed with the value 'first'")
                .containsExactly("first")
            assertThat(second.get(FIXTURE_TIMEOUT_SECONDS, SECONDS).response)
                .`as`("output of the second call, which was parsed with the value 'second'")
                .containsExactly("second")
        } finally {
            release.countDown()
            pool.shutdownNow()
        }
    }

    private fun commandExecutor() =
        CommandExecutor(
            journalist = ReposiliteJournalist(InMemoryLogger(), 0, testEnv = true, noColor = false),
            failureFacade = FailureFacade(InMemoryLogger(), Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)),
            source = ByteArrayInputStream(ByteArray(0))
        )

    private companion object {
        /** Upper bound for waiting on the threads of the fixture, not a delay the assertions depend on. */
        const val FIXTURE_TIMEOUT_SECONDS = 10L

        /**
         * Time given to a second call to finish while the first one is parked. A call that finishes within it
         * ran during the parked execution; a call that waits for the first one simply times out here.
         */
        const val OVERLAP_WINDOW_MILLIS = 500L
    }

}

@Command(name = "probe", description = ["Echoes the value it was given"])
private class ValueProbe(
    private val entered: CountDownLatch,
    private val release: CountDownLatch
) : ReposiliteCommand {

    @Option(names = ["-v", "--value"], description = ["Value to echo"], required = true)
    var value: String? = null

    private val parked = AtomicBoolean(false)

    override fun execute(context: CommandContext) {
        if (parked.compareAndSet(false, true)) {
            entered.countDown()
            release.await()
        }
        context.append(value.orEmpty())
    }

}
