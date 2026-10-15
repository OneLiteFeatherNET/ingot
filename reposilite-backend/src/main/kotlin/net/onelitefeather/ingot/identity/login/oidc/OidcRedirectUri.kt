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

package net.onelitefeather.ingot.identity.login.oidc

/**
 * The redirect uri of an OpenID Connect or GitHub login, the one the provider compares with the registered uri. A configured
 * public URL is the base of the callback. Without one, the callback is built from the scheme and the `Host` of the request
 * and the configured base path.
 */
internal object OidcRedirectUri {

    fun of(publicUrl: String, scheme: String, host: String, basePath: String, id: String): String {
        val configured = publicUrl.trim().trimEnd('/')
        if (configured.isNotEmpty()) return "$configured/api/auth/oidc/$id/callback"
        return "$scheme://$host${trimmed(basePath)}/api/auth/oidc/$id/callback"
    }

    /** The base path with a leading slash and without a trailing one, `""` for the root. */
    private fun trimmed(basePath: String): String =
        basePath.trim().trimEnd('/').let { if (it.isEmpty() || it.startsWith('/')) it else "/$it" }

}
