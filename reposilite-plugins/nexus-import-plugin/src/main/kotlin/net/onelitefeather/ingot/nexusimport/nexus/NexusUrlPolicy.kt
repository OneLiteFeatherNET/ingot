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

import java.net.URI

/**
 * Decides which Nexus URLs the importer accepts. A URL with user information is refused, because the secret would end up
 * in a stored value and in logs. Plain http is accepted with a warning. With an allow list, only its hosts are accepted.
 */
class NexusUrlPolicy(private val allowedHosts: Set<String>?) {

    data class Outcome(
        /** The reason the URL is refused, null when it is accepted. It never repeats the URL. */
        val refusal: String?,
        val warnings: List<String>,
        val uri: URI?,
    )

    fun check(raw: String): Outcome {
        val uri = runCatching { URI(raw.trim()) }.getOrNull()
            ?: return refused("The Nexus URL is not a valid URL")
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") {
            return refused("The Nexus URL must use http or https")
        }
        if (uri.rawUserInfo != null) {
            return refused("The Nexus URL must not contain user information, send the credential in the request instead")
        }
        val host = uri.host?.lowercase() ?: return refused("The Nexus URL has no host name")
        if (allowedHosts != null && host !in allowedHosts) {
            return refused("The host of the Nexus URL is not on the allow list of this instance")
        }
        val warnings = if (scheme == "http") listOf(PLAIN_HTTP_WARNING) else emptyList()
        return Outcome(refusal = null, warnings = warnings, uri = uri)
    }

    private fun refused(reason: String) = Outcome(refusal = reason, warnings = emptyList(), uri = null)

    companion object {
        const val PLAIN_HTTP_WARNING =
            "The Nexus URL uses plain http, so the password crosses the network unencrypted. Use https."
    }
}
