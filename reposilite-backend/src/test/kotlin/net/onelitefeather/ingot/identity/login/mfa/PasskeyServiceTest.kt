package net.onelitefeather.ingot.identity.login.mfa

import com.reposilite.audit.api.AuditAction
import com.reposilite.audit.api.AuditEvent
import com.reposilite.configuration.local.infrastructure.DatabaseConnectionFactory
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.Base64
import net.onelitefeather.ingot.identity.login.MutableClock
import net.onelitefeather.ingot.identity.login.storage.SecondFactorStore
import net.onelitefeather.ingot.identity.model.PrincipalKind
import net.onelitefeather.ingot.identity.storage.IdentityStore
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

internal class PasskeyServiceTest {

    private val clock = MutableClock(Instant.parse("2026-11-14T10:00:00Z"))

    @TempDir
    lateinit var workingDirectory: Path

    private class Fixture(
        val service: PasskeyService,
        val store: SecondFactorStore,
        val principalId: Int,
        val audit: MutableList<AuditEvent>,
    )

    private fun withFixture(block: (Fixture) -> Unit) {
        DatabaseConnectionFactory.createConnection(workingDirectory, "sqlite --temporary", 1).use { connection ->
            val identities = IdentityStore(connection.database, clock)
            val principal = identities.createPrincipal(PrincipalKind.USER, "alice", "Alice", false, clock.instant())!!
            val store = SecondFactorStore(connection.database, clock)
            val audit = mutableListOf<AuditEvent>()
            val service = PasskeyService(store = store, identities = identities, clock = clock, audit = audit::add)
            block(Fixture(service, store, principal.id, audit))
        }
    }

    /** Runs a complete registration with [passkey] and returns it, so that assertions can follow. */
    private fun Fixture.register(passkey: SoftwarePasskey = SoftwarePasskey(), signCount: Long = 0): SoftwarePasskey {
        val started = service.startRegistration(principalId)
        val attestation = passkey.registration(challenge = started.challenge, signCount = signCount)
        val result = service.finishRegistration(principalId, started.challengeId, attestation.clientDataJson, attestation.attestationObject, RP_ID, ORIGIN)
        assertThat(result).`as`("registration of the test passkey").isInstanceOf(PasskeyRegistration.Registered::class.java)
        return passkey
    }

    private fun Fixture.authenticate(passkey: SoftwarePasskey, signCount: Long): PasskeyAssertion {
        val started = service.startAssertion(principalId)
        val assertion = passkey.assertion(challenge = started.challenge, signCount = signCount)
        return service.finishAssertion(
            principalId = principalId,
            challengeId = started.challengeId,
            credentialId = passkey.credentialIdBase64Url,
            clientDataJson = assertion.clientDataJson,
            authenticatorData = assertion.authenticatorData,
            signature = assertion.signature,
            rpId = RP_ID,
            origin = ORIGIN,
        )
    }

    private fun Fixture.storedCounter(): Long =
        store.findPasskeys(principalId).single().signCount

    @Test
    fun `a passkey registration with a fixed ES256 key stores the credential id and the signature counter`() {
        withFixture { fixture ->
            val passkey = SoftwarePasskey()
            val started = fixture.service.startRegistration(fixture.principalId)
            val attestation = passkey.registration(challenge = started.challenge, signCount = 3)

            val result = fixture.service.finishRegistration(fixture.principalId, started.challengeId, attestation.clientDataJson, attestation.attestationObject, RP_ID, ORIGIN)

            assertThat(result).isInstanceOf(PasskeyRegistration.Registered::class.java)
            val stored = fixture.store.findPasskeys(fixture.principalId).single()
            assertThat(stored.credentialId).`as`("credential id as base64url").isEqualTo(passkey.credentialIdBase64Url)
            assertThat(stored.signCount).`as`("signature counter sent by the authenticator").isEqualTo(3L)
        }
    }

    @Test
    fun `a passkey registration from another origin is refused`() {
        withFixture { fixture ->
            val started = fixture.service.startRegistration(fixture.principalId)
            val attestation = SoftwarePasskey().registration(challenge = started.challenge, origin = "https://evil.example.test")

            val result = fixture.service.finishRegistration(fixture.principalId, started.challengeId, attestation.clientDataJson, attestation.attestationObject, RP_ID, ORIGIN)

            assertThat(result).`as`("registration whose origin is not the configured one").isEqualTo(PasskeyRegistration.Refused)
            assertThat(fixture.store.findPasskeys(fixture.principalId)).`as`("no passkey is stored").isEmpty()
        }
    }

