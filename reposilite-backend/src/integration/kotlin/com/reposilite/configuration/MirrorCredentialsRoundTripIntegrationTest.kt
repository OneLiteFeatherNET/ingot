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

import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.reposilite.RecommendedLocalSpecificationJunitExtension
import com.reposilite.ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER
import com.reposilite.configuration.local.LocalConfiguration
import com.reposilite.configuration.shared.SharedConfigurationFacade
import com.reposilite.configuration.specification.SettingsIntegrationSpecification
import com.reposilite.maven.RepositoryType
import com.reposilite.maven.application.MavenSettings
import com.reposilite.maven.application.MirrorCredentials
import com.reposilite.maven.application.MirroredRepositorySettings
import com.reposilite.maven.application.RepositorySettings
import com.reposilite.shared.http.AuthenticationMethod.LOOPBACK_LINK
import io.javalin.http.HttpStatus.BAD_REQUEST
import io.javalin.http.HttpStatus.OK
import kong.unirest.core.HttpResponse
import kong.unirest.core.Unirest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import panda.std.reactive.ReferenceUtils

/**
 * The mirror credentials of the maven domain survive the dashboard: a read followed by a write keeps them, whether the
 * secrets are hidden from the read or not. The concrete classes choose the setting, the tests are the same for both.
 */
internal abstract class MirrorCredentialsRoundTripSpecification : SettingsIntegrationSpecification() {

    private val placeholder = "********"

    private fun readDomain(): ObjectNode {
        // given: a permitted token
        val (name, secret) = useDefaultManagementToken()

        // when: the maven domain is requested as the dashboard does
        val response = Unirest.get("$base/api/settings/domain/maven")
            .basicAuth(name, secret)
            .asString()

        // then: the request succeeded and the body is an object
        assertSuccessResponse(OK, response)
        return DEFAULT_OBJECT_MAPPER.readTree(response.body) as ObjectNode
    }

    private fun writeDomain(document: ObjectNode): HttpResponse<*> {
        // given: a permitted token
        val (name, secret) = useDefaultManagementToken()

        // when: the maven domain is written back as the dashboard does
        return Unirest.put("$base/api/settings/domain/maven")
            .basicAuth(name, secret)
            .body(document.toString())
            .asEmpty()
    }

    /** The repositories of the instance: a group `onelitefeather` lists two local members that carry loopback credentials, and an external mirror. */
    private fun useRepositoriesWithCredentials() {
        settingsFacade.updateSharedSettings(
            "maven",
            MavenSettings(
                repositories = listOf(
                    RepositorySettings(id = "releases"),
                    RepositorySettings(id = "snapshots"),
                    RepositorySettings(
                        id = "onelitefeather",
                        proxied = listOf(
                            loopbackMember("releases", "releases-secret"),
                            loopbackMember("snapshots", "snapshots-secret"),
                        )
                    ),
                    RepositorySettings(
                        id = "external",
                        proxied = listOf(
                            MirroredRepositorySettings(
                                reference = "https://mirror.example.test/maven",
                                authorization = MirrorCredentials(login = "upstream", password = "upstream-secret")
                            )
                        )
                    ),
                )
            )
        )
    }

    private fun loopbackMember(id: String, secret: String) = MirroredRepositorySettings(
        reference = id,
        authorization = MirrorCredentials(method = LOOPBACK_LINK, login = "reposilite-mirror", password = secret)
    )

    private val settingsFacade
        get() = useFacade<SharedConfigurationFacade>()

    private fun ObjectNode.repositoryById(id: String): ObjectNode =
        (get("repositories") as ArrayNode).single { it.get("id").asText() == id } as ObjectNode

    private fun ObjectNode.proxiedOf(id: String): ArrayNode =
        repositoryById(id).get("proxied") as ArrayNode

    /** The stored mirror entries, keyed by repository and reference, with the credential of each as the stored JSON writes it. */
    private fun storedCredentials(): Map<String, String> =
        settingsFacade.getDomainSettings<MavenSettings>().get().repositories
            .flatMap { repository ->
                repository.proxied.map { entry ->
                    "${repository.id}/${entry.reference}" to DEFAULT_OBJECT_MAPPER.writeValueAsString(entry.authorization)
                }
            }
            .toMap()

    @Test
    fun `should keep every mirror credential byte for byte when the domain is written back unchanged`() {
        // given: stored credentials, read by the dashboard
        useRepositoriesWithCredentials()
        val before = storedCredentials()
        val domain = readDomain()

        // when: the unchanged document is written back
        val response = writeDomain(domain)

        // then: the update succeeded and every credential is the one stored before
        assertSuccessResponse(OK, response)
        assertThat(storedCredentials())
            .describedAs("stored mirror credentials after a round trip")
            .isEqualTo(before)
    }

