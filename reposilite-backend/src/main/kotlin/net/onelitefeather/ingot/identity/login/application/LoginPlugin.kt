package net.onelitefeather.ingot.identity.login.application

import com.reposilite.audit.api.AuditEvent
import com.reposilite.auth.LdapAuthenticator
import com.reposilite.auth.application.AuthenticationSettings
import com.reposilite.configuration.ConfigurationFacade
import com.reposilite.configuration.local.LocalConfiguration
import com.reposilite.token.AccessTokenFacade
import com.reposilite.auth.AuthenticationFacade
import com.reposilite.auth.SignInObserver
import com.reposilite.configuration.shared.SharedConfigurationFacade
import com.reposilite.plugin.api.Facade
import com.reposilite.plugin.api.Plugin
import com.reposilite.plugin.api.ReposiliteInitializeEvent
import com.reposilite.plugin.api.ReposilitePlugin
import com.reposilite.plugin.event
import com.reposilite.plugin.facade
import com.reposilite.plugin.parameters
import com.reposilite.plugin.reposilite
import com.reposilite.web.api.RoutingSetupEvent
import net.onelitefeather.ingot.identity.login.AccountThrottle
import net.onelitefeather.ingot.identity.login.LoginAttemptPolicy
import net.onelitefeather.ingot.identity.login.SharedFailedAddressStore
import net.onelitefeather.ingot.identity.login.JdkBreachRangeClient
import net.onelitefeather.ingot.identity.login.LoginAttemptLimiter
import net.onelitefeather.ingot.identity.login.LoginFacade
import net.onelitefeather.ingot.identity.login.LoginProviderType
import net.onelitefeather.ingot.identity.login.LoginService
import net.onelitefeather.ingot.identity.login.LoginSettings
import net.onelitefeather.ingot.identity.login.OwnTokens
import net.onelitefeather.ingot.identity.login.PasswordBreachCheck
import net.onelitefeather.ingot.identity.login.PasswordHasher
import net.onelitefeather.ingot.identity.login.ProviderAnnouncement
import net.onelitefeather.ingot.identity.login.api.AuthLoginEndpoints
import net.onelitefeather.ingot.identity.login.api.ProviderTestEndpoints
import net.onelitefeather.ingot.identity.login.api.SecondFactorEndpoints
import net.onelitefeather.ingot.identity.login.api.SelfServiceEndpoints
import net.onelitefeather.ingot.identity.login.external.ExternalAccountService
import net.onelitefeather.ingot.identity.login.external.ExternalIdentityStore
import net.onelitefeather.ingot.identity.login.ldap.LdapDirectories
import net.onelitefeather.ingot.identity.login.mfa.PasskeyService
import net.onelitefeather.ingot.identity.login.mfa.SecondFactorPolicy
import net.onelitefeather.ingot.identity.login.mfa.SecondFactorSeedResealer
import net.onelitefeather.ingot.identity.login.mfa.SecondFactorService
import net.onelitefeather.ingot.identity.login.mfa.SettingsSeedCipher
import net.onelitefeather.ingot.identity.login.oidc.AuthOidcEndpoints
import net.onelitefeather.ingot.identity.login.oidc.IdTokenValidator
import net.onelitefeather.ingot.identity.login.oidc.JdkHttpFetcher
import net.onelitefeather.ingot.identity.login.oidc.OidcConnectionCheck
import net.onelitefeather.ingot.identity.login.oidc.OidcLoginService
import net.onelitefeather.ingot.identity.login.oidc.OidcMetadata
import net.onelitefeather.ingot.identity.login.oidc.OidcProviderCatalog
import net.onelitefeather.ingot.identity.login.oidc.OidcStateCookie
import net.onelitefeather.ingot.identity.login.storage.LoginStore
import net.onelitefeather.ingot.identity.login.storage.SecondFactorStore
import net.onelitefeather.ingot.identity.session.SessionService
import net.onelitefeather.ingot.identity.session.SessionSigner
import net.onelitefeather.ingot.identity.storage.IdentityStore
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.util.Base64

@Plugin(
    name = "login",
    dependencies = ["access-token", "authentication", "failure", "configuration", "shared-configuration", "audit", "local-configuration"],
    settings = LoginSettings::class,
)
class LoginPlugin : ReposilitePlugin() {

