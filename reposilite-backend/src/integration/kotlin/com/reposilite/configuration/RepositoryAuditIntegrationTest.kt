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

@file:Suppress("FunctionName")

package com.reposilite.configuration

import com.reposilite.RecommendedLocalSpecificationJunitExtension
import com.reposilite.ReposiliteSpecification
import com.reposilite.audit.api.AuditAction
import com.reposilite.audit.api.AuditPageDto
import com.reposilite.maven.application.MavenSettings
import io.javalin.http.HttpStatus.OK
import kong.unirest.core.Unirest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class RepositoryAuditIntegrationTest : ReposiliteSpecification() {

    @Test
    fun `should audit the removal of a repository with its id and the manager`() {
        // given: a manager token and the stored maven domain
        val (token, secret) = useDefaultManagementToken()
        val current = storedMavenSettings(token, secret)
        assertThat(current.repositories.map { it.id }).`as`("repositories before the change").contains("snapshots")

        // when: the domain is saved without the repository "snapshots" and nothing else changes
        val saved = saveMavenSettings(token, secret, current.copy(repositories = current.repositories.filter { it.id != "snapshots" }))
        assertThat(saved.status).`as`("status of the settings update").isEqualTo(OK.code)

        // then: the audit log holds exactly one deletion, which names the repository and the manager
        val page = Unirest.get("$base/api/audit?action=${AuditAction.REPOSITORY_DELETED}")
            .basicAuth(token, secret)
            .asJacksonObject(AuditPageDto::class)
            .body
        assertThat(page.events.map { it.repository }).`as`("repositories of the deletions").containsExactly("snapshots")
        assertThat(page.events.single().actor).`as`("actor of the deletion").isEqualTo("manager")
    }

    @Test
    fun `should keep the settings changed event next to the repository event`() {
        // given: a manager token and the stored maven domain
        val (token, secret) = useDefaultManagementToken()
        val current = storedMavenSettings(token, secret)

        // when: the domain is saved without the repository "snapshots"
        saveMavenSettings(token, secret, current.copy(repositories = current.repositories.filter { it.id != "snapshots" }))

        // then: the settings change is still recorded for the maven domain
        val page = Unirest.get("$base/api/audit?action=${AuditAction.SETTINGS_CHANGED}")
            .basicAuth(token, secret)
            .asJacksonObject(AuditPageDto::class)
            .body
        assertThat(page.events.map { it.details.toString() })
            .`as`("settings changes in the audit log")
            .anyMatch { it.contains("\"domain\":\"maven\"") }
    }

    private fun storedMavenSettings(token: String, secret: String): MavenSettings =
        Unirest.get("$base/api/settings/domain/maven")
            .basicAuth(token, secret)
            .asJacksonObject(MavenSettings::class)
            .body

    private fun saveMavenSettings(token: String, secret: String, settings: MavenSettings) =
        Unirest.put("$base/api/settings/domain/maven").basicAuth(token, secret).body(settings).asString()

}
