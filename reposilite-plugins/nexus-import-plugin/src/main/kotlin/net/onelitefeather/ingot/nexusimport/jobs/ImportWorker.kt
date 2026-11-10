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
package net.onelitefeather.ingot.nexusimport.jobs

import net.onelitefeather.ingot.nexusimport.copy.ConflictPolicy
import net.onelitefeather.ingot.nexusimport.copy.FileCopier
import net.onelitefeather.ingot.nexusimport.copy.TargetWriter
import net.onelitefeather.ingot.nexusimport.nexus.NexusAsset
import net.onelitefeather.ingot.nexusimport.nexus.NexusClient
import net.onelitefeather.ingot.nexusimport.nexus.NexusHttpException
import java.nio.file.Path
import java.time.Clock
import java.time.Duration

/** One Nexus repository and the Ingot repository its files go to. */
data class CopyTarget(val nexusName: String, val targetName: String)

/** How a worker run ended. Only [COMPLETED] means every file of every target was handled. */
enum class RunOutcome {
    COMPLETED,
    PAUSED_READ_ONLY,
    PAUSED_CREDENTIALS,
    PAUSED_OPERATOR,
    CANCELLED,
    LEASE_LOST,
    NOT_ACQUIRED,
    NOT_STARTED_READ_ONLY,
    NO_PASSWORD_HERE,
    NOT_RUNNABLE,
    FAILED,
}

/**
 * Copies the files of a job for one worker token. Before each file it checks the read-only switch, its own lease deadline and
 * the operator's pause and cancel flags, so a worker that lost its lease or was told to stop starts no further file. Each
 * finished file is committed with the job's lease in one transaction; a failed commit ends the run. Files already final in
 * the job are skipped, which is what lets a second worker continue from the first one's rows without a stored listing token.
 */
internal class ImportWorker(
    private val store: JobStore,
    private val clock: Clock,
    private val lease: Duration,
    private val nexus: NexusClient,
    private val target: TargetWriter,
    private val tempDir: Path,
    private val readOnly: () -> Boolean,
    private val token: String,
) {

    private val copier = FileCopier(open = { asset -> nexus.download(asset) }, target = target, tempDir = tempDir)

    fun copy(jobId: Long, targets: List<CopyTarget>, policy: ConflictPolicy): RunOutcome {
        if (!store.acquire(jobId, token)) return RunOutcome.NOT_ACQUIRED
        var deadline = clock.millis() + lease.toMillis()
        val final = store.assets(jobId)
            .filter { it.result in FINAL }
            .map { it.repository to it.path }
            .toSet()

        for (copyTarget in targets) {
            for (asset in listAll(copyTarget.nexusName).sortedWith(ORDER)) {
                if (readOnly()) {
                    store.pause(jobId, token, PauseReason.READ_ONLY)
                    return RunOutcome.PAUSED_READ_ONLY
                }
                if (clock.millis() >= deadline) return RunOutcome.LEASE_LOST
                val control = store.job(jobId)
                if (control?.cancelRequested == true) {
                    return if (store.finish(jobId, token, JobStatus.CANCELLED)) RunOutcome.CANCELLED else RunOutcome.LEASE_LOST
                }
                if (control?.pauseRequested == true) {
                    return if (store.pause(jobId, token, PauseReason.OPERATOR)) RunOutcome.PAUSED_OPERATOR else RunOutcome.LEASE_LOST
                }
                if (copyTarget.targetName to asset.path in final) continue

                val result = try {
                    copier.copy(asset, policy, copyTarget.targetName)
                } catch (exception: NexusHttpException) {
                    if (exception.status == 401 || exception.status == 403) {
                        store.pause(jobId, token, PauseReason.CREDENTIALS_NEEDED)
                        return RunOutcome.PAUSED_CREDENTIALS
                    }
                    AssetResult.FAILED
                }
                val size = asset.fileSize.coerceAtLeast(0)
                val detail = if (result == AssetResult.CONFLICT) conflictDetail(asset, copyTarget.targetName) else null
                val committed = store.commitAsset(jobId, token, AssetRecord(copyTarget.targetName, asset.path, size, result, detail), asset.path)
                if (!committed) return RunOutcome.LEASE_LOST
                deadline = clock.millis() + lease.toMillis()
                if (result == AssetResult.CONFLICT && policy == ConflictPolicy.ABORT) break
            }
        }
        return if (store.finish(jobId, token, JobStatus.COMPLETED)) RunOutcome.COMPLETED else RunOutcome.LEASE_LOST
    }

    /** Both checksums of a conflict, so the operator can see what differs. */
    private fun conflictDetail(asset: NexusAsset, targetName: String): String =
        "nexus sha1 ${asset.checksums.sha1 ?: "none"}; target sha1 ${target.existing(targetName, asset.path)?.sha1 ?: "none"}"

    private fun listAll(repository: String): List<NexusAsset> = nexus.listAll(repository)

    companion object {
        /** Results that need no further work in a later run. A conflict is final under refuse and abort. */
        private val FINAL = setOf(AssetResult.COPIED, AssetResult.SKIPPED_SAME, AssetResult.GONE, AssetResult.CONFLICT)

        /** Artifacts first, then the metadata and checksum sidecars of the directory, so a metadata deploy never sees a half copy. */
        internal val ORDER: Comparator<NexusAsset> = compareBy<NexusAsset>({ isMetadata(it.path) }, { it.path })

        internal fun isMetadata(path: String): Boolean =
            path.endsWith("maven-metadata.xml") || path.endsWith(".md5") || path.endsWith(".sha1")
    }
}

/** Every asset of [repository], following the continuation token to the end. */
internal fun NexusClient.listAll(repository: String): List<NexusAsset> {
    val all = mutableListOf<NexusAsset>()
    var token: String? = null
    do {
        val page = assetPage(repository, token)
        all += page.items
        token = page.continuationToken
    } while (token != null)
    return all
}

enum class VerifyStatus { VERIFIED, MISMATCH, CONFLICTED }

/**
 * The verify pass: compares the path set, the size and the sha1 of every file of each target with the Nexus listing. A
 * repository with a conflict is never verified, whatever its files look like.
 */
class Verifier(private val nexus: NexusClient, private val target: TargetWriter) {

    fun verify(targets: List<CopyTarget>, conflicted: Set<String>): Map<String, VerifyStatus> =
        targets.associate { copyTarget ->
            val listed = nexus.listAll(copyTarget.nexusName).associateBy { it.path }
            val held = target.list(copyTarget.targetName)
            val differing = (listed.keys + held.keys).filter { path ->
                val expected = listed[path]
                val actual = held[path]
                expected == null || actual == null ||
                    expected.fileSize != actual.size ||
                    (expected.checksums.sha1 != null && expected.checksums.sha1 != actual.sha1)
            }
            copyTarget.targetName to when {
                copyTarget.targetName in conflicted -> VerifyStatus.CONFLICTED
                differing.isEmpty() -> VerifyStatus.VERIFIED
                else -> VerifyStatus.MISMATCH
            }
        }
}
