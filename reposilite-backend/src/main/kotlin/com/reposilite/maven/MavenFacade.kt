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

import com.reposilite.journalist.Journalist
import com.reposilite.journalist.Logger
import com.reposilite.maven.api.DeleteRequest
import com.reposilite.maven.api.DeployRequest
import com.reposilite.maven.api.GeneratePomRequest
import com.reposilite.maven.api.LatestArtifactQueryRequest
import com.reposilite.maven.api.LatestBadgeRequest
import com.reposilite.maven.api.LatestVersionResponse
import com.reposilite.maven.api.LookupRequest
import com.reposilite.maven.api.Metadata
import com.reposilite.maven.api.RepositoryListQuery
import com.reposilite.maven.api.RepositoryListResponse
import com.reposilite.maven.api.ResolvedDocument
import com.reposilite.maven.api.SaveMetadataRequest
import com.reposilite.maven.api.VersionLookupRequest
import com.reposilite.maven.api.VersionsResponse
import com.reposilite.plugin.api.Facade
import com.reposilite.shared.ErrorResponse
import com.reposilite.storage.api.DirectoryInfo
import com.reposilite.storage.api.FileDetails
import com.reposilite.storage.api.Location
import com.reposilite.token.AccessTokenIdentifier
import panda.std.Result
import java.io.InputStream
import java.time.Instant
import java.util.concurrent.CompletableFuture

