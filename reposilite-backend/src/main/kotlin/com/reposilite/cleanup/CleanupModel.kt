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

import com.reposilite.shared.ErrorResponse
import com.reposilite.token.AccessTokenIdentifier
import panda.std.Result
import java.time.Instant

/** How many runs are kept per policy. Older runs are dropped when a new one is recorded. */
const val CLEANUP_RUN_HISTORY_LIMIT = 20

/** Who started a run: a manager with a token, or the scheduler, which has no token. */
data class CleanupActor(
    val by: String,
    val accessToken: AccessTokenIdentifier?,
)

enum class CleanupTrigger {
    MANUAL,
    SCHEDULED,
}

/**
 * SUCCEEDED when every planned deletion worked, PARTIAL when some failed, FAILED when every attempted deletion failed.
 */
enum class CleanupRunStatus {
    SUCCEEDED,
    PARTIAL,
    FAILED,
}

/**
 * The record of one run of a policy. [failures] names every deletion that did not work, with its reason.
 */
data class CleanupRun(
    val id: String,
    val policyId: String,
    val trigger: CleanupTrigger,
    val startedAt: Instant,
    val finishedAt: Instant,
    val status: CleanupRunStatus,
    val deletedCount: Int,
    val deletedBytes: Long,
    val failures: List<String>,
)

/**
 * What a dry run would do. Nothing has been deleted when this is returned.
 */
data class CleanupPreview(
    val policyId: String,
    val evaluatedAt: Instant,
    val count: Int,
    val totalBytes: Long,
    val deletions: List<PlannedDeletion>,
    val warnings: List<String>,
)

/**
 * Deletes one version directory of a repository. The production implementation goes through the repository
 * delete service, so that the metadata of the artifact is updated as well. [runId] names the run that asks for it.
 */
fun interface VersionDeleter {
    fun delete(repository: String, path: String, actor: CleanupActor, runId: String): Result<Unit, ErrorResponse>
}

/** Run history, newest first when read. */
interface CleanupRunRepository {

    fun insert(run: CleanupRun)

    /** All runs of [policyId], or of every policy when it is null, newest first. */
    fun find(policyId: String?): List<CleanupRun>

    /** Drops the runs of [policyId] beyond the newest [count]. */
    fun keepNewest(policyId: String, count: Int)

}
