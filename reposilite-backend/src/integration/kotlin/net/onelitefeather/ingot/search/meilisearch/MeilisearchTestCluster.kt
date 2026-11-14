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

package net.onelitefeather.ingot.search.meilisearch

import com.github.dockerjava.api.command.InspectContainerResponse
import com.reposilite.ReposiliteObjectMapper
import net.onelitefeather.ingot.search.http.HttpAnswer
import net.onelitefeather.ingot.search.http.HttpCall
import net.onelitefeather.ingot.search.http.HttpExchange
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy
import org.testcontainers.utility.DockerImageName
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** The Meilisearch image of the container tests, pinned by tag and digest (v1.15, the minor version the plugin supports). */
internal const val MEILISEARCH_IMAGE: String =
    "getmeili/meilisearch:v1.15@sha256:fe500cf9cca05cb9f027981583f28eccf17d35d94499c1f8b7b844e7418152fc"

/** The master key of the test server. It only protects a container that lives for one test run. */
internal const val MEILISEARCH_MASTER_KEY: String = "IngotTestMasterKey-2026-0123456789abcdef"

private const val HTTP_PORT = 7700

/**
 * The actions of the minimal key that the reference page documents. The restricted contract runs every case with a key that
 * carries exactly these, on the indexes named ingot-*, so a missing action fails a case.
 */
internal val MEILISEARCH_MINIMAL_ACTIONS: List<String> = listOf(
    "indexes.create", "indexes.get", "settings.get", "settings.update", "documents.add", "documents.get", "documents.delete",
    "search", "stats.get", "tasks.get", "version",
)

/**
 * The Meilisearch container. Testcontainers caches the inspection of a container, which makes the mapped port of a restarted
 * container stale, so the inspection is read from Docker on every call.
 */
internal class MeilisearchContainer : GenericContainer<MeilisearchContainer>(DockerImageName.parse(MEILISEARCH_IMAGE)) {

    override fun getContainerInfo(): InspectContainerResponse =
        DockerClientFactory.instance().client().inspectContainerCmd(containerId).exec()

    /** Stops the container without removing it, so that its indexes survive a restart. */
    fun stopKeepingData() {
        DockerClientFactory.instance().client().stopContainerCmd(containerId).exec()
    }

}

/** A started Meilisearch on a random host port, with the readiness check it was started with (`/health`). */
internal class StartedMeilisearch(val container: MeilisearchContainer, private val readiness: HttpWaitStrategy) {

    /** The base URL of the server as the test JVM sees it. Plain HTTP, which the test environment accepts explicitly. */
    fun url(): String = "http://${container.host}:${container.getMappedPort(HTTP_PORT)}"

    /** Starts a stopped container again and waits until `/health` answers. */
    fun startAgain() {
        DockerClientFactory.instance().client().startContainerCmd(container.containerId).exec()
        readiness.waitUntilReady(container)
    }

    fun stop() {
        container.stop()
    }

}

/** Starts a Meilisearch server with a master key. The start returns when `/health` answers, so no test waits by time. */
internal fun startMeilisearch(): StartedMeilisearch {
    val readiness = HttpWaitStrategy().forPath("/health").forPort(HTTP_PORT).forStatusCode(200)
    readiness.withStartupTimeout(Duration.ofMinutes(2))
    val container = MeilisearchContainer()
        .withExposedPorts(HTTP_PORT)
        .withEnv("MEILI_MASTER_KEY", MEILISEARCH_MASTER_KEY)
        .withEnv("MEILI_NO_ANALYTICS", "true")
        .waitingFor(readiness)
    container.start()
    return StartedMeilisearch(container, readiness)
}

/**
 * Calls the server as the master key holder, for the fixtures only: index removal, settings, keys and the state of the index.
 * Production code never calls these.
 */
internal class MeilisearchAdminClient(private val server: StartedMeilisearch) {

    private val client: HttpClient = HttpClient.newHttpClient()

    /** Deletes [index] when it exists. */
    fun deleteIndex(index: String) {
        request("DELETE", "/indexes/$index", allowMissing = true)
    }

