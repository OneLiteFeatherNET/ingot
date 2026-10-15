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

package net.onelitefeather.ingot.identity.login.api

import io.javalin.openapi.JsonSchema
import java.time.Instant

@JsonSchema(requireNonNulls = false)
data class LoginProviderDto(
    val id: String,
    val type: String,
    val label: String,
)

/** `GET /api/auth/providers`. [setupRequired] is true while no human user exists, see the setup code. */
@JsonSchema(requireNonNulls = false)
data class LoginProvidersResponse(
    val providers: List<LoginProviderDto>,
    val tokenLogin: Boolean,
    val setupRequired: Boolean,
)

@JsonSchema(requireNonNulls = false)
data class LoginRequest(
    val provider: String? = null,
    val username: String? = null,
    val password: String? = null,
)

@JsonSchema(requireNonNulls = false)
data class LoginPrincipalDto(
    val id: Int,
    val kind: String,
    val name: String,
    val displayName: String,
)

/**
 * Result of a successful password login. The session cookies are set on the same response; [expiresAt] is when the
 * session token expires, after which the client renews it. [mustChangePassword] asks for a password change first.
 */
@JsonSchema(requireNonNulls = false)
data class LoginResponse(
    val principal: LoginPrincipalDto,
    val mustChangePassword: Boolean,
    val expiresAt: Instant,
)

/** Result of a renewed session, `POST /api/auth/refresh`. */
@JsonSchema(requireNonNulls = false)
data class SessionResponse(
    val principal: LoginPrincipalDto,
    val expiresAt: Instant,
)
