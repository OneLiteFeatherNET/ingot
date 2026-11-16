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

import com.reposilite.audit.api.AuditAction
import com.reposilite.audit.api.AuditActor
import com.reposilite.audit.api.AuditEvent
import com.webauthn4j.WebAuthnManager
import com.webauthn4j.authenticator.AuthenticatorImpl
import com.webauthn4j.converter.AttestedCredentialDataConverter
import com.webauthn4j.converter.util.ObjectConverter
import com.webauthn4j.data.AuthenticationParameters
import com.webauthn4j.data.AuthenticationRequest
import com.webauthn4j.data.PublicKeyCredentialParameters
import com.webauthn4j.data.PublicKeyCredentialType
import com.webauthn4j.data.RegistrationParameters
import com.webauthn4j.data.RegistrationRequest
import com.webauthn4j.data.attestation.statement.COSEAlgorithmIdentifier
import com.webauthn4j.data.attestation.statement.NoneAttestationStatement
import com.webauthn4j.data.client.Origin
import com.webauthn4j.data.client.challenge.DefaultChallenge
import com.webauthn4j.server.ServerProperty
import com.webauthn4j.util.exception.WebAuthnException
import com.webauthn4j.verifier.exception.MaliciousCounterValueException
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.UUID
import net.onelitefeather.ingot.identity.login.storage.PasskeyRow
import net.onelitefeather.ingot.identity.login.storage.SecondFactorStore
import net.onelitefeather.ingot.identity.login.storage.SecondFactorStore.Companion.KIND_PASSKEY
import net.onelitefeather.ingot.identity.login.storage.SecondFactorStore.Companion.KIND_WEBAUTHN_LOGIN
import net.onelitefeather.ingot.identity.login.storage.SecondFactorStore.Companion.KIND_WEBAUTHN_REGISTER
import net.onelitefeather.ingot.identity.storage.IdentityStore

/** A passkey ceremony lives this long. It is spent by the first completion, successful or not. */
internal val PASSKEY_CHALLENGE_LIFETIME: Duration = Duration.ofMinutes(2)

/** The name a passkey carries until the user renames it. */
internal const val PASSKEY_NAME = "Passkey"

/**
 * A started ceremony. [challengeId] is the handle the client returns, [challenge] the base64url bytes it signs. For an
 * assertion, [allowedCredentialIds] lists the passkeys of the principal; for a registration it is empty.
 */
data class PasskeyChallenge(
    val challengeId: String,
    val challenge: String,
    val allowedCredentialIds: List<String>,
)

sealed interface PasskeyRegistration {

    data class Registered(val factorId: Int) : PasskeyRegistration

    /**
     * The challenge is unknown, expired, spent or of another principal, the origin or relying party does not match, or the
     * attestation is not valid. The reason is not told to the client.
     */
    data object Refused : PasskeyRegistration

}

sealed interface PasskeyAssertion {

    data class Verified(val factorId: Int) : PasskeyAssertion

    /** The credential is not one of the principal, or the signature or the client data does not verify. */
    data object WrongAssertion : PasskeyAssertion

    /** The challenge is unknown, expired, spent or of another principal, or the principal is disabled. */
    data object Gone : PasskeyAssertion

    /** The signature counter did not increase while one of the counters is non-zero. The stored counter is unchanged. */
    data object CounterRefused : PasskeyAssertion

}

/**
 * Passkeys as a second factor, and as a first factor once `login.mfa.passkeyFirstFactor` allows it. This class runs the WebAuthn
 * ceremonies: it issues single-use challenges, verifies attestations and assertions with WebAuthn4J, and applies the signature
 * counter rule. The relying party identifier and the origin are the caller's; every ceremony checks them against the client data.
 *
 * Every state change is a database row, so a challenge started on one replica can be finished on another. Each conditional write
 * is part of its statement, as in [SecondFactorService].
 */
