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

/** The methods a user with an authenticator may complete a sign-in with. */
internal const val METHOD_AUTHENTICATOR = "AUTHENTICATOR"
internal const val METHOD_RECOVERY_CODE = "RECOVERY_CODE"

/** `POST /api/auth/mfa/verify`: the code of the authenticator app, or one recovery code. */
@JsonSchema(requireNonNulls = false)
data class SecondFactorRequest(
    val code: String? = null,
)

/**
 * The answer of a password login for a user with a second factor. No session cookie is set. The challenge cookie is, and
 * [methods] lists what the verification takes.
 */
@JsonSchema(requireNonNulls = false)
data class SecondFactorRequiredResponse(
    val mfaRequired: Boolean,
    val methods: List<String>,
)

/** A second factor as listed for its owner or an administrator. Never carries a secret. */
@JsonSchema(requireNonNulls = false)
data class SecondFactorDto(
    val id: Int,
    val kind: String,
    val name: String,
    val createdAt: Instant,
    val lastUsedAt: Instant? = null,
)

/** `POST /api/identity/me/factors/totp`: the enrolment, with the `otpauth` URI the authenticator app reads. Shown once. */
@JsonSchema(requireNonNulls = false)
data class AuthenticatorEnrolmentDto(
    val otpauthUri: String,
)

/** A code of the authenticator app, for confirming an enrolment or for a change that needs a fresh proof. */
@JsonSchema(requireNonNulls = false)
data class CodeRequest(
    val code: String? = null,
)

/** The factor that the confirmation created. [recoveryCodes] is set for the first factor only, and shown once. */
@JsonSchema(requireNonNulls = false)
data class ConfirmedEnrolmentDto(
    val recoveryCodes: List<String>? = null,
)

@JsonSchema(requireNonNulls = false)
data class RecoveryCodesDto(
    val recoveryCodes: List<String>,
)

/** The number of factors an administrator reset removed. */
@JsonSchema(requireNonNulls = false)
data class FactorResetDto(
    val removed: Int,
)
