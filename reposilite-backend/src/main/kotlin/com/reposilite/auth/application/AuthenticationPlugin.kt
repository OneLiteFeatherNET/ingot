/*
 * Copyright (c) 2023 dzikoysk
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

package com.reposilite.auth.application

import com.reposilite.auth.infrastructure.AuthenticationEndpoint
import com.reposilite.auth.infrastructure.PostAuthHandler
import com.reposilite.configuration.shared.SharedConfigurationFacade
import com.reposilite.plugin.api.Facade
import com.reposilite.plugin.api.ReposiliteDisposeEvent
import com.reposilite.plugin.api.ReposiliteStartedEvent
import com.reposilite.plugin.api.Plugin
import com.reposilite.plugin.api.ReposilitePlugin
import com.reposilite.plugin.event
import com.reposilite.plugin.facade
import com.reposilite.plugin.reposilite
import com.reposilite.web.api.RoutingSetupEvent
import net.onelitefeather.ingot.identity.storage.AuthorizationRevision
import net.onelitefeather.ingot.identity.storage.IdentityStore

@Plugin(name = "authentication", dependencies = ["failure", "shared-configuration", "access-token"], settings = AuthenticationSettings::class)
class AuthenticationPlugin : ReposilitePlugin() {

    override fun initialize(): Facade {
        val sharedConfigurationFacade = facade<SharedConfigurationFacade>()
        // The policy revision lives in the identity tables, so it exists wherever the database does. The interval is read here,
        // so an invalid value stops the start instead of the refresh.
        val refreshInterval = AuthorizationRevision.refreshInterval()
        val authorizationRevision = reposilite().database?.let { AuthorizationRevision(IdentityStore(it)) }

        val authenticationFacade =
            AuthenticationComponents(
                journalist = this,
                accessTokenFacade = facade(),
                failureFacade = facade(),
                authenticationSettings = sharedConfigurationFacade.getDomainSettings(),
                disableUserPasswordAuthentication = System.getProperty("reposilite.ldap.disable-user-password-authentication", "false") == "true"
            ).authenticationFacade(
                extensions = extensions(),
                authorizationRevision = authorizationRevision,
                database = reposilite().database,
            )

        authorizationRevision?.let { revision ->
            event { _: ReposiliteStartedEvent -> revision.start(refreshInterval) }
            event { _: ReposiliteDisposeEvent -> revision.close() }
        }

        event { event: RoutingSetupEvent ->
            event.registerRoutes(AuthenticationEndpoint(authenticationFacade))
            event.registerRoutes(PostAuthHandler())
        }

        return authenticationFacade
    }

}