    override fun initialize(): Facade {
        val database = reposilite().database
        val auditExtensions = extensions()
        val clock = parameters().clock ?: Clock.systemUTC()
        val loginSettings = facade<SharedConfigurationFacade>().getDomainSettings<LoginSettings>()
        val audit: (AuditEvent) -> Unit = { auditExtensions.emitEvent(it) }
        val oidcCatalog = OidcProviderCatalog(loginSettings, warn = { logger.warn(it) })

        // The signing key is shared by every instance of the database, exactly like the ETag key: the first instance
        // that starts generates it, and the others read it back. The external login state cookie is sealed with a key
        // derived from it, so no second secret is stored.
        val signingKey: ByteArray? = database?.let {
            val encodedKey = facade<ConfigurationFacade>().findOrCreateConfiguration(SessionSigner.KEY_CONFIGURATION_NAME) {
                Base64.getEncoder().encodeToString(ByteArray(SIGNING_KEY_BYTES).also { SecureRandom().nextBytes(it) })
            }
            Base64.getDecoder().decode(encodedKey)
        }

        val externalAccounts = database?.let { database ->
            ExternalAccountService(
                IdentityStore(database),
                ExternalIdentityStore(database),
                audit = audit,
                log = { message -> logger.info(message) },
                warn = { message -> logger.warn(message) },
            )
        }

        // The legacy authentication.ldap settings serve the dashboard login while login.ldap is off, so that an upgrade from
        // 1.1 needs no change. The password check must stay on: it is what makes a bind with the user's password the login.
        val accessTokenFacade = facade<AccessTokenFacade>()
        val directories = database?.let { database ->
            LdapDirectories(
                login = { loginSettings.get().ldap },
                legacy = LdapAuthenticator(
                    journalist = this,
                    ldapSettings = facade<SharedConfigurationFacade>().getDomainSettings<AuthenticationSettings>().computed { it.ldap },
                    accessTokenFacade = accessTokenFacade,
                    failureFacade = facade(),
                    disableUserPasswordAuthentication = false,
                ),
                accounts = externalAccounts,
                identities = IdentityStore(database),
            )
        }

        // The per-account delay is read from the environment once, at start: it is a deployment decision, not a page setting.
        // Its guard is handed to the authentication facade, so that Basic and Bearer requests follow the same rule. The
        // observer of the sign-ins follows the same switch, so that nothing is counted or announced while it is off.
        val accountThrottleOn = AccountThrottle.enabledIn()
        val signInObserver = SignInObserver(
            enabled = accountThrottleOn,
            audit = { auditExtensions.emitEvent(it) },
            publish = { auditExtensions.emitEvent(it) },
            warn = { logger.warn(it) },
        )
        facade<AuthenticationFacade>().installSignInObserver(signInObserver)

        // Second factors of password logins. Authenticator seeds are sealed with the settings key, read on every use, so a key
        // that is set later applies without a restart. Without a key, authenticator codes are refused and nothing else changes.
        val secondFactors = database?.let { database ->
            val credentials = LoginStore(database, clock)
            val factorStore = SecondFactorStore(database, clock)
            val identities = IdentityStore(database)
            val seedCipher = SettingsSeedCipher(codecs = { facade<SharedConfigurationFacade>().settingsCodecs() })
            // settings-reencrypt re-seals the authenticator seeds too, so that a rotated key does not lock their owners out.
            facade<SharedConfigurationFacade>().registerSealedSecretStore(SecondFactorSeedResealer(store = factorStore, seeds = seedCipher))
            SecondFactorService(
                store = factorStore,
                identities = identities,
                limiter = LoginAttemptLimiter(credentials, loginSettings, clock),
                seeds = seedCipher,
                clock = clock,
                audit = audit,
                signInObserver = signInObserver,
                passkeys = PasskeyService(store = factorStore, identities = identities, clock = clock, audit = audit),
                logError = { message -> logger.error(message) },
            )
        }

        // The second factor policy: who it covers, and the scope of every session. Read from the settings on each use, so a change applies
        // at once. Without a database there are no sessions to scope.
        val secondFactorPolicy = database?.let { database ->
            SecondFactorPolicy(
                identities = IdentityStore(database),
                settings = { loginSettings.get() },
                clock = clock,
                secondFactorRequired = { principalId -> secondFactors?.requiresSecondFactor(principalId) ?: false },
            )
        }

        val accountThrottle = database?.let { database ->
            AccountThrottle(
                store = LoginStore(database, clock),
                settings = loginSettings,
                clock = clock,
                enabled = accountThrottleOn,
            )
        }
        accountThrottle?.let { facade<AuthenticationFacade>().installAccountGuard(it) }

        // While the account throttle is on, the per-address counter of Basic and Bearer requests lives in the login tables, so
        // that replicas count together. Off, each instance counts in its own memory, as before.
        if (accountThrottleOn) {
            database?.let { database ->
                facade<AuthenticationFacade>().installSharedFailedAddresses(
                    SharedFailedAddressStore(
                        store = LoginStore(database, clock),
                        purgeFloor = { maxOf(Duration.ofMinutes(loginSettings.get().lockoutMinutes), LoginAttemptPolicy.MAX_LOCKOUT) },
                    )
                )
            }
        }

        // The breached-password check reads its settings on every password change. The offline list of its mode OFFLINE is
        // loaded here, so that an unreadable file stops the start rather than the first password change. Off by default.
        val breachCheck = PasswordBreachCheck(settings = loginSettings, range = JdkBreachRangeClient())
        breachCheck.preload()

        val loginService = database?.let { database ->
            LoginService(
                identities = IdentityStore(database),
                credentials = LoginStore(database),
                hasher = PasswordHasher(),
                settings = loginSettings,
                clock = clock,
                audit = audit,
                secondFactorRequired = { principal -> secondFactors?.requiresSecondFactor(principal.id) ?: false },
                accountThrottle = accountThrottle,
                breachCheck = breachCheck,
                signInObserver = signInObserver,
                externalPasswordProviders = { directories?.provider()?.let { listOf(it) }.orEmpty() },
                externalAnnouncements = {
                    val oidc = oidcCatalog.announced().map {
                        ProviderAnnouncement(
                            id = it.id,
                            type = LoginProviderType.OIDC,
                            label = it.label.ifBlank { it.id },
                            icon = it.icon.name,
                            loginUrl = "/api/auth/oidc/${it.id}/login",
                        )
                    }
                    oidc + listOfNotNull(directories?.announcement())
                },
            )
        }

        // One cache of discovery documents and key sets, shared by the logins and the connection test.
        val oidcMetadata = OidcMetadata(JdkHttpFetcher(), clock)

        val oidcService = if (database != null && signingKey != null && externalAccounts != null) {
            OidcLoginService(
                catalog = oidcCatalog,
                metadata = oidcMetadata,
                http = JdkHttpFetcher(),
                states = OidcStateCookie(signingKey, clock),
                validator = IdTokenValidator(clock),
                accounts = externalAccounts,
                clock = clock,
                audit = audit,
            )
        } else {
            null
        }

        val sessions = if (database != null && signingKey != null) {
            SessionService(
                store = LoginStore(database),
                identities = IdentityStore(database),
                signer = SessionSigner(signingKey, clock),
                settings = loginSettings,
                clock = clock,
                audit = audit,
                publish = { auditExtensions.emitEvent(it) },
                policy = secondFactorPolicy,
            )
        } else {
            null
        }

        val ownTokens = database?.let { database ->
            OwnTokens(
                identities = IdentityStore(database),
                onTokenChanged = { accessTokenFacade.tokenStateChanged(it) },
                audit = audit,
                clock = clock,
            )
        }

        val authenticationFacade = facade<AuthenticationFacade>()
        val loginFacade = LoginFacade(
            loginService,
            sessions,
            ownTokens,
            secondFactors,
            onSecondFactorChanged = { authenticationFacade.forgetAllCachedCredentials() },
            clock = clock,
            policy = secondFactorPolicy,
            settings = { loginSettings.get() },
        )
        accessTokenFacade.secondFactorGuard = { principalId -> loginFacade.hasSecondFactor(principalId) }
        accessTokenFacade.directoryPrincipalOf = { principalName -> loginFacade.principalNamed(principalName) }
        val basePath = facade<LocalConfiguration>().basePath

        event { event: RoutingSetupEvent ->
            if (loginService != null) {
                event.registerRoutes(AuthLoginEndpoints(loginFacade, basePath, publicUrl = { loginSettings.get().publicUrl }))
                event.registerRoutes(SelfServiceEndpoints(loginFacade, accessTokenFacade))
                event.registerRoutes(SecondFactorEndpoints(loginFacade, accessTokenFacade, publicUrl = { loginSettings.get().publicUrl }, basePath = basePath))
                event.registerRoutes(ProviderTestEndpoints(OidcConnectionCheck(oidcCatalog, oidcMetadata), loginSettings))
            }
            if (oidcService != null) {
                event.registerRoutes(AuthOidcEndpoints(loginFacade, oidcService, basePath, publicUrl = { loginSettings.get().publicUrl }))
            }
        }

        // Shown once per start, and only while no user exists: the setup code is the way into an empty instance.
        event { _: ReposiliteInitializeEvent ->
            // Skipped provider entries are named once per start, with their reason, before anything else is logged.
            oidcCatalog.report()
            if (directories?.legacyActive() == true) {
                logger.info("Dashboard login accepts LDAP passwords from the legacy authentication.ldap settings. Move them to login.ldap, which takes precedence once enabled.")
            }
            loginFacade.issueSetupCode()?.let { code ->
                logger.info("")
                logger.info("--- First administrator")
                logger.info("No user exists yet. Open the dashboard and enter this setup code to create the first administrator:")
                logger.info("$ setup code: $code")
            }
        }

        return loginFacade
    }

    private companion object {
        const val SIGNING_KEY_BYTES = 32
    }

}
