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

import com.reposilite.audit.api.AuditAction.ARTIFACT_DEPLOYED
import com.reposilite.audit.api.AuditAction.LOGIN_FAILED
import com.reposilite.audit.api.AuditAction.TOKEN_CREATED
import com.reposilite.audit.api.AuditAction.TOKEN_DELETED
import com.reposilite.audit.api.AuditActor
import com.reposilite.audit.api.AuditEvent
import com.reposilite.audit.application.AuditSettings
import com.reposilite.audit.infrastructure.InMemoryAuditRepository
import com.reposilite.journalist.backend.InMemoryLogger
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import panda.std.reactive.Reference
import java.time.Duration
import java.time.Instant

internal class AuditFacadeTest {

    private val journalist = InMemoryLogger()
    private val clock = MutableClock()

    private fun facade(
        repository: AuditRepository = InMemoryAuditRepository(),
        settings: AuditSettings = AuditSettings(),
    ): AuditFacade =
        AuditFacade(journalist, repository, Reference.reference(settings), clock)

    @Test
    fun `records the actor, the target and the details of an administrative event`() {
        val repository = InMemoryAuditRepository()

        facade(repository).record(
            AuditEvent(TOKEN_CREATED, AuditActor.named("admin", "10.0.0.2"), details = mapOf("token" to "deploy-bot", "replaced" to false))
        )

        val row = repository.find(AuditQuery()).single()
        assertThat(row.action).isEqualTo(TOKEN_CREATED)
        assertThat(row.actor).isEqualTo("admin")
        assertThat(row.actorAddress).isEqualTo("10.0.0.2")
        assertThat(row.details).isEqualTo("""{"token":"deploy-bot","replaced":false}""")
        assertThat(row.timestamp).isEqualTo(clock.millis())
        assertThat(row.success).isTrue()
        assertThat(row.occurrences).isEqualTo(1)
    }

    @Test
    fun `stores the repository and the path of an artifact event`() {
        val repository = InMemoryAuditRepository()

        facade(repository).record(AuditEvent(ARTIFACT_DEPLOYED, AuditActor.named("ci", null), repository = "releases", path = "org/example/app/1.0/app-1.0.jar"))

        val row = repository.find(AuditQuery()).single()
        assertThat(row.repository).isEqualTo("releases")
        assertThat(row.path).isEqualTo("org/example/app/1.0/app-1.0.jar")
        assertThat(row.details).isNull()
    }

    @Test
    fun `leaves the client address out when the settings do not record addresses`() {
        val repository = InMemoryAuditRepository()

        facade(repository, AuditSettings(recordActorAddress = false))
            .record(AuditEvent(TOKEN_DELETED, AuditActor.named("ci", "10.0.0.1"), details = mapOf("token" to "old")))

        assertThat(repository.find(AuditQuery()).single().actorAddress).isNull()
    }

    @Test
    fun `folds repeated failed logins of one actor and address into one row within a minute`() {
        val repository = InMemoryAuditRepository()
        val facade = facade(repository)

        repeat(5) { facade.record(failedLogin("mallory", "203.0.113.7")) }

        val rows = repository.find(AuditQuery())
        assertThat(rows).hasSize(1)
        assertThat(rows.single().occurrences).isEqualTo(5)
        assertThat(rows.single().success).isFalse()
    }

    @Test
    fun `opens a new row for failed logins once the minute has passed`() {
        val repository = InMemoryAuditRepository()
        val facade = facade(repository)

        facade.record(failedLogin("mallory", "203.0.113.7"))
        clock.advance(Duration.ofSeconds(59))
        facade.record(failedLogin("mallory", "203.0.113.7"))
        clock.advance(Duration.ofSeconds(2))
        facade.record(failedLogin("mallory", "203.0.113.7"))

        val rows = repository.find(AuditQuery())
        assertThat(rows.map { it.occurrences }).containsExactly(1, 2)
    }