    @Test
    fun `should keep the credentials of the members that stay when an untyped group becomes a group`() {
        // given: an untyped group with two credentialed members, read by the dashboard
        useRepositoriesWithCredentials()
        val before = storedCredentials()
        val domain = readDomain()

        // when: the group is switched to type GROUP, and written back with its members as they were read
        domain.repositoryById("onelitefeather").put("type", RepositoryType.GROUP.name)
        val response = writeDomain(domain)

        // then: the group is typed and the credentials of both members are the ones stored before
        assertSuccessResponse(OK, response)
        assertThat(settingsFacade.getDomainSettings<MavenSettings>().get().repositories.single { it.id == "onelitefeather" }.type)
            .describedAs("type of the group")
            .isEqualTo(RepositoryType.GROUP)
        assertThat(storedCredentials())
            .describedAs("stored mirror credentials after the switch")
            .isEqualTo(before)
    }

    @Test
    fun `should keep the stored credential of a member that is sent back without one`() {
        // given: an untyped group whose members are sent back without their authorization, as an older dashboard did
        useRepositoriesWithCredentials()
        val before = storedCredentials()
        val domain = readDomain()
        domain.repositoryById("onelitefeather").put("type", RepositoryType.GROUP.name)
        domain.proxiedOf("onelitefeather").forEach { (it as ObjectNode).remove("authorization") }

        // when: the document is written back
        val response = writeDomain(domain)

        // then: the members keep the credentials they had, because an absent secret is not a removal
        assertSuccessResponse(OK, response)
        assertThat(storedCredentials())
            .describedAs("stored mirror credentials after a write without them")
            .isEqualTo(before)
    }

    @Test
    fun `should remove only the member that was taken out, and keep the credentials of the others`() {
        // given: a group whose second member is taken out by the dashboard
        useRepositoriesWithCredentials()
        val before = storedCredentials()
        val domain = readDomain()
        domain.proxiedOf("onelitefeather").remove(1)

        // when: the document is written back
        val response = writeDomain(domain)

        // then: only the taken out member is gone, and the first keeps its credential
        assertSuccessResponse(OK, response)
        assertThat(storedCredentials())
            .describedAs("stored mirror credentials after one member is taken out")
            .isEqualTo(before.filterKeys { it != "onelitefeather/snapshots" })
    }

    @Test
    fun `should clear the credential of a member only when null is sent for it`() {
        // given: a credentialed member that the document clears explicitly
        useRepositoriesWithCredentials()
        val domain = readDomain()
        domain.proxiedOf("onelitefeather").forEach { entry ->
            if ((entry as ObjectNode).get("reference").asText() == "releases") entry.putNull("authorization")
        }

        // when: the document is written back
        val response = writeDomain(domain)

        // then: the credential of that member is gone, and the other member keeps its own
        assertSuccessResponse(OK, response)
        assertThat(storedCredentials())
            .containsEntry("onelitefeather/releases", "null")
            .containsKey("onelitefeather/snapshots")
        assertThat(storedCredentials()["onelitefeather/snapshots"]).isNotEqualTo("null")
    }

    @Test
    fun `should refuse a write that would drop the credential of a repository listed twice, rather than guess its owner`() {
        // given: a group that lists the same repository twice, each entry with its own credential
        settingsFacade.updateSharedSettings(
            "maven",
            MavenSettings(
                repositories = listOf(
                    RepositorySettings(id = "releases"),
                    RepositorySettings(
                        id = "onelitefeather",
                        proxied = listOf(loopbackMember("releases", "first-secret"), loopbackMember("releases", "second-secret"))
                    ),
                )
            )
        )
        val before = storedCredentials()
        val domain = readDomain()
        domain.proxiedOf("onelitefeather").forEach { (it as ObjectNode).remove("authorization") }

        // when: the document is written back without the credentials
        val response = writeDomain(domain)

        // then: the write is refused and nothing is stored
        assertErrorResponse(BAD_REQUEST, response)
        assertThat(storedCredentials())
            .describedAs("stored mirror credentials after the refused write")
            .isEqualTo(before)
    }

    @Test
    fun `should show the password as a placeholder only when secrets are hidden`() {
        // given: the dashboard read of the domain
        useRepositoriesWithCredentials()
        val domain = readDomain()
        val masked = domain.proxiedOf("onelitefeather").first().get("authorization").get("password").asText()

        // then: the read shows the placeholder when secrets are hidden, and the stored value otherwise
        assertThat(masked)
            .describedAs("password in the read of the dashboard")
            .isEqualTo(if (hidesSecrets) placeholder else "releases-secret")
    }

    /** Whether the concrete class hides secrets from the settings API. */
    protected abstract val hidesSecrets: Boolean

}

@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class MirrorCredentialsHiddenSecretsIntegrationTest : MirrorCredentialsRoundTripSpecification() {

    override val hidesSecrets: Boolean = true

    override fun overrideLocalConfiguration(localConfiguration: LocalConfiguration) {
        ReferenceUtils.setValue(localConfiguration.hideSettingsSecrets, true)
    }

}

@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class MirrorCredentialsShownSecretsIntegrationTest : MirrorCredentialsRoundTripSpecification() {

    override val hidesSecrets: Boolean = false

}
