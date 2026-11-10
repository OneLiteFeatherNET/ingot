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

import com.reposilite.audit.api.AuditAction
import com.reposilite.audit.api.AuditAction.UPLOAD_CHECKSUM_MISMATCH
import com.reposilite.audit.api.AuditActor
import com.reposilite.audit.application.uploadChecksumMismatchAuditEvent
import com.reposilite.maven.api.Checksum
import com.reposilite.maven.api.UploadChecksumMismatchEvent
import com.reposilite.maven.api.UploadChecksumOutcome
import com.reposilite.storage.api.toLocation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class UploadChecksumMismatchAuditTest {

    @Test
    fun `should record a refused checksum file as an upload checksum mismatch`() {
        // given: a sha1 file that a deployer sent to releases and that was refused
        val event = UploadChecksumMismatchEvent(
            repository = "releases",
            gav = "com/example/lib/1.0.0/lib-1.0.0.jar.sha1".toLocation(),
            by = "deployer@10.0.0.2",
            algorithm = Checksum.SHA1,
            outcome = UploadChecksumOutcome.REJECTED,
        )

        // when: the event is turned into an audit event
        val audit = uploadChecksumMismatchAuditEvent(event)

        // then: the action, the actor, the repository and the path are recorded
        assertThat(audit.action).describedAs("action of a refused checksum file").isEqualTo(UPLOAD_CHECKSUM_MISMATCH)
        assertThat(audit.actor).describedAs("actor of a refused checksum file").isEqualTo(AuditActor.named("deployer", "10.0.0.2"))
        assertThat(audit.repository).describedAs("repository of a refused checksum file").isEqualTo("releases")
        assertThat(audit.path).describedAs("path of a refused checksum file").isEqualTo("com/example/lib/1.0.0/lib-1.0.0.jar.sha1")
    }

    @Test
    fun `should record the algorithm and the outcome as details without any digest`() {
        // given: a sha256 file that was stored under warn
        val event = UploadChecksumMismatchEvent(
            repository = "releases",
            gav = "com/example/lib/1.0.0/lib-1.0.0.jar.sha256".toLocation(),
            by = "deployer@10.0.0.2",
            algorithm = Checksum.SHA256,
            outcome = UploadChecksumOutcome.ACCEPTED,
        )

        // when: the event is turned into an audit event
        val audit = uploadChecksumMismatchAuditEvent(event)

        // then: the details name the algorithm and the outcome
        assertThat(audit.details).describedAs("details of a checksum file stored under warn")
            .containsExactlyInAnyOrderEntriesOf(mapOf("algorithm" to "sha256", "outcome" to "accepted"))
    }

    @Test
    fun `should append the upload checksum action after every action that existed before`() {
        // when: the actions are listed in their stored order
        val actions = AuditAction.entries

        // then: the new action comes after every action that existed before it, so the ordinals of those do not move
        val position = actions.indexOf(UPLOAD_CHECKSUM_MISMATCH)
        assertThat(actions[position - 1]).describedAs("action before the new one").isEqualTo(AuditAction.REPOSITORY_DELETED)
    }
}
