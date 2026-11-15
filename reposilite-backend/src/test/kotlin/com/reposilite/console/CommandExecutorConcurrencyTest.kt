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

            // then: the first call holds the command lock, so no other call can enter while it is parked
            assertThat(executor.commandLock.isFreeNow())
                .`as`("command lock is free while the first call is parked")
                .isFalse()

            // when: a second call is started while the first one is parked
            val second = pool.submit<ExecutionResponse> { executor.execute("probe --value second") }

            // then: the second call has not run, and its latch is still unreleased
            assertThat(secondRan.count)
                .`as`("second call ran while the first one was parked")
                .isEqualTo(1)

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

    @Test
    fun `should keep a second structured call out of the command lock between the check and the run of the first`() {
        // given: a first structured call parked between its check and its run, and a second call prepared behind it
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val secondChecked = CountDownLatch(1)
        val executor = commandExecutor()
        executor.registerCommand(ClassificationProbe())
        val first = executor.prepare("probe --value safe")
        first.onClassified = {
            entered.countDown()
            release.await()
        }
        val second = executor.prepare("probe --value dangerous")
        second.onClassified = { secondChecked.countDown() }
        val pool = Executors.newFixedThreadPool(2)

        try {
            val firstAnswer = pool.submit<StructuredAnswer> { first.executeStructured(confirmed = false) }
            assertThat(entered.await(FIXTURE_TIMEOUT_SECONDS, SECONDS))
                .`as`("first call reached the gap between its check and its run")
                .isTrue()

            // then: the first call still holds the command lock in the gap, so no other call can enter it
            assertThat(executor.commandLock.isFreeNow())
                .`as`("command lock is free between the check and the run of the first call")
                .isFalse()

            // when: the dangerous call is started while the first one is parked in that gap
            val secondAnswer = pool.submit<StructuredAnswer> { second.executeStructured(confirmed = false) }

            // then: the second call has not been checked, and its latch is still unreleased
            assertThat(secondChecked.count)
                .`as`("second call was checked while the first one was parked")
                .isEqualTo(1)

            // when: the first call is released
            release.countDown()

            // then: the first call runs with its own argument, and the second one is held for confirmation
            assertThat(firstAnswer.get(FIXTURE_TIMEOUT_SECONDS, SECONDS))
                .`as`("answer to the safe call, which must not be held for the dangerous call's arguments")
                .isInstanceOf(StructuredRun::class.java)
            assertThat(secondAnswer.get(FIXTURE_TIMEOUT_SECONDS, SECONDS))
                .`as`("answer to the dangerous call")
                .isInstanceOf(ConfirmationNeeded::class.java)
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

@Command(name = "probe", description = ["Dangerous only when given the value 'dangerous'"])
private class ClassificationProbe : ReposiliteCommand {

    @Option(names = ["-v", "--value"], description = ["Value to classify"], required = true)
    var value: String? = null

    override fun isDangerous(arguments: List<String>): Boolean =
        value == "dangerous"

    override fun execute(context: CommandContext) {
        context.append(value.orEmpty())
    }

}
