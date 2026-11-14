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

package net.onelitefeather.ingot.plugin.store

import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openpgp.PGPObjectFactory
import org.bouncycastle.openpgp.PGPPublicKey
import org.bouncycastle.openpgp.PGPPublicKeyRing
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection
import org.bouncycastle.openpgp.PGPSignature
import org.bouncycastle.openpgp.PGPSignatureList
import org.bouncycastle.openpgp.PGPUtil
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentVerifierBuilderProvider
import java.io.ByteArrayInputStream
import java.time.Clock

/** The outcome of checking one detached signature. A refusal names the reason, which the status shows. */
sealed interface SignatureOutcome {

    /** The content is signed by a trusted key, whose fingerprint is [fingerprint] in upper-case hexadecimal. */
    data class Verified(val fingerprint: String) : SignatureOutcome

    data class Refused(val reason: String) : SignatureOutcome

}

/**
 * Checks a detached OpenPGP signature against the trusted keys of the instance. A signature verifies only when its key is
 * one of [keyRings] and that key has not ended its validity at the time of [clock]. The keys decide the trust, so the
 * signature file and the content are never trusted by themselves.
 */
class PluginSignatureVerifier(
    keyRings: List<PGPPublicKeyRing>,
    private val clock: Clock,
) {

    private val keysById: Map<Long, PGPPublicKey> =
        keyRings.asSequence().flatMap { it.publicKeys.asSequence() }.associateBy { it.keyID }

    private val verifierProvider = JcaPGPContentVerifierBuilderProvider().setProvider(BouncyCastleProvider())

    fun verify(content: ByteArray, signature: ByteArray): SignatureOutcome {
        val signatures = readSignatures(signature)
        if (signatures.isNullOrEmpty()) {
            return SignatureOutcome.Refused("the file is not an OpenPGP signature")
        }

        var reason = "no signature of the content verifies against a trusted key"
        for (candidate in signatures) {
            val key = keysById[candidate.keyID]
            if (key == null) {
                reason = "the signing key ${hexKeyId(candidate.keyID)} is not trusted"
                continue
            }
            if (hasEnded(key)) {
                reason = "the signing key ${fingerprint(key)} has expired"
                continue
            }
            try {
                candidate.init(verifierProvider, key)
                candidate.update(content)
                if (candidate.verify()) {
                    return SignatureOutcome.Verified(fingerprint(key))
                }
                reason = "the signature does not match the content"
            } catch (exception: Exception) {
                reason = "the signature cannot be checked: ${exception.message}"
            }
        }
        return SignatureOutcome.Refused(reason)
    }

    private fun readSignatures(bytes: ByteArray): List<PGPSignature>? =
        try {
            val factory = PGPObjectFactory(PGPUtil.getDecoderStream(ByteArrayInputStream(bytes)), JcaKeyFingerprintCalculator())
            (factory.nextObject() as? PGPSignatureList)?.toList()
        } catch (exception: Exception) {
            null
        }

    private fun hasEnded(key: PGPPublicKey): Boolean {
        val validSeconds = key.validSeconds
        return validSeconds > 0 && key.creationTime.toInstant().plusSeconds(validSeconds) <= clock.instant()
    }

    companion object {

        /** Reads every key ring of an ASCII-armoured or binary OpenPGP key file. Fails when the bytes hold no key. */
        fun readKeyRings(bytes: ByteArray): List<PGPPublicKeyRing> =
            PGPPublicKeyRingCollection(PGPUtil.getDecoderStream(ByteArrayInputStream(bytes)), JcaKeyFingerprintCalculator())
                .keyRings.asSequence().toList()

        internal fun fingerprint(key: PGPPublicKey): String =
            key.fingerprint.joinToString("") { "%02X".format(it) }

        private fun hexKeyId(keyId: Long): String =
            java.lang.Long.toHexString(keyId).uppercase().padStart(16, '0')

    }

}
