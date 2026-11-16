package net.onelitefeather.ingot.identity.login

import com.reposilite.audit.api.AuditAction
import com.reposilite.audit.api.AuditEvent
import com.reposilite.auth.SignInObserver
import com.reposilite.auth.SignInOutcome
import com.reposilite.auth.SignInPath
import com.reposilite.configuration.local.infrastructure.DatabaseConnectionFactory
import com.reposilite.plugin.api.Event
import com.reposilite.plugin.api.LoginThrottledEvent
import com.reposilite.plugin.api.NewSignInEvent
import net.onelitefeather.ingot.identity.login.LoginOutcome.Blocked
import net.onelitefeather.ingot.identity.login.LoginOutcome.Succeeded
import net.onelitefeather.ingot.identity.login.storage.LoginStore
import net.onelitefeather.ingot.identity.model.PrincipalKind
import net.onelitefeather.ingot.identity.storage.IdentityStore
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.entry
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import panda.std.reactive.Reference
import java.nio.file.Path
import java.time.Duration
import java.time.Instant

/**
 * The dashboard login with the account delay and the sign-in observation: a delayed login is audited once as
 * `LOGIN_THROTTLED` with the dashboard path, counted per outcome, and announced; a first sign-in is announced as new.
 */
internal class LoginServiceSignInObservationTest {

    private val clock = MutableClock(Instant.parse("2026-10-15T10:00:00Z"))
    private val password = "correct horse battery"
    private val settings = LoginSettings(maxFailedAttempts = 100, accountThrottle = AccountThrottleSettings(threshold = 3))
    private val home = "10.0.0.1"
    private val elsewhere = "10.0.0.9"

    @TempDir
    lateinit var workingDirectory: Path

    private val audited = mutableListOf<AuditEvent>()
    private val published = mutableListOf<Event>()
    private val observer = SignInObserver(
        enabled = true,
        audit = { audited += it },
        publish = { published += it },
        warn = {},
    )

    private fun withService(block: (LoginService, IdentityStore) -> Unit) {
        DatabaseConnectionFactory.createConnection(workingDirectory, "sqlite --temporary", 1).use { connection ->
            val store = LoginStore(connection.database, clock)
            val identities = IdentityStore(connection.database, clock)
            val service = LoginService(
                identities = identities,
                credentials = store,
                hasher = PasswordHasher(Argon2Cost(memoryKib = 8, iterations = 1, parallelism = 1)),
                settings = Reference.reference(settings),
                clock = clock,
                audit = { audited += it },
                accountThrottle = AccountThrottle(store, Reference.reference(settings), clock, enabled = true),
                signInObserver = observer,
            )
            val principal = identities.createPrincipal(PrincipalKind.USER, "alice", "alice", false, clock.instant())!!
            service.setPassword(principal, password, mustChange = false)
            block(service, identities)
        }
    }

    private fun LoginService.login(username: String, secret: String, ip: String) =
        login(LocalLoginProvider.ID, username, secret, ip)

    @Test
    fun `a login delayed by the account key is audited once as LOGIN_THROTTLED and not as LOGIN_FAILED`() {
        withService { service, _ ->
            repeat(3) { service.login("alice", "wrong guess", home) }
            audited.clear()

            val outcome = service.login("alice", password, elsewhere)

            assertThat(outcome).isEqualTo(Blocked(retryAfterSeconds = 1))
            assertThat(audited.map { it.action }).containsExactly(AuditAction.LOGIN_THROTTLED)
            assertThat(audited.single().details).containsExactly(entry("path", "dashboard"))
            assertThat(audited.toString()).doesNotContain(password)
        }
    }

    @Test
    fun `a delayed login is announced with the name, the address and the dashboard path`() {
        withService { service, _ ->
            repeat(3) { service.login("alice", "wrong guess", home) }
            published.clear()

            service.login("alice", password, elsewhere)

            assertThat(published).containsExactly(LoginThrottledEvent(name = "alice", address = elsewhere, path = "dashboard", retryAfterSeconds = 1))
        }
    }

    @Test
    fun `counts each dashboard outcome on its own`() {
        withService { service, _ ->
            repeat(3) { service.login("alice", "wrong guess", home) }
            service.login("alice", password, elsewhere)

            clock.advance(Duration.ofSeconds(1))
            assertThat(service.login("alice", password, home)).isInstanceOf(Succeeded::class.java)

            assertThat(observer.counters.count(SignInPath.DASHBOARD, SignInOutcome.FAILED)).isEqualTo(3)
            assertThat(observer.counters.count(SignInPath.DASHBOARD, SignInOutcome.THROTTLED)).isEqualTo(1)
            assertThat(observer.counters.count(SignInPath.DASHBOARD, SignInOutcome.SUCCEEDED)).isEqualTo(1)
        }
    }

    @Test
    fun `announces the first sign-in from an address as a new sign-in`() {
        withService { service, _ ->
            published.clear()

            assertThat(service.login("alice", password, home)).isInstanceOf(Succeeded::class.java)

            assertThat(published.filterIsInstance<NewSignInEvent>().map { it.address to it.path }).containsExactly(home to "dashboard")
        }
    }

}
