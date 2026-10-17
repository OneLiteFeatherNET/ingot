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

package com.reposilite.maven

import com.reposilite.journalist.Journalist
import com.reposilite.storage.StorageProvider
import com.reposilite.storage.filesystem.FileSystemStorageProvider
import com.reposilite.storage.filesystem.FileSystemStorageProviderFactory
import com.reposilite.storage.walkFiles
import java.time.Clock
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import kotlin.io.path.fileStore

/**
 * A repository whose usage is measured.
 *
 * @param quota the quota as configured (for example "100%" or "10GB"), null when the storage has none
 * @param storageType the storage provider type as configured, "fs" for the file system or "s3" for object storage
 * @param group true for a loopback group, which proxies other local repositories and stores nothing itself
 */
internal data class StorageTarget(
    val id: String,
    val storage: StorageProvider,
    val quota: String?,
    val storageType: String = "fs",
    val group: Boolean = false,
)

/**
 * Usage of one repository. [quotaBytes] is null when the quota is unknown: no quota is configured, or the
 * configured value cannot be read. [storageType] and [group] tell a client what the quota means, if anything.
 */
data class RepositoryStorageUsage(
    val id: String,
    val usedBytes: Long,
    val quota: String?,
    val quotaBytes: Long?,
    val fileCount: Long,
    val storageType: String,
    val group: Boolean,
)

/**
 * The last computed usage together with the state of the computation.
 *
 * @param computedAt when the figures were computed, null while nothing has been computed yet
 * @param computing true while a computation is running; the figures shown are then the previous ones
 */
data class StorageUsage(
    val computedAt: Instant?,
    val computing: Boolean,
    val repositories: List<RepositoryStorageUsage>,
) {

    val totalBytes: Long
        get() = repositories.sumOf { it.usedBytes }

}

/**
 * Measures how much each repository stores.
 *
 * The walk lists every file, which is slow on object storage, so it never runs on a request thread: [refresh]
 * hands the work to [executor] and returns a future. Overlapping refreshes share one computation, and [current]
 * keeps serving the previous figures until the new ones are ready. [clock] stamps the result.
 */
internal class StorageUsageService(
    private val journalist: Journalist,
    private val clock: Clock,
    private val targets: () -> List<StorageTarget>,
    private val executor: Executor,
) {

    private val lock = Any()
    private var computedAt: Instant? = null
    private var repositories: List<RepositoryStorageUsage> = emptyList()
    private var inFlight: CompletableFuture<StorageUsage>? = null

    fun current(): StorageUsage =
        synchronized(lock) {
            StorageUsage(computedAt = computedAt, computing = inFlight != null, repositories = repositories)
        }

    /**
     * Starts a computation unless one is already running, and returns the future of the running one.
     */
    fun refresh(): CompletableFuture<StorageUsage> =
        synchronized(lock) {
            inFlight ?: CompletableFuture.supplyAsync({ compute() }, executor).also { future ->
                inFlight = future
                future.whenComplete { _, _ -> synchronized(lock) { if (inFlight === future) inFlight = null } }
            }
        }

    private fun compute(): StorageUsage {
        val measured = targets().map { measure(it) }
        synchronized(lock) {
            repositories = measured
            computedAt = clock.instant()
        }
        return current()
    }

    private fun measure(target: StorageTarget): RepositoryStorageUsage {
        val files = runCatching {
            target.storage.walkFiles { path, reason ->
                journalist.logger.warn("STORAGE | Cannot read ${target.id} at '${path.ifEmpty { "/" }}': $reason")
            }
        }
            .onFailure { journalist.logger.warn("STORAGE | Cannot measure usage of ${target.id}: ${it.message}") }
            .getOrElse { emptyList() }

        return RepositoryStorageUsage(
            id = target.id,
            usedBytes = files.sumOf { it.sizeBytes },
            quota = target.quota,
            quotaBytes = quotaBytes(target),
            fileCount = files.size.toLong(),
            storageType = target.storageType,
            group = target.group,
        )
    }

    /**
     * A percentage is taken of the total capacity of the disk the repository lives on, which is the same reference
     * the quota is configured against. A fixed size is read as written.
     */
    private fun quotaBytes(target: StorageTarget): Long? {
        val quota = target.quota ?: return null
        return runCatching {
            if (quota.endsWith("%")) {
                val root = (target.storage as? FileSystemStorageProvider)?.rootDirectory ?: return null
                (root.fileStore().totalSpace * (quota.dropLast(1).toDouble() / 100.0)).toLong()
            } else {
                FileSystemStorageProviderFactory.displaySizeToBytesCount(quota)
            }
        }.getOrNull()
    }

}
