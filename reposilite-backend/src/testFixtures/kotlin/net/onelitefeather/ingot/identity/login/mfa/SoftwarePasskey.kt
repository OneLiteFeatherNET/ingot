package net.onelitefeather.ingot.identity.login.mfa

import java.math.BigInteger
import java.nio.ByteBuffer
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPrivateKeySpec
import java.util.Base64

const val RP_ID = "ingot.example.test"
const val ORIGIN = "https://ingot.example.test"

/**
 * A software authenticator for the passkey tests. The key is the fixed P-256 pair of RFC 6979 appendix A.2.5, so every run
 * signs with the same key. The WebAuthn structures are written by hand here, so the tests do not depend on the code under test.
 */
class SoftwarePasskey(
    val credentialId: ByteArray = ByteArray(CREDENTIAL_ID_BYTES) { (0xA0 + it).toByte() },
) {

    val credentialIdBase64Url: String = base64Url(credentialId)

    private val curve: ECParameterSpec = AlgorithmParameters.getInstance("EC")
        .apply { init(ECGenParameterSpec("secp256r1")) }
        .getParameterSpec(ECParameterSpec::class.java)

    private val privateKey = KeyFactory.getInstance("EC")
        .generatePrivate(ECPrivateKeySpec(BigInteger(PRIVATE_SCALAR, 16), curve))

    /** The attestation of a registration ceremony in the `none` format, with user presence and an attested credential. */
    fun registration(challenge: String, signCount: Long = 0, rpId: String = RP_ID, origin: String = ORIGIN): Registration {
        val clientData = clientData("webauthn.create", challenge, origin)
        val authenticatorData = authenticatorData(
            rpId = rpId,
            flags = FLAG_USER_PRESENT or FLAG_ATTESTED_CREDENTIAL,
            signCount = signCount,
            attested = attestedCredentialData(),
        )
        val attestationObject = CborWriter.map(
            CborWriter.text("fmt") to CborWriter.text("none"),
            CborWriter.text("attStmt") to CborWriter.map(),
            CborWriter.text("authData") to CborWriter.bytes(authenticatorData),
        )
        return Registration(clientData, attestationObject)
    }

    /** The assertion of an authentication ceremony: user presence only, signed with the fixed key. */
    fun assertion(challenge: String, signCount: Long = 0, rpId: String = RP_ID, origin: String = ORIGIN): Assertion {
        val clientData = clientData("webauthn.get", challenge, origin)
        val authenticatorData = authenticatorData(rpId = rpId, flags = FLAG_USER_PRESENT, signCount = signCount, attested = null)
        val signature = Signature.getInstance("SHA256withECDSA").run {
            initSign(privateKey)
            update(authenticatorData)
            update(sha256(clientData))
            sign()
        }
        return Assertion(clientData, authenticatorData, signature)
    }

    private fun clientData(type: String, challenge: String, origin: String): ByteArray =
        """{"type":"$type","challenge":"$challenge","origin":"$origin","crossOrigin":false}""".toByteArray(Charsets.UTF_8)

    private fun authenticatorData(rpId: String, flags: Int, signCount: Long, attested: ByteArray?): ByteArray =
        sha256(rpId.toByteArray(Charsets.UTF_8)) +
            byteArrayOf(flags.toByte()) +
            ByteBuffer.allocate(Int.SIZE_BYTES).putInt(signCount.toInt()).array() +
            (attested ?: ByteArray(0))

    /** AAGUID (zeros), credential id length, credential id and the COSE key of the ES256 public key. */
    private fun attestedCredentialData(): ByteArray =
        ByteArray(AAGUID_BYTES) +
            ByteBuffer.allocate(Short.SIZE_BYTES).putShort(credentialId.size.toShort()).array() +
            credentialId +
            coseKey()

    private fun coseKey(): ByteArray = CborWriter.map(
        CborWriter.unsigned(1) to CborWriter.unsigned(2),
        CborWriter.unsigned(3) to CborWriter.negative(-7),
        CborWriter.negative(-1) to CborWriter.unsigned(1),
        CborWriter.negative(-2) to CborWriter.bytes(hexToBytes(PUBLIC_X)),
        CborWriter.negative(-3) to CborWriter.bytes(hexToBytes(PUBLIC_Y)),
    )

    class Registration(val clientDataJson: ByteArray, val attestationObject: ByteArray)

    class Assertion(val clientDataJson: ByteArray, val authenticatorData: ByteArray, val signature: ByteArray)

    private companion object {
        const val CREDENTIAL_ID_BYTES = 16
        const val AAGUID_BYTES = 16
        const val FLAG_USER_PRESENT = 0x01
        const val FLAG_ATTESTED_CREDENTIAL = 0x40
        const val PRIVATE_SCALAR = "C9AFA9D845BA75166B5C215767B1D6934E50C3DB36E89B127B8A622B120F6721"
        const val PUBLIC_X = "60FED4BA255A9D31C961EB74C6356D68C049B8923B61FA6CE669622E60F29FB6"
        const val PUBLIC_Y = "7903FE1008B8BC99A41AE9E95628BC64F2F1B20C2D7E9F5177A3C294D4462299"

        fun sha256(value: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(value)

        fun base64Url(value: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(value)

        fun hexToBytes(hex: String): ByteArray = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

}

/** The minimal CBOR encoder the passkey test data needs: integers, byte and text strings, arrays and maps. */
object CborWriter {

    private const val MAJOR_UNSIGNED = 0
    private const val MAJOR_NEGATIVE = 1
    private const val MAJOR_BYTES = 2
    private const val MAJOR_TEXT = 3
    private const val MAJOR_ARRAY = 4
    private const val MAJOR_MAP = 5

    fun unsigned(value: Long): ByteArray {
        require(value >= 0) { "use negative for $value" }
        return head(MAJOR_UNSIGNED, value)
    }

    fun negative(value: Long): ByteArray {
        require(value < 0) { "use unsigned for $value" }
        return head(MAJOR_NEGATIVE, -1 - value)
    }

    fun bytes(value: ByteArray): ByteArray = head(MAJOR_BYTES, value.size.toLong()) + value

    fun text(value: String): ByteArray {
        val raw = value.toByteArray(Charsets.UTF_8)
        return head(MAJOR_TEXT, raw.size.toLong()) + raw
    }

    fun array(vararg items: ByteArray): ByteArray =
        items.fold(head(MAJOR_ARRAY, items.size.toLong())) { encoded, item -> encoded + item }

    fun map(vararg entries: Pair<ByteArray, ByteArray>): ByteArray =
        entries.fold(head(MAJOR_MAP, entries.size.toLong())) { encoded, (key, value) -> encoded + key + value }

    private fun head(major: Int, argument: Long): ByteArray {
        val type = major shl 5
        return when {
            argument < 24 -> byteArrayOf((type or argument.toInt()).toByte())
            argument < 0x100 -> byteArrayOf((type or 24).toByte(), argument.toByte())
            argument < 0x10000 -> byteArrayOf((type or 25).toByte()) + ByteBuffer.allocate(Short.SIZE_BYTES).putShort(argument.toShort()).array()
            else -> byteArrayOf((type or 26).toByte()) + ByteBuffer.allocate(Int.SIZE_BYTES).putInt(argument.toInt()).array()
        }
    }

}
