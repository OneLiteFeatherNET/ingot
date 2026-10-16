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

package com.reposilite.audit.application

import com.reposilite.audit.AuditFacade
import com.reposilite.audit.AuditRepository
import com.reposilite.audit.infrastructure.InMemoryAuditRepository
import com.reposilite.audit.infrastructure.SqlAuditRepository
import com.reposilite.journalist.Journalist
import com.reposilite.plugin.api.PluginComponents
import com.reposilite.scheduling.ScheduledJobClaims
import com.reposilite.scheduling.infrastructure.SqlScheduledJobClaims
import org.jetbrains.exposed.v1.jdbc.Database
import panda.std.reactive.Reference
import java.time.Clock

class AuditComponents(
    private val journalist: Journalist,
    private val database: Database?,
    private val settings: Reference<AuditSettings>,
    private val clock: Clock = Clock.systemUTC(),
) : PluginComponents {

    private fun auditRepository(): AuditRepository =
        when (database) {
            null -> InMemoryAuditRepository()
            else -> SqlAuditRepository(database)
        }

    /** Without a database there is no other instance to share the retention purge with, so the purge is not claimed. */
    private fun purgeClaims(): ScheduledJobClaims =
        database?.let { SqlScheduledJobClaims(it, clock) } ?: ScheduledJobClaims.Unshared

    fun auditFacade(
        repository: AuditRepository = auditRepository(),
        claims: ScheduledJobClaims = purgeClaims(),
    ): AuditFacade =
        AuditFacade(
            journalist = journalist,
            repository = repository,
            settings = settings,
            clock = clock,
            claims = claims,
        )

}
