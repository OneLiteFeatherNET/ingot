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
import com.reposilite.ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER
import com.reposilite.ReposiliteParameters
import com.reposilite.RecommendedLocalSpecificationJunitExtension
import com.reposilite.configuration.shared.SharedConfigurationFacade
import com.reposilite.configuration.specification.SettingsIntegrationSpecification
import io.javalin.http.HttpStatus.OK
import io.javalin.http.HttpStatus.UNAUTHORIZED
import kong.unirest.core.Unirest
import net.onelitefeather.ingot.settings.encryption.SettingsKeySources
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.Base64

/**
 * With a settings key configured and the default `hideSettingsSecrets` (off), the encryption state is readable by the settings area
 * only, the dashboard read hides every secret, and a placeholder sent back keeps the stored value. The plain read keeps returning the
 * value, as scripts expect. The key is fixed, so every run starts the same.
 */
@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class SettingsEncryptionStateIntegrationTest : SettingsIntegrationSpecification() {

    private val secret = "state-integration-secret"
    private val key = Base64.getEncoder().encodeToString(ByteArray(32) { (it * 3 + 1).toByte() })

    override fun overrideParameters(parameters: ReposiliteParameters) {
        parameters.settingsSecretEnvironment = mapOf(SettingsKeySources.KEY to key)
    }

    private val settingsFacade: SharedConfigurationFacade
        get() = useFacade()

    private fun authenticationWithSecret(): ObjectNode {
        val (name, token) = useDefaultManagementToken()
        val current = DEFAULT_OBJECT_MAPPER.readTree(
            Unirest.get("$base/api/settings/domain/authentication?secrets=hidden").basicAuth(name, token).asString().body
        ) as ObjectNode
        return current.deepCopy().apply { (get("ldap") as ObjectNode).put("searchUserPassword", secret) }
    }

    @Test
    fun `the encryption state reports the key and no plain secret to the settings area`() {
        // given: a stored secret
        val (name, token) = useDefaultManagementToken()
        Unirest.put("$base/api/settings/domain/authentication").basicAuth(name, token).body(authenticationWithSecret().toString()).asEmpty()

        // when: the state is requested with a token of the settings area
        val response = Unirest.get("$base/api/settings/encryption").basicAuth(name, token).asString()

        // then
        assertSuccessResponse(OK, response)
        val state = DEFAULT_OBJECT_MAPPER.readTree(response.body)
        assertThat(state.get("secretsEncrypted").asBoolean()).describedAs("secretsEncrypted").isTrue()
        assertThat(state.get("plaintextSecrets").asInt()).describedAs("plaintextSecrets").isZero()
        assertThat(state.get("mode").asText()).describedAs("mode").isEqualTo("optional")
        assertThat(response.body).describedAs("state body").doesNotContain(secret).doesNotContain(key)
    }

    @Test
    fun `the encryption state is refused without credentials`() {
        // when
        val response = Unirest.get("$base/api/settings/encryption").asString()

        // then
        assertThat(response.status).describedAs("status without credentials").isEqualTo(UNAUTHORIZED.code)
    }

    @Test
    fun `a dashboard read hides the secret while the plain read returns it`() {
        // given: a stored secret
        val (name, token) = useDefaultManagementToken()
        Unirest.put("$base/api/settings/domain/authentication").basicAuth(name, token).body(authenticationWithSecret().toString()).asEmpty()

        // when
        val hidden = Unirest.get("$base/api/settings/domain/authentication?secrets=hidden").basicAuth(name, token).asString()
        val plain = Unirest.get("$base/api/settings/domain/authentication").basicAuth(name, token).asString()

        // then
        assertThat(hidden.body).describedAs("hidden read").doesNotContain(secret).contains("********")
        assertThat(plain.body).describedAs("plain read").contains(secret)
    }

    @Test
    fun `a placeholder sent back keeps the stored value while hideSettingsSecrets is off`() {
        // given: a stored secret, read as the dashboard reads it
        val (name, token) = useDefaultManagementToken()
        Unirest.put("$base/api/settings/domain/authentication").basicAuth(name, token).body(authenticationWithSecret().toString()).asEmpty()
        val document = DEFAULT_OBJECT_MAPPER.readTree(
            Unirest.get("$base/api/settings/domain/authentication?secrets=hidden").basicAuth(name, token).asString().body
        )

        // when: the placeholder is written back unchanged
        val written = Unirest.put("$base/api/settings/domain/authentication").basicAuth(name, token).body(document.toString()).asEmpty()

        // then: the write succeeds and the stored value is still the secret
        assertThat(written.status).describedAs("write status").isEqualTo(OK.code)
        val plain = Unirest.get("$base/api/settings/domain/authentication").basicAuth(name, token).asString().body
        assertThat(plain).describedAs("stored value after the write").contains(secret)
        assertThat(settingsFacade.plaintextSecretCount()).describedAs("plain secrets in the stored document").isZero()
    }

}
