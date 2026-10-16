/*
 * Copyright (c) 2023 dzikoysk
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

import net.onelitefeather.ingot.identity.login.LoginSettings
import com.reposilite.RecommendedLocalSpecificationJunitExtension
import com.reposilite.RecommendedRemoteSpecificationJunitExtension
import com.reposilite.ReposiliteObjectMapper
import com.reposilite.audit.application.AuditSettings
import com.reposilite.auth.application.AuthenticationSettings
import com.reposilite.auth.application.LdapSettings
import com.reposilite.configuration.shared.SharedConfigurationFacade
import com.reposilite.configuration.specification.SettingsIntegrationSpecification
import com.reposilite.frontend.application.FrontendSettings
import com.reposilite.javadocs.application.JavadocSettings
import com.reposilite.maven.application.MavenSettings
import com.reposilite.cleanup.application.CleanupSettings
import com.reposilite.shared.ErrorResponse
import com.reposilite.statistics.api.ResolvedRequestsInterval.YEARLY
import com.reposilite.statistics.application.StatisticsSettings
import com.reposilite.web.application.WebSettings
import io.javalin.http.HttpStatus.FORBIDDEN
import io.javalin.http.HttpStatus.OK
import io.javalin.http.HttpStatus.PRECONDITION_FAILED
import kong.unirest.core.Unirest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class LocalSettingsIntegrationTest : SettingsIntegrationTest()

@ExtendWith(RecommendedRemoteSpecificationJunitExtension::class)
internal class RemoteSettingsIntegrationTest : SettingsIntegrationTest()

internal abstract class SettingsIntegrationTest : SettingsIntegrationSpecification() {

    companion object {
        private val DEFAULT_DOMAINS = mapOf(
            "web" to WebSettings::class,
            "authentication" to AuthenticationSettings::class,
            "statistics" to StatisticsSettings::class,
            "frontend" to FrontendSettings::class,
            "javadoc" to JavadocSettings::class,
            "maven" to MavenSettings::class,
            "cleanup" to CleanupSettings::class,
            "audit" to AuditSettings::class,
            "login" to LoginSettings::class
        )
    }

    @Test
    fun `should list all built-in config domains`() {
        // then: endpoint requires management access token
        assertManagerOnlyGetEndpoint("/api/settings/domains")

        // given: a permitted token
        val (permittedName, permittedSecret) = useDefaultManagementToken()

        // when: list of domains is requested
        val response = Unirest.get("$base/api/settings/domains")
            .basicAuth(permittedName, permittedSecret)
            .asJacksonObject(Array<String>::class)

        // then: response contains list of all of them
        assertSuccessResponse(OK, response) { domains ->
            assertThat(domains.toSet()).isEqualTo(DEFAULT_DOMAINS.keys)
        }
    }

    @Test
    fun `should offer schemes for all domains`() {
        DEFAULT_DOMAINS.forEach { (domain, _) ->
            // then: endpoint requires management access token
            assertManagerOnlyGetEndpoint("/api/settings/schema/$domain")

            // given: a permitted token
            val (permittedName, permittedSecret) = useDefaultManagementToken()

            // when: domain schema is requested
            val response = Unirest.get("$base/api/settings/schema/$domain")
                .basicAuth(permittedName, permittedSecret)
                .asString()

            // then: response contains list of all
            assertSuccessResponse(OK, response) {
                val schema = ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER.readTree(it)
                assertThat(schema.has("title")).isTrue
                assertThat(schema.has("description")).isTrue
            }
        }
    }

    @Test
    fun `should return settings entity for a domain`() {
        DEFAULT_DOMAINS.forEach { (domain, type) ->
            // then: endpoint requires management access token
            assertManagerOnlyGetEndpoint("/api/settings/domain/$domain")

            // given: a permitted token
            val (permittedName, permittedSecret) = useDefaultManagementToken()

            // when: settings entity is requested
            val response = Unirest.get("$base/api/settings/domain/$domain")
                .basicAuth(permittedName, permittedSecret)
                .asJacksonObject(type)

            assertSuccessResponse(OK, response)
        }
    }

    private val customSettings = mapOf(
        "web" to WebSettings(forwardedIp = "test"),
        "authentication" to AuthenticationSettings(ldap = LdapSettings(enabled = true)),
        "statistics" to StatisticsSettings(resolvedRequestsInterval = YEARLY),
        "frontend" to FrontendSettings(id = "test"),
        "javadoc" to JavadocSettings(enabled = false),
        "maven" to MavenSettings(repositories = emptyList())
    )

    @Test
    fun `should update settings`() {
        // given: an existing token without management permission
        val (unauthorizedToken, unauthorizedSecret) = useAuth("unauthorized-token", "secret")

        // when: configuration is requested without valid access token
        val unauthorizedResponse = Unirest.put("$base/api/settings/domain/")
            .basicAuth(unauthorizedToken, unauthorizedSecret)
            .asJacksonObject(ErrorResponse::class)

        // then: request is rejected
        assertErrorResponse(FORBIDDEN, unauthorizedResponse)

        // given: a permitted token
        val (permittedName, permittedSecret) = useDefaultManagementToken()

        customSettings.forEach { (domain, configuration) ->
            // when: configuration is updated with valid token
            val updateResponse = Unirest.put("$base/api/settings/domain/$domain")
                .basicAuth(permittedName, permittedSecret)
                .body(configuration)
                .asEmpty()

            // then: configuration update request succeeded
            assertSuccessResponse(OK, updateResponse)

            // when: settings entity is requested
            val response = Unirest.get("$base/api/settings/domain/$domain")
                .basicAuth(permittedName, permittedSecret)
                .asJacksonObject(configuration::class)

            // then: configuration updated succeeded
            assertThat(response.body).isEqualTo(configuration)
        }
    }

    @Test
    fun `should return stored secrets unchanged when hiding is not enabled`() {
        // given: a stored LDAP search password and a permitted token
        useFacade<SharedConfigurationFacade>()
            .updateSharedSettings("authentication", AuthenticationSettings(ldap = LdapSettings(searchUserPassword = "ldap-secret")))
        val (permittedName, permittedSecret) = useDefaultManagementToken()

        // when: the domain is requested with the default local configuration
        val response = Unirest.get("$base/api/settings/domain/authentication")
            .basicAuth(permittedName, permittedSecret)
            .asJacksonObject(AuthenticationSettings::class)

        // then: the stored value is returned as it always was
        assertSuccessResponse(OK, response) {
            assertThat(it.ldap.searchUserPassword).isEqualTo("ldap-secret")
        }
    }

    private fun currentEntityTag(domain: String): String {
        val (name, secret) = useDefaultManagementToken()
        return Unirest.get("$base/api/settings/domain/$domain")
            .basicAuth(name, secret)
            .asString()
            .headers
            .getFirst("ETag")
    }

    @Test
    fun `should return an entity tag with the settings domain`() {
        // when: a settings domain is requested
        val etag = currentEntityTag("web")

        // then: the response carries a strong entity tag
        assertThat(etag).matches("\"[0-9a-f]{64}\"")
    }

    @Test
    fun `should accept a write that carries the current entity tag and return the new one`() {
        // given: the entity tag of the current state
        val (name, secret) = useDefaultManagementToken()
        val current = currentEntityTag("web")

        // when: the domain is written with that tag
        val updateResponse = Unirest.put("$base/api/settings/domain/web")
            .basicAuth(name, secret)
            .header("If-Match", current)
            .body(WebSettings(forwardedIp = "conditional"))
            .asEmpty()

        // then: the write is applied and the response carries the tag of the stored state
        assertSuccessResponse(OK, updateResponse)
        val newTag = updateResponse.headers.getFirst("ETag")
        assertThat(newTag).isNotEqualTo(current)
        assertThat(currentEntityTag("web")).isEqualTo(newTag)
    }

    @Test
    fun `should reject a write with a stale entity tag and name the domain`() {
        // given: an entity tag read before another admin saves the domain
        val (name, secret) = useDefaultManagementToken()
        val stale = currentEntityTag("web")
        val otherAdminResponse = Unirest.put("$base/api/settings/domain/web")
            .basicAuth(name, secret)
            .body(WebSettings(forwardedIp = "other-admin"))
            .asEmpty()
        assertSuccessResponse(OK, otherAdminResponse)

        // when: the stale write arrives
        val response = Unirest.put("$base/api/settings/domain/web")
            .basicAuth(name, secret)
            .header("If-Match", stale)
            .body(WebSettings(forwardedIp = "overwrite"))
            .asJacksonObject(ErrorResponse::class)

        // then: it is rejected with 412 and the error names the domain
        assertErrorResponse(PRECONDITION_FAILED, response)
        assertThat(response.body.message).contains("web")

        // then: the other admin's change is still stored
        val stored = Unirest.get("$base/api/settings/domain/web")
            .basicAuth(name, secret)
            .asJacksonObject(WebSettings::class)
        assertSuccessResponse(OK, stored) { assertThat(it.forwardedIp).isEqualTo("other-admin") }
    }

    @Test
    fun `should expose the entity tag header to cross-origin clients`() {
        // given: a cross-origin dashboard served from another origin
        val (name, secret) = useDefaultManagementToken()

        // when: a domain is requested with an origin header
        val response = Unirest.get("$base/api/settings/domain/web")
            .basicAuth(name, secret)
            .header("Origin", "https://dashboard.example.org")
            .asString()

        // then: the browser is allowed to read the ETag header
        assertSuccessResponse(OK, response)
        assertThat(response.headers.getFirst("Access-Control-Expose-Headers")).containsIgnoringCase("ETag")
    }

    @Test
    fun `should allow if-match in cross-origin preflight requests`() {
        // when: a browser asks whether a cross-origin PUT with If-Match is allowed
        val preflight = Unirest.options("$base/api/settings/domain/web")
            .header("Origin", "https://dashboard.example.org")
            .header("Access-Control-Request-Method", "PUT")
            .header("Access-Control-Request-Headers", "authorization,content-type,if-match")
            .asEmpty()

        // then: the If-Match header is allowed
        assertThat(preflight.headers.getFirst("Access-Control-Allow-Headers")).containsIgnoringCase("if-match")
    }

    @Test
    fun `should report that secrets are visible on the frontend settings by default`() {
        // when: the public dashboard settings are requested
        val response = Unirest.get("$base/api/frontend/settings").asString()

        // then: the flag is present and false, so existing dashboards keep their behaviour
        assertSuccessResponse(OK, response)
        assertThat(ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER.readTree(response.body).get("secretsHidden").asBoolean())
            .describedAs("secretsHidden flag")
            .isFalse
    }

    @Test
    fun `should reset configuration to default values`() {
        // given: a permitted token and a facade with custom configuration
        val (permittedName, permittedSecret) = useDefaultManagementToken()

        val settingsFacade = useFacade<SharedConfigurationFacade>()
        customSettings.forEach { (domain, configuration) -> settingsFacade.updateSharedSettings(domain, configuration) }

        customSettings.forEach { (domain, configuration) ->
            // when: empty configuration is set
            val updateResponse = Unirest.put("$base/api/settings/domain/$domain")
                .basicAuth(permittedName, permittedSecret)
                .body("{}")
                .asEmpty()

            // then: update request succeeded
            assertSuccessResponse(OK, updateResponse)

            // when: settings updated settings entity is requested
            val response = Unirest.get("$base/api/settings/domain/$domain")
                .basicAuth(permittedName, permittedSecret)
                .asJacksonObject(configuration::class)

            // then: response is equal to the default configuration instance
            assertThat(response.body).isEqualTo(configuration.javaClass.getConstructor().newInstance())
        }
    }

}