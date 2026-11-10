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

package net.onelitefeather.ingot.support

import com.reposilite.ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER
import com.reposilite.audit.api.AuditAction
import com.reposilite.audit.api.AuditActor
import com.reposilite.audit.api.AuditEvent
import com.reposilite.plugin.api.Facade
import java.time.Clock
import java.time.Duration
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/** A bundle that the guard found a known secret in. Nothing of it is served. The message never names the value. */
class RedactionRefusedException : RuntimeException("support bundle refused: redaction check failed")

/** A second build while one is running, after the bounded wait. */
class BundleBusyException : RuntimeException("A support bundle is already being built, try again shortly")

/** A bundle that is ready to be served: the zip, its download name and the figures of the audit event. */
class SupportBundle(val bytes: ByteArray, val fileName: String, val files: Int, val redactions: Int)

/**
 * Serves the system information and the bundle. A bundle is built under one permit, so repeated downloads cannot load the
 * instance. Every answer passes the final check first, and a download writes one audit event. The page is not audited.
 *
 * [checkLeaks] is the last check; it is the guard of the snapshot by default and a test double in the tests that prove the refusal.
 */
class SupportBundleService(
    private val collector: SupportInformationCollector,
    private val clock: Clock,
    private val waitBound: Duration = Duration.ofSeconds(2),
    private val audit: (AuditEvent) -> Unit,
    private val checkLeaks: (SupportSnapshot) -> List<String> = { snapshot -> snapshot.guard.leaks(snapshot.files) },
    private val maxBytes: Long = SupportBundleWriter.MAX_BYTES,
) : Facade {

    private val permit = Semaphore(1)

    /**
     * The system information of this instance, after the final check of its JSON.
     *
     * @throws RedactionRefusedException when the check finds a known secret in the JSON
     */
    fun systemInformation(): SystemInformation {
        val snapshot = collector.collect()
        val json = DEFAULT_OBJECT_MAPPER.writeValueAsBytes(snapshot.systemInfo)
        if (checkLeaks(snapshot.copy(files = mapOf(SYSTEM_INFO_ENTRY to json))).isNotEmpty()) {
            throw RedactionRefusedException()
        }
        return snapshot.systemInfo
    }

    /**
     * Builds the bundle for [actor] and audits the download.
     *
     * @throws BundleBusyException when another build holds the permit after the bounded wait
     * @throws RedactionRefusedException when the final check finds a known secret; an unsuccessful event is written
     * @throws BundleTooLargeException when the archive exceeds the cap; an unsuccessful event is written
     */
    fun bundle(actor: AuditActor): SupportBundle {
        if (!permit.tryAcquire(waitBound.toMillis(), TimeUnit.MILLISECONDS)) throw BundleBusyException()
        try {
            val snapshot = collector.collect()
            if (checkLeaks(snapshot).isNotEmpty()) {
                refused(actor, reason = "redaction check failed")
                throw RedactionRefusedException()
            }

            val archive = try {
                SupportBundleWriter.write(snapshot.files, maxBytes)
            } catch (exception: BundleTooLargeException) {
                refused(actor, reason = "bundle too large")
                throw exception
            }

            val files = snapshot.files.size
            audit(
                AuditEvent(
                    action = AuditAction.SUPPORT_BUNDLE_DOWNLOADED,
                    actor = actor,
                    details = mapOf("files" to files, "redactions" to snapshot.redactions),
                )
            )
            return SupportBundle(archive, fileName(snapshot.systemInfo.instance), files, snapshot.redactions)
        } finally {
            permit.release()
        }
    }

    private fun refused(actor: AuditActor, reason: String) {
        audit(AuditEvent(action = AuditAction.SUPPORT_BUNDLE_DOWNLOADED, actor = actor, details = mapOf("reason" to reason), success = false))
    }

    private fun fileName(instance: String): String {
        val stamp = TIMESTAMP.format(clock.instant())
        return "ingot-support-${instance.replace(Regex("[^A-Za-z0-9._-]"), "_")}-$stamp.zip"
    }

    private companion object {
        const val SYSTEM_INFO_ENTRY = "system-info.json"
        val TIMESTAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC)
    }
}
