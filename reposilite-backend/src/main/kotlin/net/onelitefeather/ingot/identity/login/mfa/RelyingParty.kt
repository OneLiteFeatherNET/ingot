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

package net.onelitefeather.ingot.identity.login.mfa

import java.net.URI
import net.onelitefeather.ingot.identity.login.oidc.OidcRedirectUri

/** Answer of a passkey route when the instance has no address to bind the passkeys to. */
internal const val NO_RELYING_PARTY_MESSAGE =
    "Passkeys need the public address of the dashboard: set login.publicUrl, or open the dashboard through its address"

/**
 * The relying party of the passkey ceremonies (identity model D11): the identifier is the host of the public address, and the
 * origin is its scheme, host and port. A passkey is bound to this host, so a ceremony from another origin is refused.
 */
internal data class RelyingParty(val id: String, val origin: String) {

    companion object {

        /**
         * The relying party of the public address [publicUrl], or of the request when it is blank: [secure] gives the scheme and
         * [host] the `Host` of the request. The rule is the one of the external login redirect URIs. `null` when neither gives
         * an address.
         */
        fun of(publicUrl: String, secure: Boolean, host: String?): RelyingParty? {
            val scheme = if (secure) "https" else "http"
            // The redirect URI only carries the address; its path is not used, so the base path stays empty.
            val address = runCatching {
                URI(OidcRedirectUri.of(publicUrl = publicUrl, scheme = scheme, host = host.orEmpty(), basePath = "", id = "passkey"))
            }.getOrNull() ?: return null
            val id = address.host?.takeIf { it.isNotEmpty() } ?: return null
            val port = if (address.port == -1) "" else ":${address.port}"
            return RelyingParty(id = id, origin = "${address.scheme}://$id$port")
        }
    }

}
