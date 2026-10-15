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

import com.reposilite.configuration.shared.api.Doc
import com.reposilite.configuration.shared.api.SharedSettings
import io.javalin.openapi.JsonSchema
import net.onelitefeather.ingot.identity.login.oidc.OidcProviderSettings

@JsonSchema(requireNonNulls = false)
@Doc(title = "Login", description = "Browser login settings")
data class LoginSettings(
    @get:Doc(title = "Session Lifetime Minutes", description = "Lifetime of a browser session token. Logout and a disabled user take effect at the latest when it expires")
    val sessionLifetimeMinutes: Long = 15,
    @get:Doc(title = "Refresh Lifetime Hours", description = "Lifetime of a refresh token. Every renewal issues a new one, so this bounds the idle time of a browser session")
    val refreshLifetimeHours: Long = 168,
    @get:Doc(title = "Refresh Absolute Days", description = "Longest time a login stays renewable, counted from the login itself. Renewals cannot extend it, so a browser must log in again after this many days")
    val refreshAbsoluteDays: Long = 30,
    @get:Doc(title = "Maximum Failed Attempts", description = "Failed password logins that lock a user name from one address, and the same number of failures that lock an address. Applies by default, independently of the brute force protection of Basic and token authentication")
    val maxFailedAttempts: Int = 5,
    @get:Doc(title = "Lockout Minutes", description = "How long a locked user name or address stays locked after the first lockout. Every further lockout without a quiet period doubles it, up to 24 hours")
    val lockoutMinutes: Long = 15,
    @get:Doc(title = "OpenID Connect Providers", description = "External identity providers. Each entry is disabled by default; see the identity model, section 14")
    val oidcProviders: List<OidcProviderSettings> = emptyList(),
) : SharedSettings