class MavenFacade internal constructor(
    private val journalist: Journalist,
    private val repositorySecurityProvider: RepositorySecurityProvider,
    private val repositoryProvider: RepositoryProvider,
    private val metadataService: MetadataService,
    private val latestService: LatestService,
    private val storageUsageService: StorageUsageService,
    private val mirrorCache: MirrorCacheService,
) : Journalist, Facade {

    private val repositoryService = repositoryProvider.repositoryService

    fun findDetails(lookupRequest: LookupRequest): Result<out FileDetails, ErrorResponse> =
        repositoryService.findDetails(lookupRequest)

    fun findFile(lookupRequest: LookupRequest): Result<ResolvedDocument, ErrorResponse> =
        repositoryService.findFile(lookupRequest)
            .peek { touchMirrorCopy(lookupRequest) }

    fun findData(lookupRequest: LookupRequest): Result<InputStream, ErrorResponse> =
        repositoryService.findInputStream(lookupRequest)
            .peek { touchMirrorCopy(lookupRequest) }

    fun deployFile(deployRequest: DeployRequest): Result<Unit, ErrorResponse> =
        repositoryService.deployFile(deployRequest)
            // a deployment makes the directory hold hosted content, so it is no longer a mirror copy
            .peek { mirrorCache.forgetVersion(deployRequest.repository.name, deployRequest.gav.getParent().provenancePath()) }

    fun deleteFile(deleteRequest: DeleteRequest): Result<Unit, ErrorResponse> =
        repositoryService.deleteFile(deleteRequest)
            .peek { afterDelete(deleteRequest.repository, deleteRequest.gav) }

    /**
     * Deletes without an access token, for server side jobs such as scheduled cleanup. Every deletion path reconciles
     * the metadata the same way as [deleteFile], so both paths leave maven-metadata.xml describing the same state.
     */
    internal fun deleteUnchecked(repository: Repository, gav: Location, by: String, cleanupRunId: String? = null): Result<Unit, ErrorResponse> =
        repositoryService.deleteUnchecked(repository, gav, by, cleanupRunId)
            .peek { afterDelete(repository, gav) }

    /** The mirror copies of [repository] with their last access. Only these versions can be cleaned up as copies. */
    internal fun mirrorCacheEntries(repository: String): Map<String, Instant> =
        mirrorCache.cachedVersions(repository)

    /** Every delete path brings the metadata and the mirror records up to date with the removed file or directory. */
    private fun afterDelete(repository: Repository, gav: Location) {
        metadataService.reconcileAfterDelete(repository, gav)
        mirrorCache.forgetSubtree(repository.name, gav.provenancePath())
    }

    /** A served file refreshes the last access of its mirror copy, when the repository stores mirror copies. */
    private fun touchMirrorCopy(lookupRequest: LookupRequest) {
        val repository = getRepository(lookupRequest.repository) ?: return
        if (repository.mirrorHosts.any { it.configuration.store }) {
            mirrorCache.touch(repository.name, lookupRequest.gav.getParent().provenancePath())
        }
    }

    fun saveMetadata(saveMetadataRequest: SaveMetadataRequest): Result<Metadata, ErrorResponse> =
        metadataService.saveMetadata(saveMetadataRequest)

    fun generatePom(generatePomRequest: GeneratePomRequest): Result<Unit, ErrorResponse> =
        metadataService.generatePom(generatePomRequest)

    fun findMetadata(repository: Repository, gav: Location): Result<Metadata, ErrorResponse> =
        metadataService.findMetadata(repository, gav)

    /** Brings the version-level metadata of a SNAPSHOT directory in line with the files stored there, see [MetadataService.reconcileSnapshotVersion]. */
    internal fun reconcileSnapshotVersion(repository: Repository, versionDirectory: Location) =
        metadataService.reconcileSnapshotVersion(repository, versionDirectory)

    fun findVersions(lookupRequest: VersionLookupRequest): Result<VersionsResponse, ErrorResponse> =
        repositorySecurityProvider.canAccessResource(lookupRequest.accessToken, lookupRequest.repository, lookupRequest.gav)
            .flatMap { metadataService.findVersions(lookupRequest.repository, lookupRequest.gav, lookupRequest.filter, lookupRequest.sorted) }

    fun findLatestVersion(lookupRequest: VersionLookupRequest): Result<LatestVersionResponse, ErrorResponse> =
        repositorySecurityProvider.canAccessResource(lookupRequest.accessToken, lookupRequest.repository, lookupRequest.gav)
            .flatMap { metadataService.findLatestVersion(lookupRequest.repository, lookupRequest.gav, lookupRequest.filter, lookupRequest.sorted) }

    fun <T> findLatestVersionFile(latestArtifactQueryRequest: LatestArtifactQueryRequest, handler: MatchedVersionHandler<T>): Result<T, ErrorResponse> =
        latestService.queryLatestArtifact(
            request = latestArtifactQueryRequest,
            supplier = { findLatestVersion(it) },
            handler = handler
        )

    fun getAvailableFiles(request: LookupRequest, directoryInfo: DirectoryInfo): List<FileDetails> =
        getRepository(request.repository)!!.let { repository ->
            directoryInfo.files.filter {
                repositorySecurityProvider.canBrowseResource(
                    accessToken = request.accessToken,
                    repository = repository,
                    gav = request.gav.resolve(it.name)
                ).isOk
            }
        }

    fun createLatestBadge(lookupRequest: LatestBadgeRequest): Result<String, ErrorResponse> =
        findLatestVersion(lookupRequest.toVersionLookupRequest())
            .flatMap { latestService.createLatestBadge(lookupRequest, it.version) }

    fun acceptsCachingOf(request: LookupRequest): Boolean =
        getRepository(request.repository)?.acceptsCachingOf(request.gav) ?: false

    fun canAccessResource(accessToken: AccessTokenIdentifier?, repository: Repository, gav: Location): Result<Unit, ErrorResponse> =
        repositorySecurityProvider.canAccessResource(accessToken, repository, gav)

    fun findRepositories(accessToken: AccessTokenIdentifier?): DirectoryInfo =
        repositoryService.getRootDirectory(accessToken)

    fun listRepositories(accessToken: AccessTokenIdentifier?, query: RepositoryListQuery): RepositoryListResponse =
        RepositoryListResponse(repositoryService.listRepositories(accessToken, query))

    fun getRepository(name: String) =
        repositoryService.repositoryProvider.getRepository(name)

    fun getRepositories(): Collection<Repository> =
        repositoryService.repositoryProvider.getRepositories()

    /** Usage figures of every repository as last computed, see [StorageUsageService]. */
    fun storageUsage(): StorageUsage =
        storageUsageService.current()

    /** Starts measuring the storage usage in the background, or joins a measurement that is already running. */
    fun refreshStorageUsage(): CompletableFuture<StorageUsage> =
        storageUsageService.refresh()

    override fun getLogger(): Logger =
        journalist.logger

}
