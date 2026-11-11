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

import com.reposilite.RecommendedLocalSpecificationJunitExtension
import com.reposilite.ReposiliteSpecification
import com.reposilite.configuration.shared.SharedConfigurationFacade
import com.reposilite.maven.application.MavenSettings
import com.reposilite.maven.application.RepositorySettings
import kong.unirest.core.Unirest
import net.onelitefeather.ingot.readonly.ReadOnlySetting
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * An instance that is forced read-only, with a repository that also carries its own read-only flag. The instance-wide
 * refusal answers first, so both refusals stay distinct: 503 for the whole instance, 405 for one repository.
 */
@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class RepositoryPolicyGlobalReadOnlyIntegrationTest : ReposiliteSpecification() {

    override fun readOnlySetting(): ReadOnlySetting =
        ReadOnlySetting(forced = true)

    override fun overrideSharedConfiguration(sharedConfigurationFacade: SharedConfigurationFacade) {
        sharedConfigurationFacade.getDomainSettings<MavenSettings>().update { settings ->
            settings.copy(repositories = settings.repositories + RepositorySettings(id = "policy-frozen", readOnly = true))
        }
    }

    @Test
    fun `should answer 503 before the repository flag answers 405 to a deploy`() {
        // given: a manager
        val (name, secret) = useDefaultManagementToken()

        // when: a file is uploaded into the read-only repository while the whole instance is read-only
        val response = Unirest.put("$base/policy-frozen/com/example/app/1.0/app-1.0.jar")
            .basicAuth(name, secret)
            .body("content")
            .asEmpty()

        // then: the instance-wide refusal answers, with a retry hint
        assertThat(response.status).isEqualTo(503)
        assertThat(response.headers.getFirst("Retry-After")).isNotBlank()
    }

}
