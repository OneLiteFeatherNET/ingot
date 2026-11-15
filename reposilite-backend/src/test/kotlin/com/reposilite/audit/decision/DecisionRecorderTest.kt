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

package com.reposilite.audit.decision

import com.reposilite.audit.AuditFacade
import com.reposilite.audit.AuditQuery
import com.reposilite.audit.api.AuditAction
import com.reposilite.audit.application.AuditSettings
import com.reposilite.audit.infrastructure.InMemoryAuditRepository
import com.reposilite.journalist.Channel
import com.reposilite.journalist.backend.InMemoryLogger
import net.onelitefeather.ingot.identity.login.MutableClock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import panda.std.reactive.Reference
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.SECONDS

internal class DecisionRecorderTest {

    private val journalist = InMemoryLogger()
    private val clock = MutableClock(Instant.parse("2026-11-15T10:00:00Z"))

    private fun record(decisionId: String = "decision-1") = DecisionRecord(
        decisionId = decisionId,
        time = clock.instant(),
        principal = "deploy-bot",
        credentialId = "deploy-bot",
        address = null,
        action = "deploy",
        resource = "com/example/app/1.0/app-1.0.jar",
        rawResource = null,
        outcome = DecisionOutcome.DENY,
        write = true,
        stage = "built-in",
        ruleIds = emptyList(),
        revision = null,
    )

    /** Keeps every stored record. Fails every store when [failing] is set. */
    private class CollectingSink(private val failing: Boolean = false) : DecisionSink {
        val stored = CopyOnWriteArrayList<DecisionRecord>()

        override fun store(record: DecisionRecord) {
            if (failing) throw IllegalStateException("database is down")
            stored += record
        }
    }

    /**
     * Holds the writer inside [store] until the test releases it. [entered] tells the test that the writer has taken
     * the first record, so the queue state that follows is deterministic. Every wait is bounded.
     */
    private class BlockingSink : DecisionSink {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val stored = CopyOnWriteArrayList<DecisionRecord>()

        override fun store(record: DecisionRecord) {
            entered.countDown()
            release.await(5, SECONDS)
            stored += record
        }
    }

    private fun recorder(
        mode: DecisionLogMode,
        sink: DecisionSink,
        capacity: Int = DecisionRecorder.DEFAULT_CAPACITY,
    ) = DecisionRecorder(mode, sink, journalist.logger, clock, capacity)

    @Test
    fun `off stores nothing and reports the record as off`() {
        val sink = CollectingSink()
        val recorder = recorder(DecisionLogMode.OFF, sink)

        val outcome = recorder.record(record())
        recorder.close()

        assertThat(outcome).describedAs("outcome").isEqualTo(RecordOutcome.OFF)
        assertThat(recorder.enabled).describedAs("enabled").isFalse()
        assertThat(sink.stored).describedAs("stored records").isEmpty()
    }

    @Test
    fun `sync has stored the record when record returns`() {
        val sink = CollectingSink()
        val recorder = recorder(DecisionLogMode.SYNC, sink)

        val outcome = recorder.record(record())
        recorder.close()

        assertThat(outcome).describedAs("outcome").isEqualTo(RecordOutcome.STORED)
        assertThat(sink.stored).describedAs("stored records").hasSize(1)
    }

    @Test
    fun `sync reports a failed store instead of throwing`() {
        val recorder = recorder(DecisionLogMode.SYNC, CollectingSink(failing = true))

        val outcome = recorder.record(record())
        recorder.close()

        assertThat(outcome).describedAs("outcome").isEqualTo(RecordOutcome.FAILED)
    }

    @Test
    fun `async queues the record and the writer stores it`() {
        val sink = CollectingSink()
        val stored = CountDownLatch(1)
        val recorder = recorder(DecisionLogMode.ASYNC, object : DecisionSink {
            override fun store(record: DecisionRecord) {
                sink.store(record)
                stored.countDown()
            }
        })

        val outcome = recorder.record(record())
        val writtenInTime = stored.await(5, SECONDS)
        recorder.close()

        assertThat(outcome).describedAs("outcome").isEqualTo(RecordOutcome.QUEUED)
        assertThat(writtenInTime).describedAs("written by the writer within the bounded wait").isTrue()
        assertThat(sink.stored).describedAs("stored records").hasSize(1)
    }

    @Test
    fun `async keeps writing after a failed store`() {
        val attempts = CopyOnWriteArrayList<String>()
        val secondAttempted = CountDownLatch(1)
        val recorder = recorder(DecisionLogMode.ASYNC, DecisionSink { record ->
            attempts += record.decisionId
            if (record.decisionId == "first") throw IllegalStateException("database is down")
            secondAttempted.countDown()
        })

        recorder.record(record("first"))
        recorder.record(record("second"))
        val reached = secondAttempted.await(5, SECONDS)
        recorder.close()

        assertThat(reached).describedAs("second record reached the sink").isTrue()
        assertThat(attempts).describedAs("attempts in order").containsExactly("first", "second")
    }

    @Test
    fun `async drops a record on a full queue and does not wait for the writer`() {
        val sink = BlockingSink()
        val recorder = recorder(DecisionLogMode.ASYNC, sink, capacity = 1)

        assertThat(recorder.record(record("first"))).isEqualTo(RecordOutcome.QUEUED)
        assertThat(sink.entered.await(5, SECONDS)).describedAs("writer took the first record").isTrue()
        assertThat(recorder.record(record("second"))).describedAs("queue has room for one").isEqualTo(RecordOutcome.QUEUED)

        val dropped = recorder.record(record("third"))
        sink.release.countDown()
        recorder.close()

        assertThat(dropped).describedAs("outcome on a full queue").isEqualTo(RecordOutcome.DROPPED)
        assertThat(recorder.droppedCount()).describedAs("dropped records").isEqualTo(1)
    }

    @Test
    fun `the drop is reported as a warning through the journalist`() {
        val sink = BlockingSink()
        val recorder = recorder(DecisionLogMode.ASYNC, sink, capacity = 1)

        recorder.record(record("first"))
        sink.entered.await(5, SECONDS)
        recorder.record(record("second"))
        recorder.record(record("third"))
        sink.release.countDown()
        recorder.close()

        val warnings = journalist.messages.filter { it.key == Channel.WARN }.map { it.value }
        assertThat(warnings).describedAs("warnings").anySatisfy { message ->
            assertThat(message).contains("DECISION LOG").contains("1 record(s) dropped")
        }
    }

    @Test
    fun `the audit sink stores a decision as an audit row through the facade`() {
        val repository = InMemoryAuditRepository()
        val facade = AuditFacade(journalist, repository, Reference.reference(AuditSettings()), clock)

        AuditDecisionSink(facade).store(record())

        assertThat(repository.find(AuditQuery()).map { it.action })
            .describedAs("audit actions of the rows")
            .containsExactly(AuditAction.AUTHORIZATION_WRITE)
    }

}
