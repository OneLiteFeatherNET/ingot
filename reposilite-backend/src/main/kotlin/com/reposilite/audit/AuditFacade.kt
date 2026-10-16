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
import com.reposilite.audit.api.AuditAction
import com.reposilite.audit.api.AuditEvent
import com.reposilite.audit.application.AuditSettings
import com.reposilite.journalist.Journalist
import com.reposilite.journalist.Logger
import com.reposilite.plugin.api.Facade
import com.reposilite.scheduling.ScheduledJobClaims
import com.reposilite.scheduling.utcDayKey
import panda.std.reactive.Reference
import java.time.Clock

/** Largest number of rows one export writes. Anything beyond it is cut off and the export says so in its log. */
const val MAX_EXPORT_ROWS = 100_000

private const val EXPORT_BATCH_SIZE = 500
private const val MINUTE_MILLIS = 60_000L
private const val DAY_MILLIS = 86_400_000L

class AuditFacade(
    private val journalist: Journalist,
    private val repository: AuditRepository,
    private val settings: Reference<AuditSettings>,
    private val clock: Clock,
    private val claims: ScheduledJobClaims = ScheduledJobClaims.Unshared,
) : Facade, Journalist {

    private data class FailureKey(val name: String, val address: String?)

    private data class OpenFailure(val minute: Long, val recordId: Long)

    /** The failed-login row that is still open for each key. Guarded by its own monitor. */
    private val openFailures = mutableMapOf<FailureKey, OpenFailure>()

    fun record(event: AuditEvent) {
        val now = clock.millis()
        val address = event.actor.address?.takeIf { settings.get().recordActorAddress }

        if (event.action == AuditAction.LOGIN_FAILED) {
            recordFailedLogin(event, address, now)
            return
        }

        repository.insert(
            AuditRecord(
                id = 0,
                timestamp = now,
                actor = event.actor.name,
                actorAddress = address,
                action = event.action,
                repository = event.repository,
                path = event.path,
                details = encodeDetails(event.details),
                success = event.success,
                occurrences = 1,
            )
        )
    }

    /**
     * Failed logins are folded: all failures of one name from one address within the same minute
     * share a single row, whose occurrence counter grows. A brute-force attempt therefore costs
     * one row per minute and key instead of one per request.
     */
    private fun recordFailedLogin(event: AuditEvent, address: String?, now: Long) {
        val minute = now - now % MINUTE_MILLIS
        val key = FailureKey(event.actor.name, address)

        synchronized(openFailures) {
            val open = openFailures[key]
            if (open != null && open.minute == minute) {
                repository.incrementOccurrences(open.recordId)
                return
            }

            // A row from an earlier minute can never be folded into again, so it is dropped here.
            openFailures.entries.removeIf { it.value.minute < minute }

            val id = repository.insert(
                AuditRecord(
                    id = 0,
                    timestamp = now,
                    actor = event.actor.name,
                    actorAddress = address,
                    action = AuditAction.LOGIN_FAILED,
                    repository = null,
                    path = null,
                    details = null,
                    success = false,
                    occurrences = 1,
                )
            )
            openFailures[key] = OpenFailure(minute, id)
        }
    }

    fun find(query: AuditQuery): AuditPage {
        val limit = query.limit.coerceIn(1, MAX_PAGE_SIZE)
        val rows = repository.find(query.copy(limit = limit + 1))
        val page = rows.take(limit)
        val nextCursor = if (rows.size > limit) page.last().let { AuditCursor(it.timestamp, it.id).encode() } else null
        return AuditPage(records = page, nextCursor = nextCursor)
    }

    /**
     * Writes every row that matches the query as CSV, header first, and returns the number of rows.
     * The query's paging is ignored: the export walks the whole result in batches.
     */
    fun writeCsv(query: AuditQuery, out: Appendable): Int {
        out.append(AuditCsv.HEADER_LINE)

        var written = 0
        var cursor: AuditCursor? = null
        while (written < MAX_EXPORT_ROWS) {
            val batch = repository.find(query.copy(after = cursor, limit = EXPORT_BATCH_SIZE))
            batch.forEach { record ->
                if (written >= MAX_EXPORT_ROWS) return@forEach
                out.append(AuditCsv.line(record))
                written++
            }
            if (batch.size < EXPORT_BATCH_SIZE) break
            cursor = batch.last().let { AuditCursor(it.timestamp, it.id) }
        }

        if (written >= MAX_EXPORT_ROWS) {
            logger.warn("AUDIT | CSV export stopped at $MAX_EXPORT_ROWS rows, narrow the filters to export the rest")
        }
        return written
    }

    /**
     * Deletes the rows older than the retention period. A retention of zero or less keeps every row.
     */
    fun purgeExpired(): Long {
        val retentionDays = settings.get().retentionDays
        if (retentionDays <= 0) {
            return 0
        }

        if (!claims.claim(utcDayKey("audit-purge", clock))) {
            logger.debug("AUDIT | Retention purge of today is claimed by another instance, skipping")
            return 0
        }

        val cutoff = clock.millis() - retentionDays * DAY_MILLIS
        return repository.deleteBefore(cutoff)
            .also { removed -> if (removed > 0) logger.info("AUDIT | Removed $removed event(s) older than $retentionDays day(s)") }
    }

    fun count(): Long =
        repository.count()

    private fun encodeDetails(details: Map<String, Any?>): String? =
        details.takeIf { it.isNotEmpty() }?.let { DEFAULT_OBJECT_MAPPER.writeValueAsString(it) }

    override fun getLogger(): Logger =
        journalist.logger

}
