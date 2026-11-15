package net.onelitefeather.ingot.search.elasticsearch

import com.github.dockerjava.api.command.InspectContainerResponse
import com.reposilite.ReposiliteObjectMapper
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy
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
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory

/** The Elasticsearch 8.x image of the container tests: the latest 8.19 patch release, with security on by default. */
internal const val ELASTICSEARCH_8_IMAGE = "docker.elastic.co/elasticsearch/elasticsearch:8.19.11"

/** The Elasticsearch 9.x image of the container tests: 9.3.3, the latest 9.x patch of the 9.3 minor. */
internal const val ELASTICSEARCH_9_IMAGE = "docker.elastic.co/elasticsearch/elasticsearch:9.3.3"

/** The password of the built-in `elastic` user of the test cluster. It only protects a container that lives for one test run. */
internal const val ELASTIC_PASSWORD = "IngotTest-2026-Secure!"

private const val HTTPS_PORT = 9200
private const val HTTP_AUTHORITY = "/usr/share/elasticsearch/config/certs/http_ca.crt"

/**
 * The Elasticsearch container. Testcontainers caches the inspection of a container, which makes the mapped port of a restarted
 * container stale, so the inspection is read from Docker on every call. The port is then right after a restart too.
 */
internal class ElasticsearchContainer(image: String) : GenericContainer<ElasticsearchContainer>(DockerImageName.parse(image)) {

    override fun getContainerInfo(): InspectContainerResponse =
        DockerClientFactory.instance().client().inspectContainerCmd(containerId).exec()

    /** Stops the container without removing it, so that its indices and its security index survive a restart. */
    fun stopKeepingData() {
        DockerClientFactory.instance().client().stopContainerCmd(containerId).exec()
    }

    /** Starts a stopped container again and waits until the cluster answers, through the readiness check of the first start. */
    fun startAgain(readiness: HttpWaitStrategy) {
        DockerClientFactory.instance().client().startContainerCmd(containerId).exec()
        readiness.waitUntilReady(this)
    }

}

/** A started, secured, single-node Elasticsearch and the readiness check it was started with. */
internal class StartedElasticsearch(val container: ElasticsearchContainer, val readiness: HttpWaitStrategy) {

    /** The HTTPS endpoint of the cluster as the test JVM sees it. */
    fun url(): String = "https://${container.host}:${container.getMappedPort(HTTPS_PORT)}"

    /** The HTTP certificate authority of the cluster, written by the image at its first start, as a context that trusts only it. */
    fun sslContext(): SSLContext {
        val pem = container.copyFileFromContainer(HTTP_AUTHORITY) { stream -> stream.readBytes() }
        val certificate = CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(pem))
        val trust = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            setCertificateEntry("elasticsearch-http-authority", certificate)
        }
        val managers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(trust) }
        return SSLContext.getInstance("TLS").apply { init(null, managers.trustManagers, null) }
    }

}

/**
 * Starts a single-node Elasticsearch of [image] with security on and the password of the `elastic` user, on a random host port. The
 * start returns when the cluster answers an authenticated request, so no test waits by time.
 */
internal fun startSecuredElasticsearch(image: String): StartedElasticsearch {
    val readiness = HttpWaitStrategy()
        .forPort(HTTPS_PORT)
        .usingTls()
        .allowInsecure()
        .withBasicCredentials("elastic", ELASTIC_PASSWORD)
        .forStatusCode(200)
    readiness.withStartupTimeout(Duration.ofMinutes(4))
    val container = ElasticsearchContainer(image)
        .withExposedPorts(HTTPS_PORT)
        .withEnv("discovery.type", "single-node")
        .withEnv("ELASTIC_PASSWORD", ELASTIC_PASSWORD)
        .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m")
        .waitingFor(readiness)
    container.start()
    return StartedElasticsearch(container, readiness)
}

/**
 * Calls the cluster's REST API as the `elastic` user of the test cluster, for the fixtures only: the refresh that makes writes
 * visible, the removal of the test index, and the API key of a test, which production code never creates.
 */
internal class ElasticsearchAdminClient(private val cluster: StartedElasticsearch, sslContext: SSLContext) {

    private val client: HttpClient = HttpClient.newBuilder().sslContext(sslContext).build()
    private val mapper = ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER
    private val authorization = "Basic " + Base64.getEncoder().encodeToString("elastic:$ELASTIC_PASSWORD".toByteArray())

    /** Makes every write of [index] visible to searches, as the refresh interval would after its time. */
    fun refresh(index: String) {
        call("POST", "/$index/_refresh", body = null, allowMissing = false)
    }

    /** Deletes [index] if it exists. */
    fun deleteIndex(index: String) {
        call("DELETE", "/$index", body = null, allowMissing = true)
    }

    /**
     * Creates an API key that holds exactly the privileges of the design (D10): the cluster privilege `monitor`, and on the index
     * pattern `<prefix>-*` and the alias `<prefix>` the index privileges the plugin uses. Returns the encoded key the plugin is given.
     */
    fun createLeastPrivilegeApiKey(prefix: String): String {
        val body = mapOf(
            "name" to "$prefix-plugin",
            "role_descriptors" to mapOf(
                "ingot" to mapOf(
                    "cluster" to listOf("monitor"),
                    "indices" to listOf(
                        mapOf(
                            "names" to listOf("$prefix-*", prefix),
                            "privileges" to listOf("create_index", "read", "write", "delete", "manage"),
                        ),
                    ),
                ),
            ),
        )
        val answer = mapper.readTree(call("POST", "/_security/api_key", mapper.writeValueAsString(body), allowMissing = false))
        return answer.path("encoded").asText().also { check(it.isNotEmpty()) { "the cluster returned no encoded API key" } }
    }

    private fun call(method: String, path: String, body: String?, allowMissing: Boolean): String {
        val publisher = body?.let { HttpRequest.BodyPublishers.ofString(it) } ?: HttpRequest.BodyPublishers.noBody()
        val request = HttpRequest.newBuilder(URI.create(cluster.url() + path))
            .header("Authorization", authorization)
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(30))
            .method(method, publisher)
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        val accepted = response.statusCode() in 200..299 || (allowMissing && response.statusCode() == 404)
        check(accepted) { "the test cluster answered ${response.statusCode()} to $method $path" }
        return response.body()
    }

}

/** The environment of a backend that writes to the alias [index] on the cluster at [url], with the key of the test. */
internal fun elasticsearchTestEnvironment(url: String, index: String, apiKey: String, bulkSize: Int = 500): Map<String, String> = mapOf(
    "INGOT_ELASTICSEARCH_URLS" to url,
    "INGOT_ELASTICSEARCH_API_KEY" to apiKey,
    "INGOT_ELASTICSEARCH_INDEX" to index,
    "INGOT_ELASTICSEARCH_REPLICAS" to "0",
    "INGOT_ELASTICSEARCH_BULK_SIZE" to bulkSize.toString(),
)

/** A fresh alias name for one test: lower case, within the 64 characters the plugin accepts. */
internal fun newElasticsearchIndexName(): String = "ingot-" + java.util.UUID.randomUUID().toString().replace("-", "")

/**
 * The started clusters of this JVM, one per image. A cluster starts on first use and serves every test of its image, and every test
 * writes to an index of its own.
 */
internal object ElasticsearchClusters {

    private val started = ConcurrentHashMap<String, StartedElasticsearch>()

    fun of(image: String): StartedElasticsearch = started.computeIfAbsent(image) { startSecuredElasticsearch(it) }

}
