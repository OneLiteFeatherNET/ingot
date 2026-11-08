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

package net.onelitefeather.ingot.cluster

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

internal class ClusterOutboxTest {

    @Test
    fun `a full queue drops the oldest message and counts the drop`() {
        ClusterOutbox(capacity = 2).use { outbox ->
            // when: three messages are offered to a queue of two
            outbox.offer("first")
            outbox.offer("second")
            outbox.offer("third")

            // then: the oldest is gone and the drop is counted
            assertThat(outbox.pendingMessages()).containsExactly("second", "third")
            assertThat(outbox.dropped).isEqualTo(1)
        }
    }

    @Test
    fun `publishing never waits while the queue is full`() {
        ClusterOutbox(capacity = 1).use { outbox ->
            // given: nothing drains the queue, so it stays full
            val finished = CountDownLatch(1)

            // when: a publisher offers many messages from its own thread
            thread {
                repeat(1_000) { outbox.offer("message $it") }
                finished.countDown()
            }

            // then: every offer returned within a bounded wait
            assertThat(finished.await(5, TimeUnit.SECONDS)).describedAs("offers returned within five seconds").isTrue()
            assertThat(outbox.dropped).isEqualTo(999)
        }
    }

    @Test
    fun `queued messages reach the sink in the order they were offered`() {
        ClusterOutbox(capacity = 8).use { outbox ->
            // given: a sink that records what it receives
            val received = CopyOnWriteArrayList<String>()
            val delivered = CountDownLatch(2)
            outbox.drainTo { message ->
                received.add(message)
                delivered.countDown()
            }

            // when: two messages are offered
            outbox.offer("alpha")
            outbox.offer("beta")

            // then: both arrive, in order, within a bounded wait
            assertThat(delivered.await(5, TimeUnit.SECONDS)).describedAs("messages delivered within five seconds").isTrue()
            assertThat(received).containsExactly("alpha", "beta")
        }
    }
}
