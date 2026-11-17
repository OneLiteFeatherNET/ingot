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
package com.reposilite.console.infrastructure

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertTimeoutPreemptively
import java.io.IOException
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit.SECONDS

/**
 * The outbox sits between the logging threads and one console WebSocket. A sender that never returns must not hold up the
 * logging threads, lines must leave in the order they were queued, and overflow must be reported with one marker. The
 * sender is gated by a latch the test controls, and every wait is a bound that only fires when the expected event never comes.
 */
internal class ConsoleSocketOutboxTest {

    private val outboxes = mutableListOf<ConsoleSocketOutbox>()
    private val writerExited = CountDownLatch(1)
    private val threadFactory = ThreadFactory { task ->
        Thread {
            try {
                task.run()
            } finally {
                writerExited.countDown()
            }
        }.apply { isDaemon = true }
    }

    @AfterEach
    fun closeOutboxes() {
        outboxes.forEach { it.close() }
    }

    @Test
    fun `offering lines returns at once while the sender is blocked`() {
        // given: an outbox whose sender is blocked on its first line
        val sender = GatedSender(expected = 11)
        val outbox = outbox(sender::send)
        outbox.start()
        assertThat(outbox.offer("line-0")).isTrue()
        sender.awaitEntered()

        // when: ten more lines are logged while the sender is still blocked
        val accepted = mutableListOf<Boolean>()
        assertTimeoutPreemptively(Duration.ofSeconds(AWAIT_SECONDS)) {
            repeat(10) { accepted += outbox.offer("line-${it + 1}") }
        }

        // then: every line was accepted without waiting for the sender
        assertThat(accepted).`as`("accepted lines while the sender is blocked").containsOnly(true)
        sender.open()
    }

    @Test
    fun `delivers the offered lines in order once the sender is unblocked`() {
        // given: an outbox whose sender is blocked, holding lines that were offered in a known order
        val sender = GatedSender(expected = 4)
        val outbox = outbox(sender::send)
        outbox.start()
        outbox.offer("line-0")
        sender.awaitEntered()
        outbox.offer("line-1")
        outbox.offer("line-2")
        outbox.offer("line-3")

        // when: the sender is unblocked
        sender.open()

        // then: the lines arrive in the order they were offered
        sender.awaitDelivered()
        assertThat(sender.sent).`as`("delivered lines").containsExactly("line-0", "line-1", "line-2", "line-3")
    }

    @Test
    fun `drops lines beyond the capacity and sends one marker with the number of dropped lines`() {
        // given: an outbox of capacity three whose sender is blocked on the first line, so three more lines fill the queue
        val sender = GatedSender(expected = 5)
        val outbox = outbox(sender::send, capacity = 3)
        outbox.start()
        outbox.offer("line-0")
        sender.awaitEntered()
        outbox.offer("line-1")
        outbox.offer("line-2")
        outbox.offer("line-3")

        // when: two more lines are logged while the queue is full, then the sender is unblocked
        val fourth = outbox.offer("line-4")
        val fifth = outbox.offer("line-5")
        sender.open()

        // then: the two lines were refused, and the client receives the queued lines followed by one marker with the count
        assertThat(fourth).`as`("line offered to a full queue").isFalse()
        assertThat(fifth).`as`("line offered to a full queue").isFalse()
        sender.awaitDelivered()
        assertThat(sender.sent).`as`("delivered frames").containsExactly(
            "line-0",
            "line-1",
            "line-2",
            "line-3",
            "[console] 2 log lines dropped because this client reads too slowly",
        )
    }

    @Test
    fun `put delivers every line without dropping, even beyond the capacity`() {
        // given: an outbox of capacity two, with a sender that is not blocked
        val sender = GatedSender(expected = 10)
        sender.open()
        val outbox = outbox(sender::send, capacity = 2)
        outbox.start()

        // when: ten replayed lines are put into the outbox
        repeat(10) { outbox.put("line-$it") }

        // then: every line is delivered in order and nothing is reported as dropped
        sender.awaitDelivered()
        assertThat(sender.sent).`as`("delivered lines").containsExactly(*Array(10) { "line-$it" })
    }

    @Test
    fun `closing the outbox stops the writer and refuses later lines`() {
        // given: an outbox whose sender is blocked on its first line
        val sender = GatedSender(expected = 1)
        val outbox = outbox(sender::send)
        outbox.start()
        outbox.offer("line-0")
        sender.awaitEntered()

        // when: the outbox is closed and then a line is logged
        outbox.close()
        val lateLine = outbox.offer("late")

        // then: the writer ends, and the late line is refused and never sent
        assertThat(lateLine).`as`("line offered after close").isFalse()
        sender.open()
        assertThat(writerExited.await(AWAIT_SECONDS, SECONDS)).`as`("writer ended after close").isTrue()
        assertThat(sender.sent).`as`("frames sent").doesNotContain("late")
    }

    @Test
    fun `a failed send is reported and stops the writer`() {
        // given: an outbox whose sender fails on the first line
        val failures = CopyOnWriteArrayList<Exception>()
        val failureReported = CountDownLatch(1)
        val outbox = outbox(
            send = { throw IOException("connection reset") },
            onFailure = { failures += it; failureReported.countDown() },
        )
        outbox.start()

        // when: a line is logged
        outbox.offer("line-0")

        // then: the failure is reported to the owner and the writer ends
        assertThat(failureReported.await(AWAIT_SECONDS, SECONDS)).`as`("failure reported").isTrue()
        assertThat(writerExited.await(AWAIT_SECONDS, SECONDS)).`as`("writer ended after failure").isTrue()
        assertThat(failures.map { it.message }).containsExactly("connection reset")
    }

    private fun outbox(
        send: (String) -> Unit,
        onFailure: (Exception) -> Unit = {},
        capacity: Int = CONSOLE_SOCKET_QUEUE_CAPACITY,
    ): ConsoleSocketOutbox =
        ConsoleSocketOutbox(send, onFailure, capacity, threadFactory).also { outboxes += it }

    /** A sender that blocks every frame until [open] is called, and counts the frames it delivered. */
    private class GatedSender(expected: Int) {
        val sent: MutableList<String> = CopyOnWriteArrayList()
        private val gate = CountDownLatch(1)
        private val entered = CountDownLatch(1)
        private val delivered = CountDownLatch(expected)

        fun send(line: String) {
            entered.countDown()
            gate.await()
            sent += line
            delivered.countDown()
        }

        fun awaitEntered() {
            check(entered.await(AWAIT_SECONDS, SECONDS)) { "the sender was never called" }
        }

        fun open() = gate.countDown()

        fun awaitDelivered() {
            check(delivered.await(AWAIT_SECONDS, SECONDS)) { "not every frame was delivered, sent so far: $sent" }
        }
    }

    private companion object {
        /** A bound for the waits that must end, never a delay the tests rely on. */
        const val AWAIT_SECONDS = 10L
    }

}
