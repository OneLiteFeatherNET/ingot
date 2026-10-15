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

package net.onelitefeather.ingot.identity.login

/** The kinds of login providers. LOCAL and LDAP check a password; OIDC redirects the browser to the provider. */
enum class LoginProviderType { LOCAL, OIDC, LDAP }

/** A login provider as the login page shows it. [loginUrl] is set for the redirect providers only. */
data class ProviderAnnouncement(
    val id: String,
    val type: LoginProviderType,
    val label: String,
    val icon: String,
    val loginUrl: String? = null,
)

/**
 * A way for a human to prove their identity. A provider only answers whether the credentials are valid for an active
 * user. Rate limiting, auditing and sessions belong to [LoginService] and are the same for every provider.
 */
interface LoginProvider {

    val id: String

    val type: LoginProviderType

    val label: String

    /** Returns [LoginOutcome.Succeeded], [LoginOutcome.Rejected] or [LoginOutcome.Disabled]. */
    fun authenticate(username: String, password: String): LoginOutcome

}
