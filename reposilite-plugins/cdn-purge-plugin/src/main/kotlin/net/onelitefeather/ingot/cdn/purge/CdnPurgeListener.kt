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

package net.onelitefeather.ingot.cdn.purge

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** Sends one purge request. The implementation is injected, so the tests use a fake and never open a socket. */
interface PurgeTransport {

    /** Posts [body] to [url], with `Authorization: Bearer` when [bearerToken] is set. Throws on any failure. */
    fun post(url: String, body: String, bearerToken: String?)

}

/**
 * Turns a deploy or a delete into one purge request. The failure of a request is logged through [onFailure] and never thrown, so a
 * deployment or a deletion answers the same way whether the cache was purged or not. There is no retry.
 */
class CdnPurgeListener(
    private val settings: CdnPurgeSettings,
    private val transport: PurgeTransport,
    private val onFailure: (String, Throwable) -> Unit,
) {

    /** Purges the public URL of [path] in [repository]. Does nothing while the purge URL is not set. */
    fun purge(repository: String, path: String) {
        val purgeUrl = settings.purgeUrl ?: return
        val baseUrl = settings.baseUrl ?: return
        val url = publicUrl(baseUrl, repository, path)
        val body = """{"urls": ["${url.replace("\\", "\\\\").replace("\"", "\\\"")}"]}"""
        try {
            transport.post(purgeUrl, body, settings.token)
        } catch (exception: Exception) {
            onFailure("CDN purge of $url failed", exception)
        }
    }

}

/**
 * The public URL of [path] in [repository] under [baseUrl]. Each segment is percent-encoded, and no slash is doubled, whether the base
 * ends with one or the path starts with one.
 */
internal fun publicUrl(baseUrl: String, repository: String, path: String): String {
    val segments = (listOf(repository) + path.split('/'))
        .filter { it.isNotEmpty() }
        .map { URLEncoder.encode(it, StandardCharsets.UTF_8).replace("+", "%20") }
    return baseUrl.trimEnd('/') + "/" + segments.joinToString("/")
}