    /** Replaces the settings of [index] with [settings] as the server takes them, and waits for the task of the change. */
    fun putSettings(index: String, settings: Map<String, Any>) {
        val task = request("PATCH", "/indexes/$index/settings", ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER.writeValueAsString(settings))
        awaitTask(ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER.readTree(task).path("taskUid").asLong())
    }

    /** Creates a key that carries exactly [actions] on the indexes [indexes], and returns its secret. */
    fun createKey(actions: List<String>, indexes: List<String>): String {
        // The server requires the field expiresAt, and it must be sent as null: the shared mapper leaves null fields out.
        val body = """{"description":"ingot container test","actions":${ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER.writeValueAsString(actions)},""" +
            """"indexes":${ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER.writeValueAsString(indexes)},"expiresAt":null}"""
        return ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER.readTree(request("POST", "/keys", body)).path("key").asText()
    }

    private fun awaitTask(uid: Long) {
        repeat(TASK_POLLS) {
            val status = ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER.readTree(request("GET", "/tasks/$uid"))
                .path("status").asText()
            if (status == "succeeded") return
            check(status != "failed") { "the fixture task $uid failed" }
        }
        error("the fixture task $uid did not finish")
    }

    /** One call of the master key. Returns the body; a missing resource is accepted when [allowMissing] is set. */
    fun request(method: String, path: String, body: String? = null, allowMissing: Boolean = false): String {
        val builder = HttpRequest.newBuilder(URI.create(server.url() + path))
            .header("Authorization", "Bearer $MEILISEARCH_MASTER_KEY")
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(30))
        val publisher = body?.let { HttpRequest.BodyPublishers.ofString(it) } ?: HttpRequest.BodyPublishers.noBody()
        val response = client.send(builder.method(method, publisher).build(), HttpResponse.BodyHandlers.ofString())
        val accepted = response.statusCode() in 200..299 || (allowMissing && response.statusCode() == 404)
        check(accepted) { "the test server answered ${response.statusCode()} to $method $path: ${response.body().take(300)}" }
        return response.body()
    }

    private companion object {
        const val TASK_POLLS = 600
    }

}

/**
 * An exchange that the container tests steer, around the real transport. A held task is answered as still processing while the
 * server finishes it, which makes the bound of a wait real. A dropped id removes the `id` of the next document write, so the server
 * fails that task. Every other call goes to the delegate unchanged.
 */
internal class SteeredExchange(private val delegate: HttpExchange) : HttpExchange {

    @Volatile
    var holdTasks: Boolean = false

    @Volatile
    var dropNextDocumentIds: Boolean = false

    override fun exchange(call: HttpCall): HttpAnswer {
        val task = TASK_PATH.matchEntire(call.uri.path)
        if (holdTasks && call.method == "GET" && task != null) {
            return HttpAnswer(200, emptyMap(), """{"uid":${task.groupValues[1]},"status":"processing"}""")
        }
        if (dropNextDocumentIds && call.method == "POST" && call.uri.path.endsWith("/documents")) {
            dropNextDocumentIds = false
            return delegate.exchange(call.copy(body = withoutIds(call.body)))
        }
        return delegate.exchange(call)
    }

    private fun withoutIds(body: String?): String {
        val mapper = ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER
        val documents = mapper.readTree(body.orEmpty())
        documents.forEach { document -> (document as com.fasterxml.jackson.databind.node.ObjectNode).remove("id") }
        return mapper.writeValueAsString(documents)
    }

    private companion object {
        val TASK_PATH = Regex(".*/tasks/(\\d+)")
    }

}

/** The environment of a backend that writes to [index] on the server at [url], with [key] as its key and a one second task bound. */
internal fun meilisearchTestEnvironment(url: String, index: String, key: String = MEILISEARCH_MASTER_KEY): Map<String, String> = mapOf(
    "INGOT_MEILISEARCH_URL" to url,
    "INGOT_MEILISEARCH_ALLOW_HTTP" to "true",
    "INGOT_MEILISEARCH_API_KEY" to key,
    "INGOT_MEILISEARCH_INDEX" to index,
    "INGOT_MEILISEARCH_TASK_WAIT_SECONDS" to "20",
)
