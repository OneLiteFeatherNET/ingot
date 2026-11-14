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

@file:OptIn(ExperimentalIngotFormatApi::class, net.onelitefeather.ingot.storage.ExperimentalIngotStorageApi::class)

package com.reposilite.maven

import net.onelitefeather.ingot.format.ExperimentalIngotFormatApi
import com.reposilite.auth.AuthenticationFacade
import com.reposilite.journalist.Journalist
import com.reposilite.maven.application.MirroredRepositorySettings
import com.reposilite.maven.application.RepositorySettings
import com.reposilite.maven.application.referencedHost
import com.reposilite.shared.http.RemoteClientProvider
import com.reposilite.shared.http.createHttpProxy
import com.reposilite.status.FailureFacade
import com.reposilite.storage.StorageFacade
import net.onelitefeather.ingot.storage.capability.RelatedKeyAware
import net.onelitefeather.ingot.format.RepositoryFormats
import java.nio.file.Path
import java.nio.file.Paths
import java.util.UUID

internal class RepositoryFactory(
    private val journalist: Journalist,
    private val workingDirectory: Path,
    private val authenticationFacade: AuthenticationFacade,
    private val remoteClientProvider: RemoteClientProvider,
    private val repositoryService: RepositoryService,
    private val failureFacade: FailureFacade,
    private val storageFacade: StorageFacade,
    private val repositoriesNames: Collection<String>,
    private val mirrorEnvironment: MirrorEnvironment = MirrorEnvironment.NONE,
    private val uploadChecksumDefault: UploadChecksumPolicy = UploadChecksumPolicy.IGNORE,
    private val formats: RepositoryFormats = RepositoryFormats.installed(),
) {

    private val repositoriesDirectory = Paths.get("repositories")

    /**
     * A repository whose format is not installed is refused before any storage is created. The start records the failure and
     * leaves the repository out, so a stored repository of a later version is never served as Maven.
     */
    fun createRepository(repositoryName: String, configuration: RepositorySettings): Repository {
        val format = formats.get(configuration.resolvedFormat())
            ?: throw IllegalArgumentException(
                "Unknown format '${configuration.resolvedFormat()}' of repository '$repositoryName', the installed formats are ${formats.ids.sorted().joinToString(", ")}"
            )

        val mirrorHosts = configuration.proxied.mapNotNull { createMirroredHostConfiguration(it) }
        val storageProvider = storageFacade
            .createStorageProvider(
                journalist = journalist,
                failureFacade = failureFacade,
                workingDirectory = workingDirectory.resolve(repositoriesDirectory),
                repository = repositoryName,
                storageSettings = configuration.storageProvider,
            )
            ?: throw IllegalArgumentException("Unknown storage provider '${configuration.storageProvider.type}'")
        // The head cache of an S3 storage drops the keys that the format names, see RelatedKeyRule
        (storageProvider as? RelatedKeyAware)?.let { aware -> format.storageHints.relatedKeys?.let { aware.useRelatedKeys(it) } }

        return Repository(
            name = repositoryName.ifEmpty { UUID.randomUUID().toString() },
            visibility = configuration.visibility,
            description = configuration.description,
            tags = configuration.tags,
            redeployment = configuration.redeployment,
            preserveSnapshots = configuration.preserveSnapshots,
            declaredType = configuration.type,
            uploadChecksumPolicy = configuration.uploadChecksumPolicy ?: uploadChecksumDefault,
            versionPolicy = configuration.versionPolicy ?: VersionPolicy.MIXED,
            readOnly = configuration.readOnly ?: false,
            repositoryFormat = format,
            mirrorHosts = mirrorHosts,
            storageProvider = storageProvider,
            storagePolicy = configuration.storagePolicy,
            metadataMaxAgeInSeconds = configuration.metadataMaxAge,
            resolutionCacheMaxEntries = configuration.resolutionCacheMaxEntries,
            protectedGroupPrefixes = configuration.protectedGroupPrefixes,
        )
    }

    private fun createMirroredHostConfiguration(configurationSource: MirroredRepositorySettings): MirrorHost? {
        val host = configurationSource.referencedHost()
        if (host.isEmpty()) {
            failureFacade.logger.warn("Empty reference value of mirrored repository found, skipping configuration entry.")
            return null
        }

        val remoteClient = when {
            repositoriesNames.contains(host) ->
                RepositoryLoopbackClient(
                    authenticationFacade = authenticationFacade,
                    repositoryService = repositoryService,
                    repositoryName = host
                )
            else -> {
                val configuredProxy = configurationSource.httpProxy
                selectEnvironmentProxy(configuredProxy, host, mirrorEnvironment).fold(
                    { environmentProxy ->
                        val proxy = environmentProxy ?: configuredProxy
                            .takeIf { it.isNotEmpty() }
                            ?.let { createHttpProxy(it) }
                        remoteClientProvider.createClient(failureFacade, proxy)
                    },
                    { refusal ->
                        failureFacade.logger.warn("Mirror of repository refused: $refusal")
                        RefusedRemoteClient(refusal)
                    }
                )
            }
        }

        return MirrorHost(host, configurationSource, remoteClient)
    }

}
