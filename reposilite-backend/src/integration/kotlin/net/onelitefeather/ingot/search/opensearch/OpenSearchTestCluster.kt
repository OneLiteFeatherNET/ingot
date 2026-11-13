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

package net.onelitefeather.ingot.search.opensearch

import com.github.dockerjava.api.command.InspectContainerResponse
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.io.ByteArrayInputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.time.Duration
import java.util.Base64
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory

/** The OpenSearch image of the container tests: 2.19.3, released well before it was pinned, with the security plugin on. */
internal const val OPENSEARCH_IMAGE = "opensearchproject/opensearch:2.19.3"

/** The admin password of the test cluster. It only protects a container that lives for one test run. */
internal const val OPENSEARCH_ADMIN_PASSWORD = "IngotTest-2026-Secure!"

private const val HTTPS_PORT = 9200
private const val DEMO_AUTHORITY = "/usr/share/opensearch/config/root-ca.pem"

/**
 * The OpenSearch container. Testcontainers caches the inspection of a container, which makes the mapped port of a restarted
 * container stale, so the inspection is read from Docker on every call. The port is then right after a restart too.
 */
internal class OpenSearchContainer : GenericContainer<OpenSearchContainer>(DockerImageName.parse(OPENSEARCH_IMAGE)) {

    override fun getContainerInfo(): InspectContainerResponse =
        DockerClientFactory.instance().client().inspectContainerCmd(containerId).exec()

    /** Stops the container without removing it, so that its indices survive a restart. */
    fun stopKeepingData() {
        DockerClientFactory.instance().client().stopContainerCmd(containerId).exec()
    }

    /** Starts a stopped container again and waits until the cluster answers, through the readiness check of the first start. */
    fun startAgain(readiness: HttpWaitStrategy) {
        DockerClientFactory.instance().client().startContainerCmd(containerId).exec()
        readiness.waitUntilReady(this)
    }

}

/** A started, secured, single-node OpenSearch and the readiness check it was started with. */
internal class StartedOpenSearch(val container: OpenSearchContainer, val readiness: HttpWaitStrategy) {

    /** The HTTPS endpoint of the cluster as the test JVM sees it. */
    fun url(): String = "https://${container.host}:${container.getMappedPort(HTTPS_PORT)}"

    /** The test authority, read from the image's demo configuration, as a context that trusts only that authority. */
    fun sslContext(): SSLContext {
        val pem = container.copyFileFromContainer(DEMO_AUTHORITY) { stream -> stream.readBytes() }
        val certificate = CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(pem))
        val trust = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            setCertificateEntry("opensearch-demo-authority", certificate)
        }
        val managers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(trust) }
        return SSLContext.getInstance("TLS").apply { init(null, managers.trustManagers, null) }
    }

}

/**
 * Starts a single-node OpenSearch with the security plugin and its demo configuration, on a random host port. The start returns
 * when the cluster answers an authenticated request, so no test waits by time.
 */
internal fun startSecuredOpenSearch(): StartedOpenSearch {
    val readiness = HttpWaitStrategy()
        .forPort(HTTPS_PORT)
        .usingTls()
        .allowInsecure()
        .withBasicCredentials("admin", OPENSEARCH_ADMIN_PASSWORD)
        .forStatusCode(200)
    readiness.withStartupTimeout(Duration.ofMinutes(4))
    val container = OpenSearchContainer()
        .withExposedPorts(HTTPS_PORT)
        .withEnv("discovery.type", "single-node")
        .withEnv("OPENSEARCH_INITIAL_ADMIN_PASSWORD", OPENSEARCH_ADMIN_PASSWORD)
        .withEnv("OPENSEARCH_JAVA_OPTS", "-Xms512m -Xmx512m")
        .waitingFor(readiness)
    container.start()
    return StartedOpenSearch(container, readiness)
}

/**
 * Calls the cluster's REST API as the admin of the test cluster, for the fixtures only: the refresh that makes writes visible and
 * the removal of the test index. Production code never calls these.
 */
internal class OpenSearchAdminClient(private val cluster: StartedOpenSearch, sslContext: SSLContext) {

    private val client: HttpClient = HttpClient.newBuilder().sslContext(sslContext).build()
    private val authorization = "Basic " + Base64.getEncoder().encodeToString("admin:$OPENSEARCH_ADMIN_PASSWORD".toByteArray())

    /** Makes every write of [index] visible to searches, as the refresh interval would after its time. */
    fun refresh(index: String) {
        call("POST", "/$index/_refresh", allowMissing = false)
    }

    /** Deletes [index] if it exists. */
    fun deleteIndex(index: String) {
        call("DELETE", "/$index", allowMissing = true)
    }

    private fun call(method: String, path: String, allowMissing: Boolean) {
        val request = HttpRequest.newBuilder(URI.create(cluster.url() + path))
            .header("Authorization", authorization)
            .timeout(Duration.ofSeconds(30))
            .method(method, HttpRequest.BodyPublishers.noBody())
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        val accepted = response.statusCode() in 200..299 || (allowMissing && response.statusCode() == 404)
        check(accepted) { "the test cluster answered ${response.statusCode()} to $method $path" }
    }

}

/** The environment of a backend that writes to the alias [index] on the cluster at [url], with the admin of the test cluster. */
internal fun openSearchTestEnvironment(url: String, index: String, bulkSize: Int = 500): Map<String, String> = mapOf(
    "INGOT_OPENSEARCH_URLS" to url,
    "INGOT_OPENSEARCH_USERNAME" to "admin",
    "INGOT_OPENSEARCH_PASSWORD" to OPENSEARCH_ADMIN_PASSWORD,
    "INGOT_OPENSEARCH_INDEX" to index,
    "INGOT_OPENSEARCH_REPLICAS" to "0",
    "INGOT_OPENSEARCH_BULK_SIZE" to bulkSize.toString(),
)
