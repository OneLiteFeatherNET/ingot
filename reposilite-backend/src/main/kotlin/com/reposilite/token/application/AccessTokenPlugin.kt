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

package com.reposilite.token.application

import com.reposilite.audit.decision.DecisionLogMode
import com.reposilite.audit.decision.DecisionRecorder
import com.reposilite.audit.decision.DecisionSink
import com.reposilite.configuration.ConfigurationFacade
import com.reposilite.console.api.CommandsSetupEvent
import com.reposilite.plugin.api.Plugin
import com.reposilite.plugin.facade
import com.reposilite.plugin.api.ReposiliteInitializeEvent
import com.reposilite.plugin.api.ReposilitePlugin
import com.reposilite.plugin.api.ReposiliteDisposeEvent
import com.reposilite.plugin.event
import com.reposilite.plugin.parameters
import com.reposilite.plugin.reposilite
import com.reposilite.shared.EntityTagSigner
import com.reposilite.token.AccessTokenFacade
import com.reposilite.token.AccessTokenPermission.MANAGER
import com.reposilite.token.ExportTokensCommand
import com.reposilite.token.ImportTokensCommand
import com.reposilite.token.infrastructure.RouteAdd
import com.reposilite.token.infrastructure.RouteRemove
import com.reposilite.token.infrastructure.AccessTokenApiEndpoints
import com.reposilite.token.infrastructure.ChModCommand
import com.reposilite.token.infrastructure.ChNameCommand
import com.reposilite.token.infrastructure.KeygenCommand
import com.reposilite.token.infrastructure.RegenerateCommand
import com.reposilite.token.infrastructure.RevokeCommand
import com.reposilite.token.infrastructure.TokensCommand
import com.reposilite.web.api.RoutingSetupEvent
import net.onelitefeather.ingot.repository.RepositoryRegistry
import net.onelitefeather.ingot.identity.api.IdentityApiEndpoints
import net.onelitefeather.ingot.identity.login.LoginFacade
import net.onelitefeather.ingot.identity.authorization.DecisionTrail
import net.onelitefeather.ingot.identity.service.IdentityFacade
import net.onelitefeather.ingot.identity.service.IdpGuardMode
import net.onelitefeather.ingot.identity.storage.IdentityStore
import java.time.Clock

@Plugin(name = "access-token", dependencies = ["configuration"])
internal class AccessTokenPlugin : ReposilitePlugin() {

    companion object {
        const val MAX_TOKEN_NAME = 255
        const val MAX_ROUTE_LENGTH = 1024
    }

    override fun initialize(): AccessTokenFacade {
        val entityTags = EntityTagSigner.stored(facade<ConfigurationFacade>())
        val pluginExtensions = extensions()

        // The decision log is built only when an operator turns it on, so the default mode starts no writer and no queue.
        val decisionMode = reposilite().decisionLogSettings.mode
        val decisions = if (decisionMode == DecisionLogMode.OFF) {
            DecisionTrail.OFF
        } else {
            val recorder = DecisionRecorder(
                mode = decisionMode,
                sink = DecisionSink { record -> pluginExtensions.emitEvent(record.toAuditEvent()) },
                logger = logger,
                clock = Clock.systemUTC(),
            )
            event { _: ReposiliteDisposeEvent -> recorder.close() }
            DecisionTrail(recorder)
        }

        val accessTokenFacade = AccessTokenComponents(
            journalist = this,
            database = reposilite().database,
            runMigrations = parameters().runMigrations
        ).accessTokenFacade(
            extensions = pluginExtensions,
            entityTags = entityTags,
            bcryptStrength = parameters().tokenHashStrength,
            decisions = decisions,
        )

        // The identity administration needs the identity tables, which exist only with a database.
        val identityFacade = reposilite().database?.let { database ->
            IdentityFacade(
                store = IdentityStore(database),
                accessTokens = accessTokenFacade,
                entityTags = entityTags,
                extensions = extensions(),
                // Resolved per request, so that the repository service is only needed once the API is used.
                visibilityOf = { repository -> facade<RepositoryRegistry>().repository(repository)?.visibility?.name },
                repositoryNames = { facade<RepositoryRegistry>().repositories().map { it.name } },
                idpGuard = IdpGuardMode.read(),
                warnings = { message -> logger.warn(message) },
                decisions = decisions,
            )
        }

        // The --token configuration is re-applied on every start, so the bootstrap is not audited.
        parameters().tokens.forEach {
            val (token) = accessTokenFacade.createAccessToken(it, audited = false)
            accessTokenFacade.addPermission(token.identifier, MANAGER, audited = false)
        }

        event { event: CommandsSetupEvent ->
            event.registerCommand(TokensCommand(accessTokenFacade))
            event.registerCommand(KeygenCommand(accessTokenFacade))
            event.registerCommand(ChNameCommand(accessTokenFacade))
            event.registerCommand(ChModCommand(accessTokenFacade))
            event.registerCommand(RevokeCommand(accessTokenFacade))
            event.registerCommand(RegenerateCommand(accessTokenFacade))

            event.registerCommand(RouteAdd(accessTokenFacade))
            event.registerCommand(RouteRemove(accessTokenFacade))

            val workingDirectory = parameters().workingDirectory
            event.registerCommand(ExportTokensCommand(workingDirectory, accessTokenFacade))
            event.registerCommand(ImportTokensCommand(workingDirectory, accessTokenFacade))
        }

        event { event: RoutingSetupEvent ->
            event.registerRoutes(AccessTokenApiEndpoints(accessTokenFacade))
            // The login facade answers the step-up of the instance admin routes. It is resolved per request: the login plugin starts after this one.
            identityFacade?.let { event.registerRoutes(IdentityApiEndpoints(it, secondFactors = { facade<LoginFacade>() })) }
        }

        event { _: ReposiliteInitializeEvent ->
            if (accessTokenFacade.count() == 0L) {
                logger.info("")
                logger.info("--- Access Tokens")
                logger.info("Your instance does not have any access tokens yet.")
                logger.info("To generate token, you can use --token flag to create temporary token:")
                logger.info("$ java -jar reposilite.jar --token name:secret")
                logger.info("Or using command in this terminal/through web dashboard:")
                logger.info("$ help token-generate")
            }
        }

        return accessTokenFacade
    }

}