internal class PasskeyService(
    private val store: SecondFactorStore,
    private val identities: IdentityStore,
    private val clock: Clock,
    private val audit: (AuditEvent) -> Unit,
    private val random: SecureRandom = SecureRandom(),
) {

    private val objectConverter = ObjectConverter()
    private val manager: WebAuthnManager = WebAuthnManager.createNonStrictWebAuthnManager(objectConverter)
    private val credentialData = AttestedCredentialDataConverter(objectConverter)

    /** Starts a passkey registration for [principalId]. */
    fun startRegistration(principalId: Int): PasskeyChallenge =
        issue(principalId, KIND_WEBAUTHN_REGISTER, allowedCredentialIds = emptyList())

    /** Starts an assertion for [principalId]. The allowed credentials are the passkeys of that principal. */
    fun startAssertion(principalId: Int): PasskeyChallenge =
        issue(principalId, KIND_WEBAUTHN_LOGIN, allowedCredentialIds = store.findPasskeys(principalId).map { it.credentialId })

    /**
     * Completes a registration. The attestation must be in the `none` format, with user presence and an ES256 key. The passkey is
     * stored with its credential id, its attested credential data and its initial signature counter.
     */
    fun finishRegistration(
        principalId: Int,
        challengeId: String,
        clientDataJson: ByteArray,
        attestationObject: ByteArray,
        rpId: String,
        origin: String,
    ): PasskeyRegistration {
        val now = clock.instant()
        val challenge = spend(challengeId, KIND_WEBAUTHN_REGISTER, principalId, now) ?: return PasskeyRegistration.Refused
        val principal = identities.findPrincipalById(principalId)?.takeIf { !it.disabled } ?: return PasskeyRegistration.Refused
        val data = try {
            manager.verify(
                RegistrationRequest(attestationObject, clientDataJson),
                RegistrationParameters(
                    serverProperty(origin, rpId, challenge),
                    listOf(PublicKeyCredentialParameters(PublicKeyCredentialType.PUBLIC_KEY, COSEAlgorithmIdentifier.ES256)),
                    false,
                    true,
                ),
            )
        } catch (_: WebAuthnException) {
            return PasskeyRegistration.Refused
        } catch (_: IllegalArgumentException) {
            return PasskeyRegistration.Refused
        }
        val authenticatorData = checkNotNull(data.attestationObject).authenticatorData
        val attested = checkNotNull(authenticatorData.attestedCredentialData)
        val credentialId = base64Url(attested.credentialId)
        if (store.findPasskey(credentialId) != null) {
            return PasskeyRegistration.Refused
        }
        val factorId = store.insertPasskey(
            principalId = principalId,
            factorKey = UUID.randomUUID().toString(),
            name = PASSKEY_NAME,
            credentialId = credentialId,
            publicKey = credentialData.convert(attested),
            signCount = authenticatorData.signCount,
            at = now,
        )
        audit(AuditEvent(
            action = AuditAction.MFA_FACTOR_ENROLLED,
            actor = AuditActor.named(principal.name, null),
            details = mapOf("principal" to principal.name, "kind" to KIND_PASSKEY),
        ))
        return PasskeyRegistration.Registered(factorId)
    }

    /**
     * Completes an assertion of [principalId] with the passkey [credentialId] (base64url). The signature is verified by WebAuthn4J.
     * A passkey with a non-zero counter on either side must present a counter above the stored one. A zero counter on both sides
     * passes, because the authenticator does not count. A refused counter is audited and leaves the stored counter as it was.
     */
    fun finishAssertion(
        principalId: Int,
        challengeId: String,
        credentialId: String,
        clientDataJson: ByteArray,
        authenticatorData: ByteArray,
        signature: ByteArray,
        rpId: String,
        origin: String,
    ): PasskeyAssertion {
        val now = clock.instant()
        val challenge = spend(challengeId, KIND_WEBAUTHN_LOGIN, principalId, now) ?: return PasskeyAssertion.Gone
        val principal = identities.findPrincipalById(principalId)?.takeIf { !it.disabled } ?: return PasskeyAssertion.Gone
        val passkey = findOwnPasskey(principalId, credentialId) ?: return PasskeyAssertion.WrongAssertion

        val verified = try {
            val authenticator = AuthenticatorImpl(credentialData.convert(passkey.publicKey), NoneAttestationStatement(), passkey.signCount)
            manager.verify(
                AuthenticationRequest(base64UrlDecode(passkey.credentialId), authenticatorData, clientDataJson, signature),
                AuthenticationParameters(
                    serverProperty(origin, rpId, challenge),
                    authenticator,
                    listOf(base64UrlDecode(passkey.credentialId)),
                    false,
                    true,
                ),
            )
        } catch (_: MaliciousCounterValueException) {
            return refuseCounter(principal.name)
        } catch (_: WebAuthnException) {
            return PasskeyAssertion.WrongAssertion
        } catch (_: IllegalArgumentException) {
            return PasskeyAssertion.WrongAssertion
        }

        val presented = checkNotNull(verified.authenticatorData).signCount
        val stored = passkey.signCount
        if ((stored != 0L || presented != 0L) && presented <= stored) {
            return refuseCounter(principal.name)
        }
        if (!store.claimSignCount(passkey.id, stored, presented, now)) {
            return refuseCounter(principal.name)
        }
        return PasskeyAssertion.Verified(passkey.id)
    }

    private fun issue(principalId: Int, kind: String, allowedCredentialIds: List<String>): PasskeyChallenge {
        val now = clock.instant()
        val id = ByteArray(CHALLENGE_ID_BYTES).also { random.nextBytes(it) }
        val challenge = ByteArray(CHALLENGE_BYTES).also { random.nextBytes(it) }
        val encodedId = base64Url(id)
        store.insertChallenge(hashOf(encodedId), principalId, now, now.plus(PASSKEY_CHALLENGE_LIFETIME), kind, challenge)
        return PasskeyChallenge(challengeId = encodedId, challenge = base64Url(challenge), allowedCredentialIds = allowedCredentialIds)
    }

    /**
     * Spends the challenge [challengeId] for [kind] and [principalId] and returns its bytes. Only the first completion of a
     * challenge gets them: an unknown, expired, foreign or already spent challenge gives `null`.
     */
    private fun spend(challengeId: String, kind: String, principalId: Int, now: Instant): ByteArray? {
        val row = store.findChallenge(hashOf(challengeId))
            ?.takeIf { it.kind == kind && it.principalId == principalId && it.usedAt == null && it.expiresAt.isAfter(now) }
            ?: return null
        val challenge = row.challenge ?: return null
        return challenge.takeIf { store.claimChallenge(row.idHash, now) }
    }

    /** The passkey with [credentialId] when it belongs to [principalId]. The id is normalised before the lookup. */
    private fun findOwnPasskey(principalId: Int, credentialId: String): PasskeyRow? {
        val canonical = runCatching { base64UrlDecode(credentialId) }.getOrNull()?.let { base64Url(it) } ?: return null
        return store.findPasskey(canonical)?.takeIf { it.principalId == principalId }
    }

    private fun refuseCounter(accountName: String): PasskeyAssertion {
        audit(AuditEvent(
            action = AuditAction.MFA_CHALLENGE_FAILED,
            actor = AuditActor.named(accountName, null),
            details = mapOf("context" to "passkey"),
            success = false,
        ))
        return PasskeyAssertion.CounterRefused
    }

    private fun serverProperty(origin: String, rpId: String, challenge: ByteArray): ServerProperty =
        ServerProperty(Origin.create(origin), rpId, DefaultChallenge(challenge))

    private fun hashOf(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { "%02x".format(it) }

    private companion object {
        const val CHALLENGE_ID_BYTES = 32
        const val CHALLENGE_BYTES = 32

        fun base64Url(value: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(value)

        fun base64UrlDecode(value: String): ByteArray = Base64.getUrlDecoder().decode(value)
    }

}
