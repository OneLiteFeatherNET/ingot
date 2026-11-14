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

/** A started Meilisearch on a random host port, with the readiness check it was started with (`/health`). */
internal class StartedMeilisearch(val container: GenericContainer<*>) {

    /** The base URL of the server as the test JVM sees it. Plain HTTP, which the test environment accepts explicitly. */
    fun url(): String = "http://${container.host}:${container.getMappedPort(HTTP_PORT)}"

}

/** Starts a Meilisearch server with a master key. The start returns when `/health` answers, so no test waits by time. */
internal fun startMeilisearch(): StartedMeilisearch {
    val container = GenericContainer(DockerImageName.parse(MEILISEARCH_IMAGE))
        .withExposedPorts(HTTP_PORT)
        .withEnv("MEILI_MASTER_KEY", MEILISEARCH_MASTER_KEY)
        .withEnv("MEILI_NO_ANALYTICS", "true")
        .waitingFor(HttpWaitStrategy().forPath("/health").forPort(HTTP_PORT).forStatusCode(200).withStartupTimeout(Duration.ofMinutes(2)))
    container.start()
    return StartedMeilisearch(container)
}

/**
 * Calls the server as the master key holder, for the fixtures only: the removal of a test index. Production code never calls this.
 */
internal class MeilisearchAdminClient(private val server: StartedMeilisearch) {

    private val client: HttpClient = HttpClient.newHttpClient()

    /** Deletes [index] when it exists, and waits for the deletion task so that the next test starts clean. */
    fun deleteIndex(index: String) {
        val request = HttpRequest.newBuilder(URI.create("${server.url()}/indexes/$index"))
            .header("Authorization", "Bearer $MEILISEARCH_MASTER_KEY")
            .timeout(Duration.ofSeconds(30))
            .DELETE()
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() in 200..299 || response.statusCode() == 404) {
            "the test server answered ${response.statusCode()} to the deletion of $index"
        }
    }

}

/** The environment of a backend that writes to [index] on the server at [url], with the master key as its key. */
internal fun meilisearchTestEnvironment(url: String, index: String): Map<String, String> = mapOf(
    "INGOT_MEILISEARCH_URL" to url,
    "INGOT_MEILISEARCH_ALLOW_HTTP" to "true",
    "INGOT_MEILISEARCH_API_KEY" to MEILISEARCH_MASTER_KEY,
    "INGOT_MEILISEARCH_INDEX" to index,
    "INGOT_MEILISEARCH_TASK_WAIT_SECONDS" to "20",
)
