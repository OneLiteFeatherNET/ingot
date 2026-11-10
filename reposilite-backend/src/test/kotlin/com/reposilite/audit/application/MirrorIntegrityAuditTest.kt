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

import com.reposilite.audit.api.AuditAction
import com.reposilite.audit.api.AuditActor
import com.reposilite.maven.ChecksumPolicy
import com.reposilite.maven.ChecksumResult
import com.reposilite.maven.CopyOutcome
import com.reposilite.maven.api.MirrorIntegrityEvent
import com.reposilite.storage.api.toLocation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class MirrorIntegrityAuditTest {

    private fun event(result: ChecksumResult, outcome: CopyOutcome) = MirrorIntegrityEvent(
        result = result,
        repository = "warn-mirror",
        path = "com/example/lib/1.0.0/lib-1.0.0.jar".toLocation().toString(),
        host = "https://warn.example/releases",
        policy = ChecksumPolicy.WARN,
        algorithm = "sha256",
        outcome = outcome,
    )

    @Test
    fun `should write a failed mismatch action for a mismatching copy`() {
        // when: a mismatch is mapped to an audit event
        val audit = mirrorIntegrityAuditEvent(event(ChecksumResult.MISMATCH, CopyOutcome.STORED))

        // then: the event is a failed MIRROR_CHECKSUM_MISMATCH, written by the system
        assertThat(audit.action).describedAs("action of a mismatch").isEqualTo(AuditAction.MIRROR_CHECKSUM_MISMATCH)
        assertThat(audit.success).describedAs("success of a mismatch").isFalse()
        assertThat(audit.actor).describedAs("actor of a mirror fetch").isEqualTo(AuditActor.SYSTEM_ACTOR)
        assertThat(audit.repository).describedAs("repository of a mismatch").isEqualTo("warn-mirror")
    }

    @Test
    fun `should write a failed missing action for a host without checksum files`() {
        // when: a missing checksum is mapped to an audit event
        val audit = mirrorIntegrityAuditEvent(event(ChecksumResult.MISSING, CopyOutcome.REFUSED))

        // then: the event is a failed MIRROR_CHECKSUM_MISSING
        assertThat(audit.action).describedAs("action of a missing checksum").isEqualTo(AuditAction.MIRROR_CHECKSUM_MISSING)
        assertThat(audit.success).describedAs("success of a missing checksum").isFalse()
    }

    @Test
    fun `should carry the host, the policy, the algorithm and the outcome as details`() {
        // when: a mismatch that was stored under warn is mapped
        val details = mirrorIntegrityAuditEvent(event(ChecksumResult.MISMATCH, CopyOutcome.STORED)).details

        // then: the details name exactly these keys, with their values in lower case
        assertThat(details)
            .describedAs("details of a mirror integrity event")
            .containsOnlyKeys("host", "policy", "algorithm", "outcome")
            .containsEntry("host", "https://warn.example/releases")
            .containsEntry("policy", "warn")
            .containsEntry("algorithm", "sha256")
            .containsEntry("outcome", "stored")
    }

}
