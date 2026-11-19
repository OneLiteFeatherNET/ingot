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
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit.SECONDS

/**
 * The version 1 outbox of one console WebSocket. Log lines may be dropped and counted, results and other answers never
 * are, and the clock decides the log rate. The sender is gated by a latch the test controls, and every wait is a bound
 * that only fires when the expected event never comes.
 */
internal class ConsoleFrameOutboxTest {

    private val outboxes = mutableListOf<ConsoleFrameOutbox>()
    private val clock = TestClock(Instant.EPOCH)

    @AfterEach
    fun closeOutboxes() {
        outboxes.forEach { it.close() }
    }

    @Test
    fun `offering log lines returns at once while the writer is blocked`() {
        // given: an outbox whose writer is blocked on its first frame
        val sender = GatedSender()
        val outbox = outbox(sender::send)
        outbox.start()
        assertThat(outbox.offerLog(line(0))).`as`("first line").isTrue()
        sender.awaitEntered()

        // when: ten more lines are logged while the writer is still blocked
        val accepted = mutableListOf<Boolean>()
        assertTimeoutPreemptively(Duration.ofSeconds(AWAIT_SECONDS)) {
            repeat(10) { accepted += outbox.offerLog(line((it + 1).toLong())) }
        }

        // then: every line was accepted without waiting for the writer
        assertThat(accepted).`as`("accepted lines while the writer is blocked").containsOnly(true)
        sender.open()
    }

    @Test
    fun `a full queue of log lines drops them and the next log frame states the count`() {
        // given: an outbox of capacity three whose writer holds line 0, with lines 1 to 3 filling the queue
        val sender = GatedSender()
        val outbox = outbox(sender::send, capacity = 3)
        outbox.start()
        outbox.offerLog(line(0))
        sender.awaitEntered()
        outbox.offerLog(line(1))
        outbox.offerLog(line(2))
        outbox.offerLog(line(3))

        // when: two more lines are logged while the queue is full, and the writer is released and drains the queue
        val fourth = outbox.offerLog(line(4))
        val fifth = outbox.offerLog(line(5))
        sender.open()
        assertThat(sender.next(4)).`as`("queued frames").containsExactly(
            frame(0, dropped = 0),
            frame(1, dropped = 0),
            frame(2, dropped = 0),
            frame(3, dropped = 0),
        )
        // and a line logged once there is room again
        outbox.offerLog(line(6))

        // then: the two refused lines are counted, and the next log frame states the count
        assertThat(fourth).`as`("line offered to a full queue").isFalse()
        assertThat(fifth).`as`("line offered to a full queue").isFalse()
        assertThat(sender.next(1)).`as`("next frame").containsExactly(frame(6, dropped = 2))
    }

    @Test
    fun `a result is never dropped and the log lines it displaces are counted`() {
        // given: an outbox of capacity two whose writer holds line 0, with lines 1 and 2 filling the queue
        val sender = GatedSender()
        val outbox = outbox(sender::send, capacity = 2)
        outbox.start()
        outbox.offerLog(line(0))
        sender.awaitEntered()
        outbox.offerLog(line(1))
        outbox.offerLog(line(2))

        // when: a result arrives while the queue is full of log lines
        val accepted = outbox.putFrame(RESULT)
        sender.open()

        // then: the result is delivered, the oldest queued line is the one counted as dropped, and the next line states it
        assertThat(accepted).`as`("result accepted").isTrue()
        assertThat(sender.next(3)).`as`("delivered frames").containsExactly(
            frame(0, dropped = 0),
            frame(2, dropped = 0),
            RESULT,
        )

        // and the next line that is accepted states the line displaced by the result
        outbox.offerLog(line(3))
        assertThat(sender.next(1)).`as`("next frame").containsExactly(frame(3, dropped = 1))
    }

    @Test
    fun `a queue full of results closes the connection through the slow consumer callback`() {
        // given: an outbox of capacity two whose writer holds the first result, with two results queued
        val sender = GatedSender()
        var slowConsumer = 0
        val outbox = outbox(sender::send, capacity = 2, onSlowConsumer = { slowConsumer++ })
        outbox.start()
        outbox.putFrame("result-1")
        sender.awaitEntered()
        outbox.putFrame("result-2")
        outbox.putFrame("result-3")

        // when: a fourth result arrives and the queue holds no log line to make room
        val accepted = outbox.putFrame("result-4")

        // then: the result is refused, the slow consumer is reported once, and no queued result is lost
        assertThat(accepted).`as`("result offered to a queue full of results").isFalse()
        assertThat(slowConsumer).`as`("slow consumer reports").isEqualTo(1)
        sender.open()
        assertThat(sender.next(3)).`as`("delivered results").containsExactly("result-1", "result-2", "result-3")
    }

