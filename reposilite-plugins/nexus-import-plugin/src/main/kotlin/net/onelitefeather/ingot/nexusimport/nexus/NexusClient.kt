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

package net.onelitefeather.ingot.nexusimport.nexus

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Base64

/**
 * The Nexus REST v1 surface the importer reads: status, repositories, the three Maven settings endpoints, the asset listing
 * and the content download. Every request is a GET. The client never sends a page size, it follows `continuationToken`.
 *
 * The Authorization header goes only to the origin of [connection]. A `downloadUrl` on another origin is refused, and a
 * redirect to another origin is followed without the header, at most [MAX_REDIRECTS] times.
 *
 * Waits are injected: [pacing] spaces requests and [sleeper] performs the waits, so tests advance a fake clock instead of
 * sleeping. Retries back off exponentially, or follow `Retry-After` when Nexus sends it.
 */
class NexusClient(
    private val connection: NexusConnection,
    private val transport: NexusTransport,
    private val clock: Clock,
    private val sleeper: (Duration) -> Unit,
    private val requestsPerSecond: Int = DEFAULT_REQUESTS_PER_SECOND,
) {

    private val objectMapper = ObjectMapper()
    private val spacing = Duration.ofMillis(1000L / requestsPerSecond.coerceIn(1, 100))
    private var nextRequestAt: Instant = Instant.EPOCH

    /** Reachability and the credential check: a GET of the status endpoint, which needs no credential. */
    fun status(): NexusReply = send(connection.baseUri.resolve(STATUS_PATH), authenticated = false)

    /** The repository list. A `401` or `403` here is the answer of the connection test, not an error to hide. */
    fun repositories(): List<NexusRepository> {
        val body = json(send(connection.baseUri.resolve(REPOSITORIES_PATH), authenticated = true))
        return body.map { node ->
            NexusRepository(
                name = node.text("name"),
                format = node.text("format"),
                type = node.text("type"),
                url = node.textOrNull("url"),
            )
        }
    }

    /** The settings of one repository, `kind` being `hosted`, `proxy` or `group`. A `403` says the credential may not read them. */
    fun mavenSettings(kind: String, name: String): JsonNode =
        json(send(connection.baseUri.resolve("$REPOSITORIES_PATH/maven/$kind/${encode(name)}"), authenticated = true))

    /** One page of assets of [repository]. The first page has no token. */
    fun assetPage(repository: String, continuationToken: String?): NexusAssetPage {
        val query = buildString {
            append("repository=").append(encode(repository))
            if (continuationToken != null) append("&continuationToken=").append(encode(continuationToken))
        }
        val body = json(send(connection.baseUri.resolve("$ASSETS_PATH?$query"), authenticated = true))
        val items = body.path("items").map { item ->
            NexusAsset(
                repository = repository,
                path = item.text("path"),
                downloadUrl = item.text("downloadUrl"),
                fileSize = item.path("fileSize").asLong(-1L),
                checksums = NexusChecksums(
                    sha1 = item.path("checksum").textOrNull("sha1"),
                    md5 = item.path("checksum").textOrNull("md5"),
                    sha256 = item.path("checksum").textOrNull("sha256"),
                    sha512 = item.path("checksum").textOrNull("sha512"),
                ),
                lastModified = item.textOrNull("lastModified"),
            )
        }
        return NexusAssetPage(items, body.textOrNull("continuationToken"))
    }

    /**
     * Opens the content of [asset]. The caller closes the reply. The URL must be on the connection origin, otherwise the
     * request is refused before anything is sent.
     */
    fun download(asset: NexusAsset): NexusReply {
        val uri = URI.create(asset.downloadUrl)
        if (!connection.isSameOrigin(uri)) {
            throw NexusRefusedException("The download URL of ${asset.path} points to another origin and was not requested")
        }
        return send(uri, authenticated = true, stream = true)
    }

    private fun send(start: URI, authenticated: Boolean, stream: Boolean = false): NexusReply {
        var uri = start
        var redirects = 0
        var attempt = 1
        while (true) {
            pace()
            val headers = buildMap {
                put("Accept", "application/json")
                if (authenticated && connection.isSameOrigin(uri)) put("Authorization", connection.authorizationHeader())
            }
            val reply = try {
                transport.get(uri, headers)
            } catch (exception: IOException) {
                if (attempt >= MAX_ATTEMPTS) throw NexusUnavailableException("Nexus did not answer: ${exception.javaClass.simpleName}")
                sleeper(backoff(attempt, retryAfter = null))
                attempt++
                continue
            }

            when {
                reply.status in 300..399 && reply.headers["location"] != null -> {
                    reply.body.close()
                    if (++redirects > MAX_REDIRECTS) throw NexusUnavailableException("Nexus redirected more than $MAX_REDIRECTS times")
                    uri = uri.resolve(reply.headers.getValue("location"))
                }

                reply.status == 429 || reply.status >= 500 -> {
                    reply.body.close()
                    if (attempt >= MAX_ATTEMPTS) throw NexusHttpException(reply.status, uri)
                    sleeper(backoff(attempt, retryAfter = reply.headers["retry-after"]))
                    attempt++
                }

                reply.status in 200..299 -> {
                    return if (stream) NexusReply(reply.status, reply.body) else NexusReply(reply.status, reply.body.use { it.readBytes() }.inputStream())
                }

                else -> {
                    reply.body.close()
                    throw NexusHttpException(reply.status, uri)
                }
            }
        }
    }

    /** Reserves the next request slot and waits until it, so the rate holds across the listing and the downloads. */
    private fun pace() {
        val now = clock.instant()
        val slot = maxOf(nextRequestAt, now)
        if (slot.isAfter(now)) {
            sleeper(Duration.between(now, slot))
        }
        nextRequestAt = slot.plus(spacing)
    }

    private fun backoff(attempt: Int, retryAfter: String?): Duration {
        val fromHeader = retryAfter?.trim()?.toLongOrNull()?.let { Duration.ofSeconds(it) }
        return fromHeader ?: Duration.ofMillis(BASE_BACKOFF.toMillis() shl (attempt - 1))
    }

    private fun json(reply: NexusReply): JsonNode =
        reply.body.use { objectMapper.readTree(it) }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")

    companion object {
        const val STATUS_PATH = "/service/rest/v1/status"
        const val REPOSITORIES_PATH = "/service/rest/v1/repositories"
        const val ASSETS_PATH = "/service/rest/v1/assets"
        const val DEFAULT_REQUESTS_PER_SECOND = 5
        const val MAX_ATTEMPTS = 5
        const val MAX_REDIRECTS = 3
        val BASE_BACKOFF: Duration = Duration.ofSeconds(1)
    }
}

