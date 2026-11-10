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

package com.reposilite.configuration.shared

import com.fasterxml.jackson.databind.JsonNode
import com.reposilite.ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER
import com.reposilite.audit.api.AuditAction.REPOSITORY_CREATED
import com.reposilite.audit.api.AuditAction.REPOSITORY_DELETED
import com.reposilite.audit.api.AuditActor
import com.reposilite.maven.RepositoryType
import com.reposilite.maven.RepositoryVisibility.PRIVATE
import com.reposilite.maven.RepositoryVisibility.PUBLIC
import com.reposilite.maven.application.MavenSettings
import com.reposilite.maven.application.RepositorySettings
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class RepositoryAuditEventsTest {

    private val actor = AuditActor.named("admin", null)

    private fun maven(vararg repositories: RepositorySettings): JsonNode =
        DEFAULT_OBJECT_MAPPER.valueToTree(MavenSettings(repositories = repositories.toList()))

    @Test
    fun `records a creation with the id, the visibility and the type of the new repository`() {
        val before = maven(RepositorySettings("releases"))
        val after = maven(RepositorySettings("releases"), RepositorySettings("internal", PRIVATE, RepositoryType.HOSTED))

        val events = repositoryAuditEvents(before, after, actor)

        val event = events.single()
        assertThat(event.action).isEqualTo(REPOSITORY_CREATED)
        assertThat(event.repository).isEqualTo("internal")
        assertThat(event.actor).isEqualTo(actor)
        assertThat(event.details).containsEntry("visibility", "PRIVATE").containsEntry("type", "HOSTED")
    }

    @Test
    fun `records a removal with the id of the removed repository`() {
        val before = maven(RepositorySettings("releases"), RepositorySettings("snapshots", PRIVATE))
        val after = maven(RepositorySettings("releases"))

        val event = repositoryAuditEvents(before, after, actor).single()

        assertThat(event.action).isEqualTo(REPOSITORY_DELETED)
        assertThat(event.repository).isEqualTo("snapshots")
        assertThat(event.details).containsEntry("visibility", "PRIVATE").containsEntry("type", null)
    }

    @Test
    fun `records a rename as a removal of the old id and a creation of the new id`() {
        val before = maven(RepositorySettings("a"))
        val after = maven(RepositorySettings("b"))

        val events = repositoryAuditEvents(before, after, actor)

        assertThat(events.map { it.action to it.repository })
            .describedAs("events of the rename")
            .containsExactly(REPOSITORY_DELETED to "a", REPOSITORY_CREATED to "b")
    }

    @Test
    fun `records nothing for a visibility change of an existing repository`() {
        val before = maven(RepositorySettings("releases", PUBLIC))
        val after = maven(RepositorySettings("releases", PRIVATE))

        assertThat(repositoryAuditEvents(before, after, actor)).isEmpty()
    }

    @Test
    fun `records nothing when the repositories are unchanged`() {
        val settings = maven(RepositorySettings("releases"))

        assertThat(repositoryAuditEvents(settings, settings, actor)).isEmpty()
    }

}
