package net.onelitefeather.ingot.identity.login.application

import com.reposilite.audit.api.AuditEvent
import com.reposilite.configuration.ConfigurationFacade
import com.reposilite.configuration.local.LocalConfiguration
import com.reposilite.token.AccessTokenFacade
import com.reposilite.configuration.shared.SharedConfigurationFacade
import com.reposilite.plugin.api.Facade
import com.reposilite.plugin.api.Plugin
import com.reposilite.plugin.api.ReposiliteInitializeEvent
import com.reposilite.plugin.api.ReposilitePlugin
import com.reposilite.plugin.event
import com.reposilite.plugin.facade
import com.reposilite.plugin.reposilite
import com.reposilite.web.api.RoutingSetupEvent
import net.onelitefeather.ingot.identity.login.LoginFacade
import net.onelitefeather.ingot.identity.login.LoginProviderType
import net.onelitefeather.ingot.identity.login.LoginService
import net.onelitefeather.ingot.identity.login.LoginSettings
import net.onelitefeather.ingot.identity.login.OwnTokens
import net.onelitefeather.ingot.identity.login.PasswordHasher
import net.onelitefeather.ingot.identity.login.ProviderAnnouncement
import net.onelitefeather.ingot.identity.login.api.AuthLoginEndpoints
import net.onelitefeather.ingot.identity.login.api.ProviderTestEndpoints
import net.onelitefeather.ingot.identity.login.api.SelfServiceEndpoints
import net.onelitefeather.ingot.identity.login.external.ExternalAccountService
import net.onelitefeather.ingot.identity.login.external.ExternalIdentityStore
import net.onelitefeather.ingot.identity.login.ldap.LdapLoginProvider
import net.onelitefeather.ingot.identity.login.oidc.AuthOidcEndpoints
import net.onelitefeather.ingot.identity.login.oidc.IdTokenValidator
import net.onelitefeather.ingot.identity.login.oidc.JdkHttpFetcher
import net.onelitefeather.ingot.identity.login.oidc.OidcConnectionCheck
import net.onelitefeather.ingot.identity.login.oidc.OidcLoginService
import net.onelitefeather.ingot.identity.login.oidc.OidcMetadata
import net.onelitefeather.ingot.identity.login.oidc.OidcProviderCatalog
import net.onelitefeather.ingot.identity.login.oidc.OidcStateCookie
import net.onelitefeather.ingot.identity.login.storage.LoginStore
import net.onelitefeather.ingot.identity.session.SessionService
import net.onelitefeather.ingot.identity.session.SessionSigner
import net.onelitefeather.ingot.identity.storage.IdentityStore
import java.security.SecureRandom
import java.time.Clock
import java.util.Base64

@Plugin(
    name = "login",
    dependencies = ["access-token", "authentication", "configuration", "shared-configuration", "audit", "local-configuration"],
    settings = LoginSettings::class,
)
class LoginPlugin : ReposilitePlugin() {

    override fun initialize(): Facade {
        val database = reposilite().database
        val auditExtensions = extensions()
        val clock = Clock.systemUTC()
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
            ExternalAccountService(IdentityStore(database), ExternalIdentityStore(database), audit = audit, log = { message -> logger.info(message) })
        }

        val loginService = database?.let { database ->
            LoginService(
                identities = IdentityStore(database),
                credentials = LoginStore(database),
                hasher = PasswordHasher(),
                settings = loginSettings,
                clock = clock,
                audit = audit,
                externalPasswordProviders = {
                    val ldap = loginSettings.get().ldap
                    if (externalAccounts != null && ldap.enabled && ldap.missingField() == null) {
                        listOf(LdapLoginProvider(ldap, externalAccounts))
                    } else {
                        emptyList()
                    }
                },
                externalAnnouncements = {
                    val ldap = loginSettings.get().ldap
                    val oidc = oidcCatalog.announced().map {
                        ProviderAnnouncement(
                            id = it.id,
                            type = LoginProviderType.OIDC,
                            label = it.label.ifBlank { it.id },
                            icon = it.icon.name,
                            loginUrl = "/api/auth/oidc/${it.id}/login",
                        )
                    }
                    val directory = if (ldap.enabled && ldap.missingField() == null) {
                        listOf(ProviderAnnouncement(id = LdapLoginProvider.ID, type = LoginProviderType.LDAP, label = ldap.label.ifBlank { "LDAP" }, icon = "LDAP"))
                    } else {
                        emptyList()
                    }
                    oidc + directory
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
            )
        } else {
            null
        }

        val accessTokenFacade = facade<AccessTokenFacade>()
        val ownTokens = database?.let { database ->
            OwnTokens(
                identities = IdentityStore(database),
                onTokenChanged = { accessTokenFacade.tokenStateChanged(it) },
                audit = audit,
                clock = clock,
            )
        }

        val loginFacade = LoginFacade(loginService, sessions, ownTokens)
        val basePath = facade<LocalConfiguration>().basePath

        event { event: RoutingSetupEvent ->
            if (loginService != null) {
                event.registerRoutes(AuthLoginEndpoints(loginFacade, basePath))
                event.registerRoutes(SelfServiceEndpoints(loginFacade, accessTokenFacade))
                event.registerRoutes(ProviderTestEndpoints(OidcConnectionCheck(oidcCatalog, oidcMetadata), loginSettings))
            }
            if (oidcService != null) {
                event.registerRoutes(AuthOidcEndpoints(loginFacade, oidcService, basePath))
            }
        }

        // Shown once per start, and only while no user exists: the setup code is the way into an empty instance.
        event { _: ReposiliteInitializeEvent ->
            // Skipped provider entries are named once per start, with their reason, before anything else is logged.
            oidcCatalog.report()
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
