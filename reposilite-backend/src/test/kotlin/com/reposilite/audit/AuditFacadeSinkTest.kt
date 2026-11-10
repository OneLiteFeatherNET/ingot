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

package com.reposilite.audit

import com.reposilite.ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER
import com.reposilite.audit.api.AuditAction.LOGIN_FAILED
import com.reposilite.audit.api.AuditAction.TOKEN_CREATED
import com.reposilite.audit.api.AuditActor
import com.reposilite.audit.api.AuditEvent
import com.reposilite.audit.application.AuditSettings
import com.reposilite.audit.infrastructure.InMemoryAuditRepository
import com.reposilite.audit.sink.AuditLineSink
import com.reposilite.audit.sink.AuditSinkLine
import com.reposilite.journalist.backend.InMemoryLogger
import net.onelitefeather.ingot.identity.login.MutableClock
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import panda.std.reactive.Reference
import java.time.Instant

internal class AuditFacadeSinkTest {

    private val journalist = InMemoryLogger()
    private val clock = MutableClock(Instant.parse("2026-11-10T08:15:30Z"))

    /** Keeps every line it is offered and the number of rows that existed at the moment of the offer. */
    private class RecordingSink(private val rowsAtOffer: () -> Long = { -1 }) : AuditLineSink {
        val lines = mutableListOf<AuditSinkLine>()
        val rowCounts = mutableListOf<Long>()

        override fun offer(line: AuditSinkLine) {
            rowCounts += rowsAtOffer()
            lines += line
        }
    }

    private class ThrowingSink : AuditLineSink {
        override fun offer(line: AuditSinkLine) {
            throw IllegalStateException("sink is broken")
        }
    }

    private class FailingRepository(private val delegate: InMemoryAuditRepository = InMemoryAuditRepository()) :
        AuditRepository by delegate {
        override fun insert(record: AuditRecord): Long =
            throw IllegalStateException("database is down")
    }

    private fun facade(
        repository: AuditRepository = InMemoryAuditRepository(),
        settings: AuditSettings = AuditSettings(),
        sink: AuditLineSink = RecordingSink(),
    ): AuditFacade =
        AuditFacade(journalist, repository, Reference.reference(settings), clock, lineSink = sink, node = "ingot-a")

    @Test
    fun `writes the line before the row is inserted`() {
        val repository = InMemoryAuditRepository()
        val sink = RecordingSink(rowsAtOffer = { repository.count() })

        facade(repository, sink = sink).record(AuditEvent(TOKEN_CREATED, AuditActor.named("admin", null)))

        assertThat(sink.rowCounts).describedAs("rows in the table when the line was offered").containsExactly(0L)
        assertThat(repository.count()).isEqualTo(1)
    }

    @Test
    fun `keeps the line when the database insert fails`() {
        val sink = RecordingSink()

        assertThatThrownBy {
            facade(FailingRepository(), sink = sink).record(AuditEvent(TOKEN_CREATED, AuditActor.named("admin", null)))
        }.isInstanceOf(IllegalStateException::class.java)

        assertThat(sink.lines).describedAs("lines written while the database was down").hasSize(1)
    }

    @Test
    fun `keeps the table row when the sink fails`() {
        val repository = InMemoryAuditRepository()

        facade(repository, sink = ThrowingSink()).record(AuditEvent(TOKEN_CREATED, AuditActor.named("admin", null)))

        assertThat(repository.count()).describedAs("rows in the table").isEqualTo(1)
    }

    @Test
    fun `leaves the actor address out of the line when the settings do not record addresses`() {
        val sink = RecordingSink()

        facade(settings = AuditSettings(recordActorAddress = false), sink = sink)
            .record(AuditEvent(TOKEN_CREATED, AuditActor.named("admin", "10.0.0.2")))

        assertThat(sink.lines.single().actorAddress).isNull()
    }

    @Test
    fun `writes one line per failed login and folds the table rows`() {
        val repository = InMemoryAuditRepository()
        val sink = RecordingSink()
        val facade = facade(repository, sink = sink)

        repeat(3) { facade.record(AuditEvent(LOGIN_FAILED, AuditActor.named("mallory", "203.0.113.7"), success = false)) }

        assertThat(sink.lines).describedAs("lines of the failed logins").hasSize(3)
        assertThat(sink.lines.map { DEFAULT_OBJECT_MAPPER.readTree(it.encode()).get("occurrences").asInt() })
            .describedAs("occurrences written for each line")
            .containsOnly(1)
        assertThat(repository.find(AuditQuery()).single().occurrences).describedAs("folded table row").isEqualTo(3)
    }

    @Test
    fun `stamps the line with the time of the injected clock and the node of the instance`() {
        val sink = RecordingSink()

        facade(sink = sink).record(AuditEvent(TOKEN_CREATED, AuditActor.named("admin", null)))

        assertThat(sink.lines.single().time).isEqualTo(clock.instant())
        assertThat(sink.lines.single().node).isEqualTo("ingot-a")
    }

}
