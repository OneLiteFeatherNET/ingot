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

package net.onelitefeather.ingot.deploy

import com.reposilite.RecommendedRemoteSpecificationJunitExtension
import com.reposilite.ReposiliteParameters
import com.reposilite.ReposiliteSpecification
import com.reposilite.SharedContainers
import com.reposilite.maven.api.LookupRequest
import com.reposilite.storage.api.toLocation
import com.reposilite.token.AccessTokenPermission.MANAGER
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extension
import org.junit.jupiter.api.extension.ExtensionContext
import kong.unirest.core.Unirest.get
import kong.unirest.core.Unirest.put

/**
 * An instance started with the deploy target `worker` answers the probes and nothing else that serves repositories or the
 * dashboard. A worker only starts with shared state, so the test runs on MariaDB, on S3 storage and on the Valkey cluster bus.
 * Each test boots an instance of its own, so no test depends on another one.
 */
@ExtendWith(RecommendedRemoteSpecificationJunitExtension::class, ValkeyClusterBusJunitExtension::class)
internal class WorkerRoutingTest : ReposiliteSpecification() {

    private val artifactPath = "/releases/org/example/lib/1.0/lib-1.0.jar"
    private val artifactGav = "org/example/lib/1.0/lib-1.0.jar"

    override fun overrideParameters(parameters: ReposiliteParameters) {
        parameters.target = "worker"
    }

    /** The worker check reads the repositories while the instance is created, so they are on object storage from the start. */
    override fun seedsSharedStorage(): Boolean = true

    /** The artifact exists before each test, so a download that is served would answer 200. */
    @BeforeEach
    fun storeArtifact() {
        useDocument("releases", "org/example/lib/1.0", "lib-1.0.jar", ORIGINAL_CONTENT, store = true)
    }

    @Test
    fun `should answer the health probe on the worker`() {
        val response = get("$base/api/status/health").asString()

        assertThat(response.status).describedAs("status of GET /api/status/health on target worker").isEqualTo(200)
    }

    @Test
    fun `should answer the readiness probe on the worker`() {
        val response = get("$base/api/status/ready").asString()

        assertThat(response.status).describedAs("status of GET /api/status/ready on target worker").isEqualTo(200)
    }

    @Test
    fun `should answer the instance status on the worker`() {
        val (name, secret) = useAuth("worker-monitor", "worker-monitor-secret", listOf(MANAGER))

        val response = get("$base/api/status/instance").basicAuth(name, secret).asString()

        assertThat(response.status).describedAs("status of GET /api/status/instance with a manager on target worker").isEqualTo(200)
    }

    @Test
    fun `should answer 404 for a repository download on the worker`() {
        val response = get("$base$artifactPath").asString()

        assertThat(response.status).describedAs("status of GET $artifactPath on target worker").isEqualTo(404)
    }

    @Test
    fun `should answer 404 for an artifact upload on the worker and write nothing`() {
        val (name, secret) = useAuth("worker-deployer", "worker-deployer-secret", listOf(MANAGER))

        val response = put("$base$artifactPath").basicAuth(name, secret).body("artifact").asString()

        assertThat(response.status).describedAs("status of PUT $artifactPath on target worker").isEqualTo(404)
        assertThat(storedArtifact())
            .describedAs("the stored artifact is left as it was on target worker")
            .isEqualTo(ORIGINAL_CONTENT)
    }

    @Test
    fun `should answer 404 for the settings api on the worker`() {
        val (name, secret) = useAuth("worker-settings", "worker-settings-secret", listOf(MANAGER))

        val response = get("$base/api/settings/domains").basicAuth(name, secret).asString()

        assertThat(response.status).describedAs("status of GET /api/settings/domains with a manager on target worker").isEqualTo(404)
    }

    @Test
    fun `should answer 404 for the dashboard on the worker`() {
        val response = get("$base/").asString()

        assertThat(response.status).describedAs("status of GET / on target worker").isEqualTo(404)
    }

    /** Reads the artifact through the facade, because the worker serves no repository over HTTP to read it back. */
    private fun storedArtifact(): String =
        mavenFacade.findFile(LookupRequest(accessToken = null, repository = "releases", gav = artifactGav.toLocation()))
            .also { assertThat(it.isOk).describedAs("the artifact is stored on the shared storage").isTrue }
            .get()
            .content
            .readAllBytes()
            .decodeToString()

    private companion object {
        const val ORIGINAL_CONTENT = "original artifact"
    }

}

/**
 * Points the cluster bus of the instance at the shared Valkey broker, because a worker refuses to start without a bus. The
 * bus URL is read from the system property `ingot.cluster.url` when the instance starts, so the property is set for the
 * length of the test only. The cluster integration test reads the same property, and the tests of one JVM run one after
 * another.
 */
internal class ValkeyClusterBusJunitExtension : Extension, BeforeEachCallback, AfterEachCallback {

    override fun beforeEach(context: ExtensionContext) {
        val valkey = SharedContainers.valkey
        System.setProperty("ingot.cluster.url", "valkey://${valkey.host}:${valkey.getMappedPort(SharedContainers.VALKEY_PORT)}")
    }

    override fun afterEach(context: ExtensionContext) {
        System.clearProperty("ingot.cluster.url")
    }

}
