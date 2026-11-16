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
import java.util.Base64

/** The methods a user may complete a sign-in with, as the login answer names them. */
internal const val METHOD_AUTHENTICATOR = "AUTHENTICATOR"
internal const val METHOD_RECOVERY_CODE = "RECOVERY_CODE"
internal const val METHOD_PASSKEY = "PASSKEY"

/**
 * `POST /api/auth/mfa/verify`: the code of the authenticator app, one recovery code, or a passkey assertion. A body with
 * [passkey] is a passkey sign-in, and a body with only [code] is a code sign-in as before.
 */
@JsonSchema(requireNonNulls = false)
data class SecondFactorRequest(
    val code: String? = null,
    val passkey: PasskeyAssertionDto? = null,
)

/**
 * The assertion of a passkey for a sign-in. [challengeId] is the id of the options that `POST /api/auth/mfa/passkey/options`
 * returned; the other fields are the authenticator's answer, in base64url.
 */
@JsonSchema(requireNonNulls = false)
data class PasskeyAssertionDto(
    val challengeId: String? = null,
    val credentialId: String? = null,
    val clientDataJSON: String? = null,
    val authenticatorData: String? = null,
    val signature: String? = null,
)

/**
 * The options of a passkey ceremony. [challengeId] is the handle to send back, [challenge] the bytes to sign, and [rpId] the
 * relying party. [allowCredentials] lists the passkeys that may answer an assertion, and is empty for a registration.
 */
@JsonSchema(requireNonNulls = false)
data class PasskeyOptionsDto(
    val challengeId: String,
    val challenge: String,
    val rpId: String,
    val allowCredentials: List<String> = emptyList(),
)

/** `POST /api/identity/me/factors/passkey`: the attestation of the browser, with the id of the registration options. */
@JsonSchema(requireNonNulls = false)
data class PasskeyRegistrationDto(
    val challengeId: String? = null,
    val clientDataJSON: String? = null,
    val attestationObject: String? = null,
)

/** The passkey that a registration created. */
@JsonSchema(requireNonNulls = false)
data class PasskeyRegisteredDto(
    val id: Int,
    val name: String,
)

/** Decodes a base64url field of a request. `null` when the field is missing, blank or not base64url. */
internal fun decodeBase64Url(value: String?): ByteArray? =
    value?.takeIf { it.isNotBlank() }?.let { runCatching { Base64.getUrlDecoder().decode(it) }.getOrNull() }

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

/**
 * The answer of a step-up: the authentication time of the renewed session and the end of its step-up window. The session
 * cookies of the answer carry the renewed session.
 */
@JsonSchema(requireNonNulls = false)
data class StepUpDto(
    val authenticatedAt: Instant,
    val stepUpUntil: Instant,
)
