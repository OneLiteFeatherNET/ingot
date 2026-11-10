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

package net.onelitefeather.ingot.support

import com.reposilite.ReposiliteRunner
import com.reposilite.ReposiliteSpecification
import com.reposilite.RecommendedLocalSpecificationJunitExtension
import com.reposilite.audit.api.AuditPageDto
import com.reposilite.configuration.local.LocalConfiguration
import com.reposilite.configuration.shared.SharedConfigurationFacade
import com.reposilite.maven.application.MavenSettings
import com.reposilite.maven.application.MirrorCredentials
import com.reposilite.maven.application.MirroredRepositorySettings
import com.reposilite.maven.application.RepositorySettings
import kong.unirest.core.Unirest
import net.onelitefeather.ingot.readonly.ReadOnlySetting
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import panda.std.reactive.ReferenceUtils
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/**
 * The support routes of an instance with the console area in use. The manager token is an instance admin, so it holds every
 * area. Every test starts its own instance and reads only what it planted itself.
 */
@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class SupportBundleIntegrationTest : ReposiliteSpecification() {

    private val manager = ReposiliteRunner.DEFAULT_TOKEN

    private val plantedSecrets = listOf("planted-mirror-4472", "planted-tls-4473")

    override fun overrideLocalConfiguration(localConfiguration: LocalConfiguration) {
        ReferenceUtils.setValue(localConfiguration.keyPassword, "planted-tls-4473")
    }

    @Test
    fun `should answer 401 to the system information without credentials`() {
        val response = Unirest.get("$base/api/admin/support/system-info").asString()

        assertThat(response.status).`as`("status without credentials").isEqualTo(401)
    }

    @Test
    fun `should answer 401 to the bundle without credentials`() {
        val response = Unirest.get("$base/api/admin/support/bundle").asString()

        assertThat(response.status).`as`("status without credentials").isEqualTo(401)
    }

    @Test
    fun `should answer 403 to the bundle for a principal without the console area`() {
        // given: a token with no admin area
        val (name, secret) = useAuth("plain-reader", "plain-secret")

        // when: the bundle is requested
        val response = Unirest.get("$base/api/admin/support/bundle").basicAuth(name, secret).asString()

        // then: the request is refused before anything is read
        assertThat(response.status).`as`("status without the console area").isEqualTo(403)
    }

    @Test
    fun `should serve the system information to a manager with no-store`() {
        val response = Unirest.get("$base/api/admin/support/system-info")
            .basicAuth(manager.first, manager.second)
            .asString()

        assertThat(response.status).`as`("status of the page").isEqualTo(200)
        assertThat(response.headers.getFirst("Cache-Control")).`as`("cache control of the page").isEqualTo("no-store")
        assertThat(response.body).`as`("body of the page").contains("\"ingot\"", "\"database\"", "\"configuration\"")
    }

    @Test
    fun `should serve the bundle as a zip attachment with its files and no-store`() {
        val response = Unirest.get("$base/api/admin/support/bundle")
            .basicAuth(manager.first, manager.second)
            .asBytes()

        assertThat(response.status).`as`("status of the bundle").isEqualTo(200)
        assertThat(response.headers.getFirst("Content-Type")).`as`("content type").startsWith("application/zip")
        assertThat(response.headers.getFirst("Content-Disposition")).`as`("attachment name")
            .startsWith("attachment; filename=\"ingot-support-")
            .endsWith(".zip\"")
        assertThat(response.headers.getFirst("Cache-Control")).`as`("cache control of the bundle").isEqualTo("no-store")
        assertThat(unzip(response.body).keys).`as`("files of the bundle").contains(
            "manifest.json", "system-info.json", "configuration-local.json", "configuration-shared.json", "cluster.json", "plugins.json",
        )
    }

    @Test
    fun `should write one audit event for a download with the actor and none for the page`() {
        // given: the page is read first
        Unirest.get("$base/api/admin/support/system-info").basicAuth(manager.first, manager.second).asString()

        // when: the bundle is downloaded
        Unirest.get("$base/api/admin/support/bundle").basicAuth(manager.first, manager.second).asBytes()

        // then: exactly one download event names the manager
        val events = Unirest.get("$base/api/audit?action=SUPPORT_BUNDLE_DOWNLOADED")
            .basicAuth(manager.first, manager.second)
            .asJacksonObject(AuditPageDto::class)
            .body
            .events
        assertThat(events).`as`("download events").hasSize(1)
        assertThat(events.single().actor).`as`("actor of the download").isEqualTo(manager.first)
    }

    @Test
    fun `should keep the planted secrets out of the page and the bundle`() {
        // given: a mirror password in the shared settings, a TLS key password in the local configuration and a log line with both
        useFacade<SharedConfigurationFacade>().getDomainSettings<MavenSettings>().update { old ->
            old.copy(
                repositories = old.repositories + RepositorySettings(
                    id = "planted-mirror",
                    proxied = listOf(
                        MirroredRepositorySettings(
                            reference = upstreamReference(UNSTARTED_UPSTREAM_PORT),
                            authorization = MirrorCredentials(login = "planted-login", password = "planted-mirror-4472"),
                        )
                    ),
                )
            )
        }
        reposilite.journalist.logger.warn("upstream rejected planted-mirror-4472 with planted-tls-4473")

        // when: the page and the bundle are read
        val page = Unirest.get("$base/api/admin/support/system-info").basicAuth(manager.first, manager.second).asString().body
        val entries = unzip(
            Unirest.get("$base/api/admin/support/bundle").basicAuth(manager.first, manager.second).asBytes().body
        )

        // then: no planted secret is found in any entry or in the page
        plantedSecrets.forEach { secret ->
            assertThat(page).`as`("page holding '$secret'").doesNotContain(secret)
            assertThat(entries.filterValues { it.contains(secret) }.keys).`as`("entries holding '$secret'").isEmpty()
        }
        assertThat(entries.filterKeys { it.startsWith("logs/") }.values.single()).`as`("the scrubbed log line").contains("[redacted]")
    }

    @Test
    fun `should name no settings domain after a support address`() {
        // when: the names of the settings domains are listed
        val domains = useFacade<SharedConfigurationFacade>().getDomainNames()

        // then: the support addresses stay free, so a domain can never shadow them
        assertThat(domains).`as`("settings domains").doesNotContain("system-info", "support")
    }

    @Test
    fun `should offer the support pages while the opt-out is not set`() {
        val response = Unirest.get("$base/api/admin/support/availability")
            .basicAuth(manager.first, manager.second)
            .asString()

        assertThat(response.status).`as`("status of the availability").isEqualTo(200)
        assertThat(response.body).`as`("availability").contains("\"enabled\":true")
    }

    private fun unzip(bytes: ByteArray): Map<String, String> {
        val entries = linkedMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { input ->
            generateSequence { input.nextEntry }.forEach { entry ->
                entries[entry.name] = String(input.readBytes(), Charsets.UTF_8)
            }
        }
        return entries
    }

}