    @Test
    fun `keeps failed logins from different addresses apart`() {
        val repository = InMemoryAuditRepository()
        val facade = facade(repository)

        facade.record(failedLogin("mallory", "203.0.113.7"))
        facade.record(failedLogin("mallory", "203.0.113.8"))

        assertThat(repository.find(AuditQuery())).hasSize(2)
    }

    @Test
    fun `filters by actor, action and repository`() {
        val repository = InMemoryAuditRepository()
        val facade = facade(repository)
        seedMixedEvents(facade)

        assertThat(facade.find(AuditQuery(actor = "ci")).records).hasSize(2)
        assertThat(facade.find(AuditQuery(action = TOKEN_CREATED)).records.single().actor).isEqualTo("admin")
        assertThat(facade.find(AuditQuery(repository = "releases")).records.single().path).isEqualTo("org/example/app/1.0/app-1.0.jar")
    }

    @Test
    fun `searches the actor, the path and the details without case sensitivity`() {
        val repository = InMemoryAuditRepository()
        val facade = facade(repository)
        seedMixedEvents(facade)

        assertThat(facade.find(AuditQuery(text = "DEPLOY-BOT")).records.single().action).isEqualTo(TOKEN_CREATED)
        assertThat(facade.find(AuditQuery(text = "app-1.0.jar")).records.single().action).isEqualTo(ARTIFACT_DEPLOYED)
        assertThat(facade.find(AuditQuery(text = "mallory")).records.single().action).isEqualTo(LOGIN_FAILED)
    }

    @Test
    fun `treats wildcard characters in the search text literally`() {
        val repository = InMemoryAuditRepository()
        val facade = facade(repository)
        seedMixedEvents(facade)

        assertThat(facade.find(AuditQuery(text = "%")).records).isEmpty()
        assertThat(facade.find(AuditQuery(text = "_")).records).isEmpty()
    }

    @Test
    fun `filters by time range where from is inclusive and to is exclusive`() {
        val repository = InMemoryAuditRepository()
        val facade = facade(repository)
        val start = clock.millis()

        facade.record(AuditEvent(TOKEN_DELETED, AuditActor.named("ci", null), details = mapOf("token" to "first")))
        clock.advance(Duration.ofHours(1))
        facade.record(AuditEvent(TOKEN_DELETED, AuditActor.named("ci", null), details = mapOf("token" to "second")))
        clock.advance(Duration.ofHours(1))
        facade.record(AuditEvent(TOKEN_DELETED, AuditActor.named("ci", null), details = mapOf("token" to "third")))

        val middle = facade.find(AuditQuery(from = start + HOUR_MILLIS, to = start + 2 * HOUR_MILLIS)).records
        assertThat(middle.map { it.details }).containsExactly("""{"token":"second"}""")
    }

    @Test
    fun `pages newest first and hands out a cursor until the last page`() {
        val facade = facade()
        repeat(5) { index ->
            facade.record(AuditEvent(TOKEN_DELETED, AuditActor.named("ci", null), details = mapOf("token" to "t$index")))
            clock.advance(Duration.ofSeconds(1))
        }

        val first = facade.find(AuditQuery(limit = 2))
        assertThat(first.records.map { it.details }).containsExactly("""{"token":"t4"}""", """{"token":"t3"}""")
        assertThat(first.nextCursor).isNotNull()

        val second = facade.find(AuditQuery(limit = 2, after = AuditCursor.decode(first.nextCursor!!)))
        assertThat(second.records.map { it.details }).containsExactly("""{"token":"t2"}""", """{"token":"t1"}""")

        val last = facade.find(AuditQuery(limit = 2, after = AuditCursor.decode(second.nextCursor!!)))
        assertThat(last.records.map { it.details }).containsExactly("""{"token":"t0"}""")
        assertThat(last.nextCursor).isNull()
    }

    @Test
    fun `pages through rows that share a timestamp without losing or repeating any`() {
        val facade = facade()
        repeat(3) { index ->
            facade.record(AuditEvent(TOKEN_DELETED, AuditActor.named("ci", null), details = mapOf("token" to "t$index")))
        }

        val first = facade.find(AuditQuery(limit = 2))
        val second = facade.find(AuditQuery(limit = 2, after = AuditCursor.decode(first.nextCursor!!)))

        val ids = (first.records + second.records).map { it.id }
        assertThat(ids).hasSize(3).doesNotHaveDuplicates()
        assertThat(second.nextCursor).isNull()
    }

