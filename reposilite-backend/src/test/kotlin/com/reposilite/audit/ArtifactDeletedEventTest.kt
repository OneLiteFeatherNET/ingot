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

import com.reposilite.audit.api.AuditAction.ARTIFACT_DELETED
import com.reposilite.audit.api.AuditActor
import com.reposilite.audit.application.artifactDeletedEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class ArtifactDeletedEventTest {

    @Test
    fun `should record a file deleted by a manager as an artifact deletion`() {
        // when: a manager deletes a file by hand
        val event = artifactDeletedEvent(repository = "releases", path = "net/example/app/1.0/app.pom", by = "admin@10.0.0.2", cleanupRunId = null)

        // then: the deletion is recorded with the repository, the path and the actor
        assertThat(event).isNotNull
        assertThat(event!!.action).isEqualTo(ARTIFACT_DELETED)
        assertThat(event.actor).isEqualTo(AuditActor.named("admin", "10.0.0.2"))
        assertThat(event.repository).isEqualTo("releases")
        assertThat(event.path).isEqualTo("net/example/app/1.0/app.pom")
    }

    @Test
    fun `should leave a file deleted by a cleanup run to the run event`() {
        // when: a cleanup run removes a file
        val event = artifactDeletedEvent(repository = "releases", path = "net/example/app/1.0/app.pom", by = "cleanup-scheduler", cleanupRunId = "run-1")

        // then: no artifact event is written, the run is recorded once with its totals
        assertThat(event).isNull()
    }

}