    @Test
    fun `a passkey registration for another relying party identifier is refused`() {
        withFixture { fixture ->
            val started = fixture.service.startRegistration(fixture.principalId)
            val attestation = SoftwarePasskey().registration(challenge = started.challenge, rpId = "evil.example.test")

            val result = fixture.service.finishRegistration(fixture.principalId, started.challengeId, attestation.clientDataJson, attestation.attestationObject, RP_ID, ORIGIN)

            assertThat(result).`as`("registration whose rpIdHash is not the configured relying party").isEqualTo(PasskeyRegistration.Refused)
            assertThat(fixture.store.findPasskeys(fixture.principalId)).`as`("no passkey is stored").isEmpty()
        }
    }

    @Test
    fun `a registration challenge can be spent only once`() {
        withFixture { fixture ->
            val started = fixture.service.startRegistration(fixture.principalId)
            val first = SoftwarePasskey().registration(challenge = started.challenge)
            val second = SoftwarePasskey(credentialId = ByteArray(16) { 0x55 }).registration(challenge = started.challenge)
            fixture.service.finishRegistration(fixture.principalId, started.challengeId, first.clientDataJson, first.attestationObject, RP_ID, ORIGIN)

            val result = fixture.service.finishRegistration(fixture.principalId, started.challengeId, second.clientDataJson, second.attestationObject, RP_ID, ORIGIN)

            assertThat(result).`as`("second completion of the same challenge").isEqualTo(PasskeyRegistration.Refused)
            assertThat(fixture.store.findPasskeys(fixture.principalId)).`as`("only the first passkey is stored").hasSize(1)
        }
    }

    @Test
    fun `a registration challenge is accepted just before two minutes`() {
        withFixture { fixture ->
            val started = fixture.service.startRegistration(fixture.principalId)
            val attestation = SoftwarePasskey().registration(challenge = started.challenge)
            clock.advance(Duration.ofSeconds(119))

            val result = fixture.service.finishRegistration(fixture.principalId, started.challengeId, attestation.clientDataJson, attestation.attestationObject, RP_ID, ORIGIN)

            assertThat(result).`as`("completion at 119 seconds").isInstanceOf(PasskeyRegistration.Registered::class.java)
        }
    }

    @Test
    fun `a registration challenge is refused at two minutes`() {
        withFixture { fixture ->
            val started = fixture.service.startRegistration(fixture.principalId)
            val attestation = SoftwarePasskey().registration(challenge = started.challenge)
            clock.advance(Duration.ofSeconds(120))

            val result = fixture.service.finishRegistration(fixture.principalId, started.challengeId, attestation.clientDataJson, attestation.attestationObject, RP_ID, ORIGIN)

            assertThat(result).`as`("completion at 120 seconds").isEqualTo(PasskeyRegistration.Refused)
        }
    }

    @Test
    fun `a registration starts with an opaque user handle of 32 random bytes`() {
        withFixture { fixture ->
            val started = fixture.service.startRegistration(fixture.principalId)

            val handle = Base64.getUrlDecoder().decode(checkNotNull(started.userHandle) { "registration options without a user handle" })

            assertThat(handle).`as`("user handle bytes").hasSize(32)
        }
    }

    @Test
    fun `the user handle of a registration does not contain the account name`() {
        withFixture { fixture ->
            val started = fixture.service.startRegistration(fixture.principalId)

            val handle = Base64.getUrlDecoder().decode(checkNotNull(started.userHandle) { "registration options without a user handle" })

            assertThat(String(handle, Charsets.ISO_8859_1)).`as`("user handle bytes").doesNotContain("alice")
        }
    }

    @Test
    fun `the user handle of an account stays the same across registrations`() {
        withFixture { fixture ->
            val first = fixture.service.startRegistration(fixture.principalId).userHandle
            val second = fixture.service.startRegistration(fixture.principalId).userHandle

            assertThat(second).`as`("user handle of the second registration").isEqualTo(first)
        }
    }

    @Test
    fun `an earlier passkey still signs in after a later registration returns the account's user handle`() {
        withFixture { fixture ->
            val passkey = fixture.register(signCount = 0)
            fixture.service.startRegistration(fixture.principalId).userHandle

            val result = fixture.authenticate(passkey, signCount = 1)

            assertThat(result).`as`("assertion of the passkey registered earlier").isInstanceOf(PasskeyAssertion.Verified::class.java)
        }
    }

