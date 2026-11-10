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

package com.reposilite.javadocs.application

import com.reposilite.configuration.shared.SharedConfigurationFacade
import com.reposilite.frontend.FrontendFacade
import com.reposilite.javadocs.JavadocContainerService
import com.reposilite.javadocs.JavadocFacade
import com.reposilite.javadocs.infrastructure.JavadocEndpoints
import com.reposilite.javadocs.javadocEvictionRoot
import com.reposilite.maven.MavenFacade
import com.reposilite.maven.api.DeployEvent
import com.reposilite.plugin.api.Facade
import com.reposilite.plugin.api.JavadocInvalidatedEvent
import com.reposilite.plugin.api.Plugin
import com.reposilite.plugin.api.ReposilitePlugin
import com.reposilite.plugin.event
import com.reposilite.plugin.facade
import com.reposilite.plugin.parameters
import com.reposilite.status.FailureFacade
import com.reposilite.web.api.RoutingSetupEvent

@Plugin(name = "javadoc", dependencies = ["failure", "shared-configuration", "frontend", "maven"], settings = JavadocSettings::class)
internal class JavadocPlugin : ReposilitePlugin() {

    override fun initialize(): Facade {
        val failureFacade = facade<FailureFacade>()
        val mavenFacade = facade<MavenFacade>()
        val frontendFacade = facade<FrontendFacade>()

        val javadocSettings = facade<SharedConfigurationFacade>().getDomainSettings<JavadocSettings>()
        val javadocEnabled = javadocSettings.computed { it.enabled }
        val javadocSuffixes = javadocSettings.computed { it.suffixes }
        val javadocFolder = parameters().workingDirectory.resolve("javadocs")

        frontendFacade.registerPlaceholder(
            key = "{{REPOSILITE.JAVADOC_SUFFIXES}}",
            value = javadocSettings.computed { if (it.enabled) it.suffixes.joinToString(",") else "" },
        )

        val javadocContainerService = JavadocContainerService(
            mavenFacade = mavenFacade,
            javadocFolder = javadocFolder,
            failureFacade = failureFacade,
            suffixes = javadocSuffixes,
        )

        val javadocFacade = JavadocFacade(
            journalist = this,
            javadocFolder = javadocFolder,
            mavenFacade = mavenFacade,
            javadocContainerService = javadocContainerService
        )

        event { event: DeployEvent ->
            val artifactRoot = javadocEvictionRoot(event.gav, javadocSuffixes.get()) ?: return@event

            runCatching {
                javadocFacade.evictArtifact(event.repository.name, artifactRoot)
            }.onFailure {
                failureFacade.throwException("Cannot evict javadoc cache for ${event.gav}", it)
            }

            // Published after the local eviction and after the deploy is stored; without a cluster bus nobody listens.
            extensions().emitEvent(JavadocInvalidatedEvent(event.repository.name, artifactRoot.toString()))
        }

        event { event: RoutingSetupEvent ->
            event.registerRoutes(
                JavadocEndpoints(
                    javadocFacade = javadocFacade,
                    javadocEnabled = javadocEnabled,
                ),
            )
        }

        return javadocFacade
    }

}