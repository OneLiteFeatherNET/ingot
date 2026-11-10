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

import com.reposilite.RecommendedLocalSpecificationJunitExtension
import com.reposilite.RecommendedRemoteSpecificationJunitExtension
import com.reposilite.ReposiliteParameters
import com.reposilite.ReposiliteSpecification
import com.reposilite.configuration.shared.SettingsSecretReferences
import com.reposilite.configuration.shared.SharedConfigurationFacade
import com.reposilite.maven.application.MavenSettings
import com.reposilite.maven.application.MirrorCredentials
import io.javalin.Javalin
import kong.unirest.core.Unirest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.Base64
import java.util.concurrent.ConcurrentLinkedQueue

@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class LocalSettingsSecretReferenceIntegrationTest : SettingsSecretReferenceIntegrationTest()

@ExtendWith(RecommendedRemoteSpecificationJunitExtension::class)
internal class RemoteSettingsSecretReferenceIntegrationTest : SettingsSecretReferenceIntegrationTest()

/**
 * A mirror password that is stored as a reference reaches the upstream as its resolved value, while the stored document keeps the
 * reference. The instance is started with the switch on and a variable that is listed for it, through the environment the test
 * injects, so no variable of the test process is changed.
 */
internal abstract class SettingsSecretReferenceIntegrationTest : ReposiliteSpecification() {

    private val mirrorPassword = "mirror-pass-value"
    private val gav = "com/reposilite/remote.jar"
    private val content = "remote-content"

    override fun overrideParameters(parameters: ReposiliteParameters) {
        parameters.settingsSecretEnvironment = mapOf(
            SettingsSecretReferences.SWITCH to "true",
            SettingsSecretReferences.ENVIRONMENT_LIST to "MIRROR_PASSWORD",
            "MIRROR_PASSWORD" to mirrorPassword,
        )
    }

    @Test
    fun `should send the mirror password resolved from a listed variable while the document keeps the reference`() {
        // given: an upstream that records the Authorization header of every request for the artifact
        val authorizations = ConcurrentLinkedQueue<String>()
        val upstream = Javalin.create { config ->
            config.routes.head("/releases/$gav") { ctx -> ctx.result(content) }
            config.routes.get("/releases/$gav") { ctx ->
                authorizations.add(ctx.header("Authorization").orEmpty())
                ctx.result(content)
            }
        }.start(0)

        try {
            // given: the proxied repository of the instance points at the upstream with the password as a reference
            val settings = useFacade<SharedConfigurationFacade>()
            val stored = settings.getDomainSnapshot<MavenSettings>("maven")!!.settings
            val updated = stored.copy(
                repositories = stored.repositories.map { repository ->
                    if (repository.id != "proxied") repository
                    else repository.copy(
                        proxied = repository.proxied.map { mirror ->
                            mirror.copy(
                                reference = upstreamReference(upstream.port()),
                                authorization = MirrorCredentials(login = "upstream", password = "\${env:MIRROR_PASSWORD}"),
                            )
                        }
                    )
                }
            )
            assertThat(settings.updateSharedSettings("maven", updated)!!.isOk)
                .describedAs("write of the mirror with a reference")
                .isTrue()

            // when: the artifact is requested through the mirror
            val response = Unirest.get("$base/proxied/$gav").asString()

            // then: the artifact is served, the upstream received the resolved credential, and the document keeps the reference
            assertThat(response.isSuccess).describedAs("mirror response").isTrue()
            assertThat(response.body).describedAs("mirror body").isEqualTo(content)
            assertThat(authorizations)
                .describedAs("authorization headers seen by the upstream")
                .containsExactly("Basic " + Base64.getEncoder().encodeToString("upstream:$mirrorPassword".toByteArray()))
            assertThat(settings.getDomainSnapshot<MavenSettings>("maven")!!.settings.repositories.single { it.id == "proxied" }.proxied.single().authorization?.password)
                .describedAs("stored password")
                .isEqualTo("\${env:MIRROR_PASSWORD}")
        } finally {
            upstream.stop()
        }
    }
}
