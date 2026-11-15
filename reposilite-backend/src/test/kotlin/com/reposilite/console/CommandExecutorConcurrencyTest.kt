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
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock

internal class CommandExecutorConcurrencyTest {

    @Test
    fun `should keep a second call out of the command lock while a parked call holds it`() {
        // given: a first call parked inside its execution, after its arguments were parsed
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val secondRan = CountDownLatch(1)
        val executor = commandExecutor()
        executor.registerCommand(ValueProbe(entered, release, secondRan))
        val pool = Executors.newFixedThreadPool(2)

        try {
            val first = pool.submit<ExecutionResponse> { executor.execute("probe --value first") }
            assertThat(entered.await(FIXTURE_TIMEOUT_SECONDS, SECONDS))
                .`as`("first call reached the parked execution")
                .isTrue()

            // when: a second call is started while the first one is parked
            val second = pool.submit<ExecutionResponse> { executor.execute("probe --value second") }

            // then: the second call has not run, and the first call holds the command lock, so no other call can enter
            assertThat(secondRan.count)
                .`as`("second call ran while the first one was parked")
                .isEqualTo(1)
            assertThat(executor.commandLock.isFreeNow())
                .`as`("command lock is free while the first call is parked")
                .isFalse()

            // when: the first call is released
            release.countDown()

            // then: the first call still echoes its own argument, and the second call runs afterwards with its own
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

    /** Whether no thread holds the lock right now. The probe takes the lock only for a moment and gives it back. */
    private fun ReentrantLock.isFreeNow(): Boolean =
        tryLock().also { acquired -> if (acquired) unlock() }

    private fun commandExecutor() =
        CommandExecutor(
            journalist = ReposiliteJournalist(InMemoryLogger(), 0, testEnv = true, noColor = false),
            failureFacade = FailureFacade(InMemoryLogger(), Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)),
            source = ByteArrayInputStream(ByteArray(0))
        )

    private companion object {
        /**
         * Upper bound for waiting on a thread of the fixture. It is only reached when a call never completes; the
         * assertions do not depend on it.
         */
        const val FIXTURE_TIMEOUT_SECONDS = 10L
    }

}

@Command(name = "probe", description = ["Echoes the value it was given"])
private class ValueProbe(
    private val entered: CountDownLatch,
    private val release: CountDownLatch,
    private val secondRan: CountDownLatch,
) : ReposiliteCommand {

    @Option(names = ["-v", "--value"], description = ["Value to echo"], required = true)
    var value: String? = null

    private val parked = AtomicBoolean(false)

    override fun execute(context: CommandContext) {
        if (parked.compareAndSet(false, true)) {
            entered.countDown()
            release.await()
        } else {
            secondRan.countDown()
        }
        context.append(value.orEmpty())
    }

}
