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
import com.reposilite.ReposiliteParameters
import com.reposilite.ReposiliteSpecification
import com.reposilite.configuration.shared.SharedConfigurationFacade
import com.reposilite.frontend.application.FrontendSettings
import io.javalin.http.HttpStatus
import kong.unirest.core.Unirest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * An instance that loads its shared settings from a mounted file with the reload switch on. The test rewrites the file and
 * drives the reload explicitly, so it waits for no poll and reads no clock.
 */
@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class SharedConfigurationReloadIntegrationTest : ReposiliteSpecification() {

    private val sharedConfiguration by lazy { reposiliteWorkingDirectory.resolve("custom-shared-configuration.json") }

    private fun document(frontendId: String): String =
        """
        {
            "frontend": {
                "id": "$frontendId"
            }
        }
        """.trimIndent()

    override fun overrideParameters(parameters: ReposiliteParameters) {
        // given: a mounted shared configuration with the reload switch on
        parameters.sharedConfigurationPath = sharedConfiguration.toPath()
        parameters.sharedConfigurationReload = true
        sharedConfiguration.writeText(document("initial-repository"))
    }

    @Test
    fun `an edited shared configuration file is applied by the next reload without a restart`() {
        // given: the file is edited after the instance has started
        val sharedConfigurationFacade = reposilite.extensions.facade<SharedConfigurationFacade>()
        sharedConfiguration.writeText(document("edited-repository"))

        // when: the reload runs
        sharedConfigurationFacade.reloadIfChanged()

        // then: the running instance serves the edited value
        assertThat(sharedConfigurationFacade.getDomainSettings<FrontendSettings>().get().id)
            .describedAs("frontend id after the edit of the mounted file")
            .isEqualTo("edited-repository")
    }

    @Test
    fun `a settings save over the API is refused with a conflict and the mounted file is unchanged`() {
        // given: a permitted token and the mounted file as it was written
        val (name, secret) = useDefaultManagementToken()
        val before = sharedConfiguration.readText()

        // when: the frontend domain is saved over the API
        val response = Unirest.put("$base/api/settings/domain/frontend")
            .basicAuth(name, secret)
            .header("Content-Type", "application/json")
            .body("""{"id":"saved-from-dashboard"}""")
            .asString()

        // then: the save is refused as a conflict and the file is byte for byte the same
        assertThat(response.status)
            .describedAs("status of a save to a domain managed by the file")
            .isEqualTo(HttpStatus.CONFLICT.code)
        assertThat(sharedConfiguration.readText())
            .describedAs("file after a refused save")
            .isEqualTo(before)
    }
}
