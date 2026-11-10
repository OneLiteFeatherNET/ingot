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

package net.onelitefeather.ingot.identity.session

/**
 * Resource isolation for cookie credentials, by the W3C Fetch Metadata headers. A browser sends `Sec-Fetch-Site` with
 * every request and says where it came from. A request under the api that carries the session or refresh cookie, and
 * comes from another site or from a sibling subdomain, is refused before it is authenticated. A sibling subdomain is
 * `same-site`, so it is refused too: it could otherwise set the csrf cookie that the double-submit check trusts.
 *
 * A top-level navigation is exempt, because the identity provider sends the browser back to the callback that way. A
 * request with an Authorization header is a token request and is exempt as well. Every other value, and a missing
 * header, is allowed, so clients that send no fetch metadata keep working.
 */
internal object FetchMetadataPolicy {

    private val REFUSED_SITES = setOf("same-site", "cross-site")

    data class Request(
        val method: String,
        val path: String,
        val hasAuthorizationHeader: Boolean,
        val sessionCookie: String?,
        val refreshCookie: String?,
        val secFetchSite: String?,
        val secFetchMode: String?,
        val secFetchDest: String?,
    )

    /** `true` when the request must be refused with 403. */
    fun violation(request: Request, basePath: String = "/"): Boolean {
        if (request.hasAuthorizationHeader) {
            return false
        }
        if (request.sessionCookie == null && request.refreshCookie == null) {
            return false
        }
        val site = request.secFetchSite?.trim()?.lowercase() ?: return false
        if (site !in REFUSED_SITES) {
            return false
        }
        if (!isApiPath(request.path, basePath)) {
            return false
        }
        return !isTopLevelNavigation(request)
    }

    private fun isTopLevelNavigation(request: Request): Boolean =
        request.secFetchMode.equals("navigate", ignoreCase = true) &&
            request.secFetchDest.equals("document", ignoreCase = true)

    /** The api at the root and below the configured base path, which a reverse proxy may strip before the request arrives. */
    private fun isApiPath(path: String, basePath: String): Boolean {
        val base = basePath.trim().trimEnd('/').let { if (it.isEmpty() || it.startsWith('/')) it else "/$it" }
        val normalized = path.trimEnd('/')
        return listOf("", base).distinct().any { prefix -> normalized == "$prefix/api" || normalized.startsWith("$prefix/api/") }
    }

}

/**
 * What an operator can change about the fetch metadata rule. Read once at start, from the environment variable or,
 * when that is unset or blank, from the system property next to it.
 *
 * @property enabled `INGOT_FETCH_METADATA` / `ingot.fetchMetadata`. On by default. Only the value `off`, in any case,
 * turns the rule off. Any other value keeps it on, so a typo cannot switch the protection off.
 */
data class FetchMetadataSettings(val enabled: Boolean = true) {

    companion object {

        fun resolve(
            environment: (String) -> String? = System::getenv,
            property: (String) -> String? = System::getProperty,
        ): FetchMetadataSettings {
            fun lookup(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() }

            val value = lookup(environment("INGOT_FETCH_METADATA")) ?: lookup(property("ingot.fetchMetadata"))
            return FetchMetadataSettings(enabled = !"off".equals(value, ignoreCase = true))
        }

    }

}
