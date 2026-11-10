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

import com.reposilite.audit.api.AuditAction.NEXUS_IMPORT_RUN
import com.reposilite.audit.api.AuditActor
import com.reposilite.audit.api.AuditEvent
import com.reposilite.audit.application.AuditSettings
import com.reposilite.audit.application.deployAuditRecorded
import com.reposilite.audit.infrastructure.InMemoryAuditRepository
import com.reposilite.journalist.backend.InMemoryLogger
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import panda.std.reactive.Reference

/**
 * The summary audit mode of the Nexus import: a bulk session is registered with the exact session identifier of the job, and
 * only deploy events of that identifier are left out. The run event itself is always recorded.
 */
internal class AuditBulkSessionTest {

    private val clock = MutableClock()

    private fun facade(repository: InMemoryAuditRepository = InMemoryAuditRepository()) =
        AuditFacade(InMemoryLogger(), repository, Reference.reference(AuditSettings()), clock)

    @Test
    fun `should leave out the per-file deploy events of a registered bulk session`() {
        // given: the job registered its session identifier
        val facade = facade()
        facade.registerBulkSession("migration@7")

        // when / then: its deploy events are not recorded per file
        assertThat(deployAuditRecorded("migration@7", facade)).describedAs("a registered session is summarised").isFalse
    }

    @Test
    fun `should still record a deploy by a token that merely looks like the import`() {
        // given: a bulk session is registered
        val facade = facade()
        facade.registerBulkSession("migration@7")

        // when / then: a token named like the import, and a different job, are audited per file
        assertThat(deployAuditRecorded("migration-bulk@10.0.0.2", facade)).isTrue
        assertThat(deployAuditRecorded("migration@8", facade)).isTrue
    }

    @Test
    fun `should record the run event with the migration actor in both modes`() {
        // given: an empty audit log
        val repository = InMemoryAuditRepository()
        val facade = facade(repository)

        // when: the job records its run event
        facade.record(AuditEvent(NEXUS_IMPORT_RUN, AuditActor.named("migration", "7"), details = mapOf("copied" to 3)))

        // then: the row names the migration actor and the counts
        val row = repository.find(AuditQuery()).single()
        assertThat(row.action).isEqualTo(NEXUS_IMPORT_RUN)
        assertThat(row.actor).isEqualTo("migration")
        assertThat(row.details).isEqualTo("""{"copied":3}""")
    }

    @Test
    fun `should forget a bulk session once it is unregistered`() {
        // given: a registered and then unregistered session
        val facade = facade()
        facade.registerBulkSession("migration@7")
        facade.unregisterBulkSession("migration@7")

        // when / then: its deploys are audited per file again
        assertThat(deployAuditRecorded("migration@7", facade)).isTrue
    }
}