    @Test
    fun `a burst of one thousand lines at one instant delivers at most five hundred`() {
        // given: an outbox on a clock that does not move, and a writer that is not blocked
        val sender = GatedSender()
        sender.open()
        val outbox = outbox(sender::send)
        outbox.start()

        // when: one thousand lines are logged in the same instant
        val accepted = (0 until 1000).count { outbox.offerLog(line(it.toLong())) }

        // then: five hundred are accepted, and the rest are counted as dropped
        assertThat(accepted).`as`("accepted lines of the burst").isEqualTo(BURST)
        assertThat(sender.next(accepted)).`as`("delivered lines").hasSize(BURST)
    }

    @Test
    fun `lines over the rate are counted and the budget refills with the clock`() {
        // given: an outbox whose burst of five hundred lines is spent at one instant, with a writer that is not blocked
        val sender = GatedSender()
        sender.open()
        val outbox = outbox(sender::send)
        outbox.start()
        (0 until BURST).forEach { outbox.offerLog(line(it.toLong())) }
        sender.next(BURST)

        // when: the clock moves on by one second and three hundred lines are logged
        clock.advance(Duration.ofSeconds(1))
        val accepted = (0 until 300).count { outbox.offerLog(line(1000L + it)) }

        // then: the rate of two hundred lines a second is the budget, and the line after the refill states the lines over it
        assertThat(accepted).`as`("lines accepted after one second").isEqualTo(RATE_PER_SECOND)
        sender.next(RATE_PER_SECOND)
        clock.advance(Duration.ofSeconds(1))
        outbox.offerLog(line(2000L))
        assertThat(sender.next(1)).`as`("next frame").containsExactly(frame(2000L, dropped = 100))
    }

    @Test
    fun `closing the outbox refuses later lines and results`() {
        // given: an outbox that is closed while its writer is blocked
        val sender = GatedSender()
        val outbox = outbox(sender::send)
        outbox.start()
        outbox.offerLog(line(0))
        sender.awaitEntered()
        outbox.close()

        // when: a line and a result are offered after the close
        val lateLine = outbox.offerLog(line(1))
        val lateResult = outbox.putFrame("late")

        // then: both are refused
        sender.open()
        assertThat(lateLine).`as`("line offered after close").isFalse()
        assertThat(lateResult).`as`("result offered after close").isFalse()
    }

    private fun outbox(
        send: (Any) -> Unit,
        capacity: Int = CONSOLE_SOCKET_QUEUE_CAPACITY,
        onSlowConsumer: () -> Unit = {},
    ): ConsoleFrameOutbox =
        ConsoleFrameOutbox(send, onFailure = {}, onSlowConsumer = onSlowConsumer, clock = clock, capacity = capacity, threadFactory = THREAD_FACTORY)
            .also { outboxes += it }

    private fun line(seq: Long): LogLine = LogLine(seq = seq, node = "node-a", level = "INFO", text = "line-$seq")

    private fun frame(seq: Long, dropped: Int): LogFrame =
        LogFrame(seq = seq, node = "node-a", level = "INFO", text = "line-$seq", dropped = dropped)

    /** A sender that blocks until [open] is called, and records every frame it delivers in order. */
    private class GatedSender {
        private val gate = CountDownLatch(1)
        private val entered = CountDownLatch(1)
        private val delivered = LinkedBlockingQueue<Any>()

        fun send(frame: Any) {
            entered.countDown()
            gate.await(AWAIT_SECONDS, SECONDS)
            delivered.put(frame)
        }

        fun awaitEntered() {
            check(entered.await(AWAIT_SECONDS, SECONDS)) { "the writer was never called" }
        }

        fun open() = gate.countDown()

        /** Takes the next [count] delivered frames, in delivery order. */
        fun next(count: Int): List<Any> = List(count) {
            checkNotNull(delivered.poll(AWAIT_SECONDS, SECONDS)) { "frame ${it + 1} of $count was not delivered" }
        }
    }

    /** A clock that only moves when [advance] is called. */
    private class TestClock(private var now: Instant) : Clock() {
        fun advance(duration: Duration) {
            now = now.plus(duration)
        }

        override fun getZone(): ZoneId = ZoneId.of("UTC")
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = now
    }

    private companion object {
        /** A bound for the waits that must end, never a delay the tests rely on. */
        const val AWAIT_SECONDS = 10L
        const val BURST = 500
        const val RATE_PER_SECOND = 200
        const val RESULT = "result"
        val THREAD_FACTORY = java.util.concurrent.ThreadFactory { task -> Thread(task).apply { isDaemon = true } }
    }

}
