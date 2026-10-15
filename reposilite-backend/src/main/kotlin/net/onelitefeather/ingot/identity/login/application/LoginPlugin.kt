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

package net.onelitefeather.ingot.identity.login.application

import com.reposilite.auth.application.AuthenticationSettings
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
import net.onelitefeather.ingot.identity.login.LoginService
import net.onelitefeather.ingot.identity.login.LoginSettings
import net.onelitefeather.ingot.identity.login.OwnTokens
import net.onelitefeather.ingot.identity.login.PasswordHasher
import net.onelitefeather.ingot.identity.login.api.AuthLoginEndpoints
import net.onelitefeather.ingot.identity.login.api.SelfServiceEndpoints
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

        val service = database?.let { database ->
            LoginService(
                identities = IdentityStore(database),
                credentials = LoginStore(database),
                hasher = PasswordHasher(),
                protection = facade<SharedConfigurationFacade>()
                    .getDomainSettings<AuthenticationSettings>()
                    .computed { it.bruteForceProtection },
                clock = clock,
                audit = { auditExtensions.emitEvent(it) },
            )
        }

        // The signing key is shared by every instance of the database, exactly like the ETag key: the first instance
        // that starts generates it, and the others read it back.
        val sessions = database?.let { database ->
            val encodedKey = facade<ConfigurationFacade>().findOrCreateConfiguration(SessionSigner.KEY_CONFIGURATION_NAME) {
                Base64.getEncoder().encodeToString(ByteArray(SIGNING_KEY_BYTES).also { SecureRandom().nextBytes(it) })
            }
            SessionService(
                store = LoginStore(database),
                identities = IdentityStore(database),
                signer = SessionSigner(Base64.getDecoder().decode(encodedKey), clock),
                settings = loginSettings,
                clock = clock,
            )
        }

        val accessTokenFacade = facade<AccessTokenFacade>()
        val ownTokens = database?.let { database ->
            OwnTokens(
                identities = IdentityStore(database),
                onTokenChanged = { accessTokenFacade.tokenStateChanged(it) },
                audit = { auditExtensions.emitEvent(it) },
                clock = clock,
            )
        }

        val loginFacade = LoginFacade(service, sessions, ownTokens)
        val basePath = facade<LocalConfiguration>().basePath

        event { event: RoutingSetupEvent ->
            if (service != null) {
                event.registerRoutes(AuthLoginEndpoints(loginFacade, basePath))
                event.registerRoutes(SelfServiceEndpoints(loginFacade, accessTokenFacade))
            }
        }

        // Shown once per start, and only while no user exists: the setup code is the way into an empty instance.
        event { _: ReposiliteInitializeEvent ->
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
