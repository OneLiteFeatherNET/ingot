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

package com.reposilite.cleanup

import com.reposilite.cleanup.application.CleanupPolicy
import com.reposilite.audit.api.AuditAction
import com.reposilite.audit.api.AuditActor
import com.reposilite.audit.api.AuditEvent
import com.reposilite.cleanup.application.CleanupScope
import com.reposilite.journalist.Journalist
import com.reposilite.plugin.Extensions
import com.reposilite.plugin.api.Facade
import com.reposilite.shared.ErrorResponse
import com.reposilite.shared.errorResponse
import com.reposilite.shared.notFoundError
import com.reposilite.storage.StorageProvider
import com.reposilite.storage.walkFiles
import io.javalin.http.HttpStatus.CONFLICT
import panda.std.Result
import panda.std.asSuccess
import java.time.Clock
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * A repository as the cleanup sees it.
 *
 * @param storesMirrors true when the repository stores copies of mirrored artifacts
 * @param cachedVersions the version directories that hold a recorded mirror copy, with their last access. A version
 *   without a record is never a mirror copy, even in a repository that stores mirror copies.
 */
internal data class CleanupTarget(
    val name: String,
    val storage: StorageProvider,
    val storesMirrors: Boolean,
    val cachedVersions: Map<String, Instant>,
)

/**
 * Previews and runs cleanup policies.
 *
 * A dry run and a run plan the same way, from a fresh inventory. A run deletes through [deleter] and records its
 * outcome; the last [CLEANUP_RUN_HISTORY_LIMIT] runs of each policy are kept. One policy runs at a time: a second
 * request for a policy that is still running is refused with a conflict.
 *
 * Every run that starts emits one [AuditAction.CLEANUP_RUN] event with its totals. The deletions of the run are not
 * audited one by one: each one carries the run id on its delete event, and the audit log leaves them to the run.
 */
class CleanupService internal constructor(
    private val journalist: Journalist,
    private val clock: Clock,
    private val policies: () -> List<CleanupPolicy>,
    private val targets: () -> List<CleanupTarget>,
    private val deleter: VersionDeleter,
    private val runs: CleanupRunRepository,
    private val extensions: Extensions = Extensions(journalist),
) : Facade {

    private val running = ConcurrentHashMap.newKeySet<String>()

    /** Lists what a run would delete, and deletes nothing. */
    fun preview(policyId: String): Result<CleanupPreview, ErrorResponse> {
        val policy = findPolicy(policyId) ?: return notFoundError("Cleanup policy $policyId not found")
        val (deletions, warnings) = plan(policy)
        return CleanupPreview(
            policyId = policyId,
            evaluatedAt = clock.instant(),
            count = deletions.size,
            totalBytes = deletions.sumOf { it.sizeBytes },
            deletions = deletions,
            warnings = warnings,
        ).asSuccess()
    }

    /** Deletes what the policy plans right now and records the outcome. */
    fun run(policyId: String, actor: CleanupActor, trigger: CleanupTrigger): Result<CleanupRun, ErrorResponse> {
        val policy = findPolicy(policyId) ?: return notFoundError("Cleanup policy $policyId not found")
        if (!running.add(policyId)) {
            return errorResponse(CONFLICT, "Cleanup policy $policyId is already running")
        }

        try {
            val startedAt = clock.instant()
            val runId = UUID.randomUUID().toString()
            val (deletions, _) = plan(policy)
            val outcomes = deletions.map { deletion -> deletion to deleter.delete(deletion.repository, deletion.path, actor, runId) }

            val succeeded = outcomes.filter { (_, result) -> result.isOk }.map { (deletion, _) -> deletion }
            val failures = outcomes
                .filter { (_, result) -> result.isErr }
                .map { (deletion, result) -> "${deletion.repository}/${deletion.path}: ${result.error.message}" }
            val status = when {
                failures.isEmpty() -> CleanupRunStatus.SUCCEEDED
                succeeded.isEmpty() -> CleanupRunStatus.FAILED
                else -> CleanupRunStatus.PARTIAL
            }

            val run = CleanupRun(
                id = runId,
                policyId = policyId,
                trigger = trigger,
                startedAt = startedAt,
                finishedAt = clock.instant(),
                status = status,
                deletedCount = succeeded.size,
                deletedBytes = succeeded.sumOf { it.sizeBytes },
                failures = failures,
            )
            runs.insert(run)
            runs.keepNewest(policyId, CLEANUP_RUN_HISTORY_LIMIT)
            journalist.logger.info("CLEANUP | Policy $policyId ($trigger by ${actor.by}) finished as $status: ${run.deletedCount} version(s), ${run.deletedBytes} bytes deleted, ${failures.size} failure(s)")
            extensions.emitEvent(
                AuditEvent(
                    action = AuditAction.CLEANUP_RUN,
                    actor = if (trigger == CleanupTrigger.SCHEDULED) AuditActor.SYSTEM_ACTOR else AuditActor.fromSessionIdentifier(actor.by),
                    repository = policy.repositories.singleOrNull(),
                    details = mapOf(
                        "runId" to run.id,
                        "policy" to policyId,
                        "scope" to policy.scope.name,
                        "trigger" to trigger.name,
                        "status" to status.name,
                        "deletedCount" to run.deletedCount,
                        "deletedBytes" to run.deletedBytes,
                    ),
                    success = status == CleanupRunStatus.SUCCEEDED,
                )
            )
            return run.asSuccess()
        } finally {
            running.remove(policyId)
        }
    }

    /** The runs of one policy, or of all policies, newest first. */
    fun runs(policyId: String?): List<CleanupRun> =
        runs.find(policyId)

    /** Runs a policy for the scheduler. The scheduler has no access token, so its deletions are not checked against one. */
    internal fun runScheduled(policyId: String): Result<CleanupRun, ErrorResponse> =
        run(policyId, CleanupActor(by = SCHEDULER_ACTOR, accessToken = null), CleanupTrigger.SCHEDULED)

    private fun findPolicy(policyId: String): CleanupPolicy? =
        policies().firstOrNull { it.id == policyId }

    private fun plan(policy: CleanupPolicy): Pair<List<PlannedDeletion>, List<String>> {
        val allTargets = targets()
        val inventory = allTargets
            .filter { it.name in policy.repositories }
            .flatMap { target -> CleanupInventory.versionsOf(target.name, target.storage.walkFiles(), target.cachedVersions) }
        return CleanupPlanner.plan(policy, inventory, clock) to warningsFor(policy, allTargets)
    }

    private fun warningsFor(policy: CleanupPolicy, allTargets: List<CleanupTarget>): List<String> =
        policy.repositories.flatMap { name ->
            val target = allTargets.firstOrNull { it.name == name }
            when {
                target == null -> listOf("repository '$name' does not exist")
                policy.scope == CleanupScope.MIRROR_CACHE && !target.storesMirrors ->
                    listOf("repository '$name' stores no mirrored artifacts, so the MIRROR_CACHE scope finds nothing there")
                policy.scope == CleanupScope.MIRROR_CACHE && target.cachedVersions.isEmpty() ->
                    listOf(
                        "repository '$name' has no recorded mirror copies yet, so MIRROR_CACHE finds nothing there. " +
                            "Copies cached before provenance was recorded are never deleted by MIRROR_CACHE"
                    )
                policy.scope == CleanupScope.MIRROR_CACHE ->
                    listOf("copies cached before provenance was recorded are never deleted by MIRROR_CACHE, only copies with a recorded mirror store are considered")
                else -> emptyList()
            }
        }

    companion object {
        const val SCHEDULER_ACTOR = "cleanup-scheduler"
    }

}
