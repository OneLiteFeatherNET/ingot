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

package com.reposilite.maven

import net.onelitefeather.ingot.search.ArtifactIndexSink
import com.reposilite.auth.AuthenticationFacade
import com.reposilite.journalist.Journalist
import com.reposilite.maven.application.RepositorySettings
import com.reposilite.maven.application.referencedHost
import com.reposilite.plugin.Extensions
import com.reposilite.shared.ErrorResponse
import com.reposilite.shared.http.RemoteClientProvider
import com.reposilite.shared.notFoundError
import com.reposilite.statistics.StatisticsFacade
import com.reposilite.status.FailureFacade
import com.reposilite.storage.StorageFacade
import com.reposilite.storage.s3.S3StorageProviderSettings
import com.reposilite.storage.s3.findS3SharedBucketConflicts
import java.nio.file.Path
import panda.std.Result
import panda.std.asSuccess
import panda.std.reactive.Reference

internal class RepositoryProvider(
    private val journalist: Journalist,
    private val workingDirectory: Path,
    private val remoteClientProvider: RemoteClientProvider,
    private val authenticationFacade: AuthenticationFacade,
    extensions: Extensions,
    private val failureFacade: FailureFacade,
    statisticsFacade: StatisticsFacade,
    private val storageFacade: StorageFacade,
    mirrorService: MirrorService,
    resolutionProvider: ResolutionProvider,
    repositorySecurityProvider: RepositorySecurityProvider,
    repositoriesSource: Reference<List<RepositorySettings>>,
    artifactIndexSink: ArtifactIndexSink,
) {

    val repositoryService = RepositoryService(
        journalist = journalist,
        repositoryProvider = this,
        securityProvider = repositorySecurityProvider,
        mirrorService = mirrorService,
        resolutionProvider = resolutionProvider,
        statisticsFacade = statisticsFacade,
        extensions = extensions,
        artifactIndexSink = artifactIndexSink,
    )

    /** The repositories in configuration order, keyed by name. Read by requests and usage walks, so it is only ever swapped. */
    @Volatile
    private var repositories: Map<String, Repository> = emptyMap()

    /** The repositories the current configuration built, with the settings each one was built from. */
    private var built: List<BuiltRepository> = emptyList()

    init {
        apply(repositoriesSource.get())
        repositoriesSource.subscribe { apply(it) }
    }

    /**
     * Brings the repositories in line with [configuration]. A repository whose settings did not change keeps its live instance:
     * its storage serves requests and usage walks, and closing it would fail every read still in flight. Only the repositories
     * that were replaced or removed are shut down, once the new set is in place.
     */
    @Synchronized
    private fun apply(configuration: List<RepositorySettings>) {
        val previous = built
        val names = configuration.map { it.id }.toSet()
        val factory = RepositoryFactory(
            journalist = journalist,
            workingDirectory = workingDirectory,
            authenticationFacade = authenticationFacade,
            remoteClientProvider = remoteClientProvider,
            failureFacade = failureFacade,
            storageFacade = storageFacade,
            repositoryService = repositoryService,
            repositoriesNames = names,
        )

        val sharedBucketConflicts = findS3SharedBucketConflicts(
            configuration.mapNotNull { settings ->
                (settings.storageProvider as? S3StorageProviderSettings)?.let { settings.id to it }
            }
        )

        val next = configuration.mapNotNull { settings ->
            when (settings.id) {
                in sharedBucketConflicts -> {
                    failureFacade.throwException(
                        identifier = "Cannot load repository '${settings.id}'",
                        throwable = IllegalStateException("Its S3 key namespace overlaps another repository sharing the same bucket. Give each repository a distinct 'prefix', or enable 'sharedBucket' on every repository sharing the bucket.")
                    )
                    null
                }
                else -> reuse(previous, settings, names) ?: build(factory, settings, names)
            }
        }

        built = next
        repositories = next.associate { it.repository.name to it.repository }
        previous
            .filter { old -> next.none { it.repository === old.repository } }
            .forEach { it.repository.shutdown() }
    }

    /** The live repository built from the same settings, when its links to other repositories still resolve the same way. */
    private fun reuse(previous: List<BuiltRepository>, settings: RepositorySettings, names: Set<String>): BuiltRepository? {
        val old = previous.firstOrNull { it.settings.id == settings.id } ?: return null
        if (old.settings != settings || linksIn(settings, old.names) != linksIn(settings, names)) {
            return null
        }
        return BuiltRepository(settings, old.repository, names)
    }

    private fun build(factory: RepositoryFactory, settings: RepositorySettings, names: Set<String>): BuiltRepository? =
        runCatching { factory.createRepository(settings.id, settings) }
            .onFailure { failureFacade.throwException("Cannot load ${settings.id} repository", it) }
            .getOrNull()
            ?.let { BuiltRepository(settings, it, names) }

    /** For each mirrored entry, whether it names one of the local repositories, which decides how the mirror is reached. */
    private fun linksIn(settings: RepositorySettings, names: Set<String>): List<Boolean> =
        settings.proxied.map { it.referencedHost() in names }


    fun findRepository(name: String): Result<Repository, ErrorResponse> =
        getRepository(name)
            ?.asSuccess()
            ?: notFoundError("Repository $name not found")

    fun getRepository(name: String): Repository? =
        repositories[name]

    fun getRepositories(): Collection<Repository> =
        repositories.values

}

/** A repository together with the settings it was built from and the names of the local repositories it was built against. */
private class BuiltRepository(
    val settings: RepositorySettings,
    val repository: Repository,
    val names: Set<String>,
)
