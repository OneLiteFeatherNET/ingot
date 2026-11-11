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

package com.reposilite.configuration

import com.fasterxml.jackson.databind.node.ObjectNode
import com.reposilite.RecommendedLocalSpecificationJunitExtension
import com.reposilite.ReposiliteParameters
import com.reposilite.ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER
import com.reposilite.configuration.shared.SharedConfigurationFacade
import com.reposilite.configuration.specification.SettingsIntegrationSpecification
import io.javalin.http.HttpStatus.BAD_REQUEST
import io.javalin.http.HttpStatus.OK
import kong.unirest.core.Unirest
import net.onelitefeather.ingot.settings.encryption.SettingsKeySources
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.Base64

/**
 * With a settings key configured, a stored secret is sealed in the stored document and never returned by the settings API in a
 * hidden read or in an export. The export opens with its password and nothing else. Every instance of this class starts with the same
 * fixed key, so the fixture is repeatable.
 */
@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class EncryptSettingsAtRestIntegrationTest : SettingsIntegrationSpecification() {

    private val secret = "integration-search-secret"
    private val password = "export-password-12"
    private val key = Base64.getEncoder().encodeToString(ByteArray(32) { (it * 7 + 3).toByte() })

    override fun overrideParameters(parameters: ReposiliteParameters) {
        parameters.settingsSecretEnvironment = mapOf(SettingsKeySources.KEY to key)
    }

    private val settingsFacade: SharedConfigurationFacade
        get() = useFacade()

    private fun authenticationWithSecret(): ObjectNode {
        // given: the stored authentication domain, with the search user password replaced
        val (name, token) = useDefaultManagementToken()
        val current = DEFAULT_OBJECT_MAPPER.readTree(
            Unirest.get("$base/api/settings/domain/authentication?secrets=hidden")
                .basicAuth(name, token)
                .asString()
                .body
        ) as ObjectNode
        // The search user password belongs to the LDAP block of the authentication domain.
        return current.deepCopy().apply { (get("ldap") as ObjectNode).put("searchUserPassword", secret) }
    }

    @Test
    fun `a stored secret is sealed in the stored document and hidden from the dashboard read`() {
        // given: a secret written through the API
        val (name, token) = useDefaultManagementToken()
        val written = Unirest.put("$base/api/settings/domain/authentication")
            .basicAuth(name, token)
            .body(authenticationWithSecret().toString())
            .asEmpty()
        assertThat(written.status).describedAs("write status").isEqualTo(OK.code)

        // when: the dashboard reads the domain with the hidden form, and the stored document is read
        val hidden = Unirest.get("$base/api/settings/domain/authentication?secrets=hidden")
            .basicAuth(name, token)
            .asString()
        val stored = settingsFacade.fetchConfiguration()

        // then: the response holds the placeholder and the stored document holds an envelope and no plain value
        assertThat(hidden.body).describedAs("hidden read").doesNotContain(secret).contains("********")
        assertThat(stored).describedAs("stored document").doesNotContain(secret).contains("enc:v1:")
        assertThat(settingsFacade.plaintextSecretCount()).describedAs("plain secrets in the stored document").isZero()
    }

    @Test
    fun `an export opens with its password to the saved value and not with another password`() {
        // given: the secret is stored
        val (name, token) = useDefaultManagementToken()
        Unirest.put("$base/api/settings/domain/authentication")
            .basicAuth(name, token)
            .body(authenticationWithSecret().toString())
            .asEmpty()

        // when: an export is made with the password
        val exported = Unirest.post("$base/api/settings/export")
            .basicAuth(name, token)
            .header("Content-Type", "application/json")
            .body("""{"password":"$password"}""")
            .asString()
        assertSuccessResponse(OK, exported)
        val file = DEFAULT_OBJECT_MAPPER.readTree(exported.body) as ObjectNode

        // then: the file holds no plain value, and it opens with the password only
        assertThat(exported.body).describedAs("export file").doesNotContain(secret)

        val wrong = Unirest.post("$base/api/settings/import/open")
            .basicAuth(name, token)
            .header("Content-Type", "application/json")
            .body(DEFAULT_OBJECT_MAPPER.createObjectNode().put("password", "another-password-1").set<ObjectNode>("export", file).toString())
            .asString()
        assertThat(wrong.status).describedAs("wrong password status").isEqualTo(BAD_REQUEST.code)

        val right = Unirest.post("$base/api/settings/import/open")
            .basicAuth(name, token)
            .header("Content-Type", "application/json")
            .body(DEFAULT_OBJECT_MAPPER.createObjectNode().put("password", password).set<ObjectNode>("export", file).toString())
            .asString()
        assertSuccessResponse(OK, right)
        assertThat(DEFAULT_OBJECT_MAPPER.readTree(right.body).at("/authentication/ldap/searchUserPassword").asText())
            .describedAs("opened search user password")
            .isEqualTo(secret)
    }

}
