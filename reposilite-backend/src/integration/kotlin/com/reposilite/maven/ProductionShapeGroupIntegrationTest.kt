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

import com.reposilite.ReposiliteParameters
import com.reposilite.RecommendedLocalSpecificationJunitExtension
import com.reposilite.ReposiliteSpecification
import com.reposilite.configuration.local.infrastructure.DatabaseConnectionFactory
import com.reposilite.configuration.shared.SharedConfigurationFacade
import com.reposilite.maven.application.MavenSettings
import com.reposilite.journalist.backend.InMemoryLogger
import kong.unirest.core.Unirest.get
import net.onelitefeather.ingot.identity.storage.ProductionShapeDataset
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * Boots the server on a legacy database that holds the production shape. The shared configuration is loaded from the
 * document an operator would write, so the empty mirrored entry of the group goes through the real settings path. The
 * group answers from its first loopback mirror, so no request reaches the external proxy.
 */
@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class ProductionShapeGroupIntegrationTest : ReposiliteSpecification() {

    override fun overrideParameters(parameters: ReposiliteParameters) {
        // The legacy tables are written before the server starts, so its start is the upgrade of that database.
        val url = "jdbc:sqlite:${reposiliteWorkingDirectory.resolve("reposilite.db").absolutePath}"
        parameters.database = url
        DatabaseConnectionFactory.createConnection(reposiliteWorkingDirectory.toPath(), url, 1).use { connection ->
            ProductionShapeDataset.seedLegacyTokens(connection.database, InMemoryLogger())
        }
    }

    override fun overrideSharedConfiguration(sharedConfigurationFacade: SharedConfigurationFacade) {
        val result = sharedConfigurationFacade.loadSharedSettingsFromString(ProductionShapeDataset.sharedDocument)
        assertThat(result.isOk)
            .describedAs("loading the production shape of the maven domain: ${if (result.isErr) result.error else "ok"}")
            .isTrue
    }

    @Test
    fun `loads the group with its empty mirrored entry kept in settings and skipped for the mirror hosts`() {
        // when: the settings in use are read back
        val group = sharedConfigurationFacade().getDomainSettings<MavenSettings>().get()
            .repositories.single { it.id == "onelitefeather" }

        // then: the settings keep the entry as written, between the loopback references
        assertThat(group.proxied.map { it.reference })
            .describedAs("proxied references of the group as written")
            .containsExactly("onelitefeather-releases", "", "onelitefeather-snapshots", "onelitefeather-proxy")

        // and: the group repository exists with three mirror hosts, the empty entry producing none
        val repository = mavenFacade.getRepository("onelitefeather")
        assertThat(repository).describedAs("group repository").isNotNull
        assertThat(repository!!.mirrorHosts.map { it.host })
            .describedAs("mirror hosts of the group")
            .containsExactly("onelitefeather-releases", "onelitefeather-snapshots", "onelitefeather-proxy")
    }

    @Test
    fun `serves a group artifact through its first loopback mirror to a reader`() {
        // given: an artifact in the first loopback mirror of the group
        useDocument("onelitefeather-releases", "com/example/a/1.0", "a-1.0.jar", content = "group-content", store = true)

        // when: a reader asks the group for it
        val (name, secret) = ProductionShapeDataset.READER_NAME to ProductionShapeDataset.secrets.getValue(ProductionShapeDataset.READER_NAME)
        val response = get("$base/onelitefeather/com/example/a/1.0/a-1.0.jar")
            .basicAuth(name, secret)
            .asString()

        // then: the group answers with the artifact, so the empty entry did not break the request
        assertThat(response.status).describedAs("status of the group request").isEqualTo(200)
        assertThat(response.body).describedAs("body served by the group").isEqualTo("group-content")
    }

    private fun sharedConfigurationFacade(): SharedConfigurationFacade =
        reposilite.extensions.facade()

}
