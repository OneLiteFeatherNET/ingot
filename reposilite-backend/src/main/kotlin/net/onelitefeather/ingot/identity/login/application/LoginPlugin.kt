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
import com.reposilite.configuration.shared.SharedConfigurationFacade
import com.reposilite.plugin.api.Facade
import com.reposilite.plugin.api.Plugin
import com.reposilite.plugin.api.ReposilitePlugin
import com.reposilite.plugin.event
import com.reposilite.plugin.facade
import com.reposilite.plugin.reposilite
import com.reposilite.web.api.RoutingSetupEvent
import net.onelitefeather.ingot.identity.login.LoginFacade
import net.onelitefeather.ingot.identity.login.LoginService
import net.onelitefeather.ingot.identity.login.PasswordHasher
import net.onelitefeather.ingot.identity.login.api.AuthLoginEndpoints
import net.onelitefeather.ingot.identity.login.storage.LoginStore
import net.onelitefeather.ingot.identity.storage.IdentityStore
import java.time.Clock

@Plugin(name = "login", dependencies = ["access-token", "authentication", "shared-configuration", "audit"])
class LoginPlugin : ReposilitePlugin() {

    override fun initialize(): Facade {
        val database = reposilite().database
        val auditExtensions = extensions()

        val service = database?.let { database ->
            LoginService(
                identities = IdentityStore(database),
                credentials = LoginStore(database),
                hasher = PasswordHasher(),
                protection = facade<SharedConfigurationFacade>()
                    .getDomainSettings<AuthenticationSettings>()
                    .computed { it.bruteForceProtection },
                clock = Clock.systemUTC(),
                audit = { auditExtensions.emitEvent(it) },
            )
        }

        val loginFacade = LoginFacade(service)

        event { event: RoutingSetupEvent ->
            if (service != null) {
                event.registerRoutes(AuthLoginEndpoints(loginFacade))
            }
        }

        return loginFacade
    }

}