    @Test
    fun `an assertion with a valid signature succeeds`() {
        withFixture { fixture ->
            val passkey = fixture.register(signCount = 0)

            val result = fixture.authenticate(passkey, signCount = 1)

            assertThat(result).isInstanceOf(PasskeyAssertion.Verified::class.java)
        }
    }

    @Test
    fun `an assertion with a bad signature is refused`() {
        withFixture { fixture ->
            val passkey = fixture.register(signCount = 0)
            val started = fixture.service.startAssertion(fixture.principalId)
            val assertion = passkey.assertion(challenge = started.challenge, signCount = 1)
            val tampered = assertion.signature.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 0x01).toByte() }

            val result = fixture.service.finishAssertion(
                principalId = fixture.principalId,
                challengeId = started.challengeId,
                credentialId = passkey.credentialIdBase64Url,
                clientDataJson = assertion.clientDataJson,
                authenticatorData = assertion.authenticatorData,
                signature = tampered,
                rpId = RP_ID,
                origin = ORIGIN,
            )

            assertThat(result).`as`("assertion whose signature does not match").isEqualTo(PasskeyAssertion.WrongAssertion)
        }
    }

    @Test
    fun `zero counters on both sides pass and keep the stored counter at zero`() {
        withFixture { fixture ->
            val passkey = fixture.register(signCount = 0)

            val result = fixture.authenticate(passkey, signCount = 0)

            assertThat(result).`as`("assertion with counter zero against stored zero").isInstanceOf(PasskeyAssertion.Verified::class.java)
            assertThat(fixture.storedCounter()).`as`("stored counter after the assertion").isZero()
        }
    }

    @Test
    fun `an increasing counter after a zero counter passes and stores the presented value`() {
        withFixture { fixture ->
            val passkey = fixture.register(signCount = 0)

            val result = fixture.authenticate(passkey, signCount = 5)

            assertThat(result).`as`("assertion with counter 5 against stored zero").isInstanceOf(PasskeyAssertion.Verified::class.java)
            assertThat(fixture.storedCounter()).`as`("stored counter after the assertion").isEqualTo(5L)
        }
    }

    @Test
    fun `an increasing counter after a non-zero counter passes and stores the presented value`() {
        withFixture { fixture ->
            val passkey = fixture.register(signCount = 4)

            val result = fixture.authenticate(passkey, signCount = 9)

            assertThat(result).`as`("assertion with counter 9 against stored 4").isInstanceOf(PasskeyAssertion.Verified::class.java)
            assertThat(fixture.storedCounter()).`as`("stored counter after the assertion").isEqualTo(9L)
        }
    }

    @Test
    fun `an equal non-zero counter is refused and leaves the stored counter unchanged`() {
        withFixture { fixture ->
            val passkey = fixture.register(signCount = 4)

            val result = fixture.authenticate(passkey, signCount = 4)

            assertThat(result).`as`("assertion with counter 4 against stored 4").isEqualTo(PasskeyAssertion.CounterRefused)
            assertThat(fixture.storedCounter()).`as`("stored counter after the refused assertion").isEqualTo(4L)
        }
    }

    @Test
    fun `a decreasing counter is refused and leaves the stored counter unchanged`() {
        withFixture { fixture ->
            val passkey = fixture.register(signCount = 4)

            val result = fixture.authenticate(passkey, signCount = 2)

            assertThat(result).`as`("assertion with counter 2 against stored 4").isEqualTo(PasskeyAssertion.CounterRefused)
            assertThat(fixture.storedCounter()).`as`("stored counter after the refused assertion").isEqualTo(4L)
        }
    }

    @Test
    fun `a refused counter writes an MFA_CHALLENGE_FAILED audit event with the passkey context`() {
        withFixture { fixture ->
            val passkey = fixture.register(signCount = 4)
            fixture.audit.clear()

            fixture.authenticate(passkey, signCount = 4)

            val failures = fixture.audit.filter { it.action == AuditAction.MFA_CHALLENGE_FAILED }
            assertThat(failures).`as`("audit events of the refused counter").hasSize(1)
            assertThat(failures.single().details).`as`("details of the audit event").containsEntry("context", "passkey")
        }
    }

}