    @Test
    fun `caps the page size at the maximum`() {
        val facade = facade()
        repeat(MAX_PAGE_SIZE + 1) {
            facade.record(AuditEvent(TOKEN_DELETED, AuditActor.named("ci", null), details = mapOf("token" to "t")))
        }

        assertThat(facade.find(AuditQuery(limit = 10_000)).records).hasSize(MAX_PAGE_SIZE)
    }

    @Test
    fun `purges events older than the retention period and keeps the rest`() {
        val repository = InMemoryAuditRepository()
        val facade = facade(repository, AuditSettings(retentionDays = 90))

        facade.record(AuditEvent(TOKEN_DELETED, AuditActor.named("ci", null), details = mapOf("token" to "old")))
        clock.advance(Duration.ofDays(91))
        facade.record(AuditEvent(TOKEN_DELETED, AuditActor.named("ci", null), details = mapOf("token" to "new")))

        assertThat(facade.purgeExpired()).isEqualTo(1)
        assertThat(repository.find(AuditQuery()).map { it.details }).containsExactly("""{"token":"new"}""")
    }

    @Test
    fun `keeps every event when the retention is zero`() {
        val repository = InMemoryAuditRepository()
        val facade = facade(repository, AuditSettings(retentionDays = 0))

        facade.record(AuditEvent(TOKEN_DELETED, AuditActor.named("ci", null), details = mapOf("token" to "old")))
        clock.advance(Duration.ofDays(3650))

        assertThat(facade.purgeExpired()).isZero()
        assertThat(repository.count()).isEqualTo(1)
    }

    @Test
    fun `writes a csv with a header and quotes the cells that need it`() {
        val facade = facade()
        facade.record(AuditEvent(TOKEN_DELETED, AuditActor.named("ci", null), details = mapOf("note" to "a, \"quoted\" value")))

        val out = StringBuilder()
        val written = facade.writeCsv(AuditQuery(), out)

        val lines = out.toString().lines().filter { it.isNotEmpty() }
        assertThat(written).isEqualTo(1)
        assertThat(lines.first()).isEqualTo("id,timestamp,actor,actorAddress,action,repository,path,success,occurrences,details")
        assertThat(lines[1]).contains(",ci,,TOKEN_DELETED,,,true,1,\"{\"\"note\"\":\"\"a, \\\"\"quoted\\\"\" value\"\"}\"")
    }

    @Test
    fun `neutralises cells that a spreadsheet would read as a formula`() {
        val facade = facade()
        facade.record(AuditEvent(TOKEN_DELETED, AuditActor.named("=HYPERLINK(\"x\")", null), details = mapOf("token" to "t")))

        val out = StringBuilder()
        facade.writeCsv(AuditQuery(), out)

        val row = out.toString().lines()[1]
        assertThat(row).contains("'=HYPERLINK(").doesNotContain(",=HYPERLINK")
    }

    private fun failedLogin(name: String, address: String) =
        AuditEvent(LOGIN_FAILED, AuditActor.named(name, address), success = false)

    private fun seedMixedEvents(facade: AuditFacade) {
        facade.record(AuditEvent(ARTIFACT_DEPLOYED, AuditActor.named("ci", "10.0.0.1"), repository = "releases", path = "org/example/app/1.0/app-1.0.jar"))
        facade.record(AuditEvent(ARTIFACT_DEPLOYED, AuditActor.named("ci", "10.0.0.1"), repository = "snapshots", path = "org/example/app/2.0-SNAPSHOT/app.pom"))
        facade.record(AuditEvent(TOKEN_CREATED, AuditActor.named("admin", "10.0.0.2"), details = mapOf("token" to "deploy-bot")))
        facade.record(failedLogin("mallory", "203.0.113.7"))
    }

    private companion object {
        const val HOUR_MILLIS = 60L * 60L * 1000L
    }

}