/** The read-only switch forces the instance into read-only mode. Both routes are reads, so the bundle is still served. */
@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class SupportBundleReadOnlyIntegrationTest : ReposiliteSpecification() {

    override fun readOnlySetting(): ReadOnlySetting =
        ReadOnlySetting(forced = true)

    @Test
    fun `should serve the bundle while the instance is read-only`() {
        val response = Unirest.get("$base/api/admin/support/bundle")
            .basicAuth(ReposiliteRunner.DEFAULT_TOKEN.first, ReposiliteRunner.DEFAULT_TOKEN.second)
            .asBytes()

        assertThat(response.status).`as`("status of the bundle while read-only").isEqualTo(200)
    }
}

/** The opt-out answers 404 on both data routes, and the availability route tells the dashboard to hide the pages. */
@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class SupportBundleDisabledIntegrationTest : ReposiliteSpecification() {

    override fun supportBundleSetting(): SupportBundleSetting =
        SupportBundleSetting(enabled = false)

    @Test
    fun `should answer 404 to the system information`() {
        val response = Unirest.get("$base/api/admin/support/system-info")
            .basicAuth(ReposiliteRunner.DEFAULT_TOKEN.first, ReposiliteRunner.DEFAULT_TOKEN.second)
            .asString()

        assertThat(response.status).`as`("status of the page with the opt-out").isEqualTo(404)
    }

    @Test
    fun `should answer 404 to the bundle`() {
        val response = Unirest.get("$base/api/admin/support/bundle")
            .basicAuth(ReposiliteRunner.DEFAULT_TOKEN.first, ReposiliteRunner.DEFAULT_TOKEN.second)
            .asBytes()

        assertThat(response.status).`as`("status of the bundle with the opt-out").isEqualTo(404)
    }

    @Test
    fun `should tell the dashboard that the pages are off`() {
        val response = Unirest.get("$base/api/admin/support/availability")
            .basicAuth(ReposiliteRunner.DEFAULT_TOKEN.first, ReposiliteRunner.DEFAULT_TOKEN.second)
            .asString()

        assertThat(response.body).`as`("availability with the opt-out").contains("\"enabled\":false")
    }
}