/** A successful answer whose body the caller reads and closes. */
class NexusReply(val status: Int, val body: java.io.InputStream)

/** The connection the importer was given. The password is a [Secret] and never part of a message. */
class NexusConnection(
    val baseUri: URI,
    val user: String,
    private val password: Secret,
) {
    private val origin = origin(baseUri)

    fun isSameOrigin(uri: URI): Boolean = origin(uri) == origin

    internal fun authorizationHeader(): String =
        "Basic " + Base64.getEncoder().encodeToString("$user:${password.reveal()}".toByteArray(StandardCharsets.UTF_8))

    private fun origin(uri: URI): String =
        "${uri.scheme?.lowercase()}://${uri.host?.lowercase()}:${if (uri.port == -1) defaultPort(uri) else uri.port}"

    private fun defaultPort(uri: URI): Int = if (uri.scheme.equals("https", ignoreCase = true)) 443 else 80
}

data class NexusRepository(val name: String, val format: String, val type: String, val url: String?)

data class NexusChecksums(val sha1: String?, val md5: String?, val sha256: String?, val sha512: String?)

data class NexusAsset(
    val repository: String,
    val path: String,
    val downloadUrl: String,
    val fileSize: Long,
    val checksums: NexusChecksums,
    val lastModified: String?,
)

data class NexusAssetPage(val items: List<NexusAsset>, val continuationToken: String?)

/** A status that Nexus answered. The URI carries no credential, so it is safe to report. */
class NexusHttpException(val status: Int, val uri: URI) : RuntimeException("Nexus answered HTTP $status for ${uri.path}")

class NexusRefusedException(message: String) : RuntimeException(message)

class NexusUnavailableException(message: String) : RuntimeException(message)

private fun JsonNode.text(field: String): String = path(field).asText()

private fun JsonNode.textOrNull(field: String): String? =
    path(field).takeIf { !it.isNull && !it.isMissingNode }?.asText()
