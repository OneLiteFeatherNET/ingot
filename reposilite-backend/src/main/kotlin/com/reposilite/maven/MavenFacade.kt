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
import com.reposilite.journalist.Journalist
import com.reposilite.journalist.Logger
import com.reposilite.maven.api.Checksum
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
import com.reposilite.maven.api.RepositoryMembersResponse
import com.reposilite.maven.api.ResolvedDocument
import com.reposilite.maven.api.SaveMetadataRequest
import com.reposilite.maven.api.VersionLookupRequest
import com.reposilite.maven.api.VersionsResponse
import com.reposilite.plugin.api.Facade
import com.reposilite.plugin.api.StorageUsageRefreshedEvent
import com.reposilite.shared.ErrorResponse
import com.reposilite.shared.errorResponse
import com.reposilite.shared.http.CacheScope
import com.reposilite.storage.api.DirectoryInfo
import com.reposilite.storage.api.DocumentInfo
import com.reposilite.storage.api.FileDetails
import com.reposilite.storage.api.Location
import com.reposilite.token.AccessTokenIdentifier
import io.javalin.http.HttpStatus.METHOD_NOT_ALLOWED
import panda.std.Result
import com.reposilite.shared.http.ConditionalRequests
import java.io.InputStream
import java.time.Instant
import java.util.Optional
import java.util.concurrent.CompletableFuture

class MavenFacade internal constructor(
    private val journalist: Journalist,
    private val repositorySecurityProvider: RepositorySecurityProvider,
    private val repositoryProvider: RepositoryProvider,
    private val metadataService: MetadataService,
    private val latestService: LatestService,
    private val storageUsageService: StorageUsageService,
    private val mirrorCache: MirrorCacheService,
    private val artifactIndexSink: ArtifactIndexSink,
    private val cachePrivateAsPublic: Boolean,
) : Journalist, Facade {

    private val repositoryService = repositoryProvider.repositoryService

    fun findDetails(lookupRequest: LookupRequest): Result<out FileDetails, ErrorResponse> =
        repositoryService.findDetails(lookupRequest).map { details -> withListableFiles(lookupRequest, details) }

    /** A directory lists only what artifact nodes let the token see, when they take part in reading its repository. */
    private fun withListableFiles(lookupRequest: LookupRequest, details: FileDetails): FileDetails {
        val directory = details as? DirectoryInfo ?: return details
        val repository = getRepository(lookupRequest.repository) ?: return details
        return repositorySecurityProvider.filterByArtifactNodes(lookupRequest.accessToken, repository, lookupRequest.gav, directory.files)
            ?.let { DirectoryInfo(directory.name, it) }
            ?: details
    }

    fun findFile(lookupRequest: LookupRequest): Result<ResolvedDocument, ErrorResponse> =
        repositoryService.findFile(lookupRequest)
            .peek { touchMirrorCopy(lookupRequest) }

    fun findData(lookupRequest: LookupRequest): Result<InputStream, ErrorResponse> =
        repositoryService.findInputStream(lookupRequest)
            .peek { touchMirrorCopy(lookupRequest) }

    fun findDataRange(lookupRequest: LookupRequest, offset: Long, length: Long): Result<Optional<InputStream>, ErrorResponse> =
        repositoryService.findInputStreamRange(lookupRequest, offset, length)
            .peek { touchMirrorCopy(lookupRequest) }

    /**
     * The strong entity tag of a stored artifact: its stored checksum (`.sha256`, then `.sha1`, then `.md5`) when there is
     * one, otherwise a tag from its size and modification time. Metadata and other files that are not cached change in
     * place, so they skip the extra read and use the size and time tag.
     */
    fun findEntityTag(lookupRequest: LookupRequest, details: DocumentInfo): String? {
        val repository = getRepository(lookupRequest.repository)
        val fallback = ConditionalRequests.etag(details.contentLength, details.lastModifiedTime)
        if (repository == null || !repository.acceptsCachingOf(lookupRequest.gav)) {
            return fallback
        }

        return ENTITY_TAG_CHECKSUMS
            .firstNotNullOfOrNull { checksum ->
                val location = lookupRequest.gav.resolveSibling("${lookupRequest.gav.getSimpleName()}.${checksum.extension}")
                if (!repository.storageProvider.exists(location)) {
                    return@firstNotNullOfOrNull null
                }
                repository.storageProvider.getFile(location)
                    .map { stream -> stream.use { it.readNBytes(CHECKSUM_READ_LIMIT).decodeToString() } }
                    .orNull()
                    ?.trim()
                    ?.substringBefore(' ')
                    ?.takeIf { it.isNotEmpty() && it.all { char -> char.isLetterOrDigit() } }
                    ?.let { "\"$it\"" }
            }
            ?: fallback
    }

    /**
     * Answers 405 when the repository is a typed proxy or group, which take no uploads. Hosted and untyped repositories pass.
     * Mirror copies never go through here, so a proxy keeps caching. Maintenance such as a cleanup run is not an upload either,
     * and is not checked by the HTTP routes that delete.
     */
    fun checkAcceptsUploads(repository: Repository): Result<Unit, ErrorResponse> =
        if (repository.acceptsUploads) {
            Result.ok(Unit)
        } else {
            errorResponse(METHOD_NOT_ALLOWED, "Repository '${repository.name}' is a ${repository.effectiveType.wireName} and does not accept uploads")
        }

    fun deployFile(deployRequest: DeployRequest): Result<Unit, ErrorResponse> =
        checkAcceptsUploads(deployRequest.repository)
            .flatMap { repositoryService.deployFile(deployRequest) }
            // a deployment makes the directory hold hosted content, so it is no longer a mirror copy
            .peek {
                val directory = deployRequest.gav.getParent().provenancePath()
                // only a dropped copy record changes the origin of indexed rows, so a plain deploy queues nothing (artifact search spec, 3.5)
                if (mirrorCache.forgetVersion(deployRequest.repository.name, directory)) {
                    artifactIndexSink.reorigin(deployRequest.repository.name, directory)
                }
            }

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
        checkAcceptsUploads(generatePomRequest.repository)
            .flatMap { metadataService.generatePom(generatePomRequest) }

    fun findMetadata(repository: Repository, gav: Location): Result<Metadata, ErrorResponse> =
        metadataService.findMetadata(repository, gav)

    /** Brings the version-level metadata of a SNAPSHOT directory in line with the files stored there, see [MetadataService.reconcileSnapshotVersion]. */
    internal fun reconcileSnapshotVersion(repository: Repository, versionDirectory: Location) =
        metadataService.reconcileSnapshotVersion(repository, versionDirectory)

    /** Runs [operation] under the metadata lock of the artifact directory, see [MetadataService.withMetadataLock]. */
    internal fun <T> withMetadataLock(repository: Repository, artifactDirectory: Location, operation: (MetadataLockScope) -> Result<T, ErrorResponse>): Result<T, ErrorResponse> =
        metadataService.withMetadataLock(repository, artifactDirectory, operation)

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
            repositorySecurityProvider.filterByArtifactNodes(request.accessToken, repository, request.gav, directoryInfo.files)
                ?: directoryInfo.files.filter {
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

    /** The scope of the Cache-Control value of a file answer from [repository], see [resolveCacheScope]. */
    fun cacheScopeOf(repository: String): CacheScope =
        resolveCacheScope(
            repository = repository,
            nodeOf = { name -> getRepository(name)?.cacheScopeNode() },
            privateAsPublic = cachePrivateAsPublic,
        )

    fun canAccessResource(accessToken: AccessTokenIdentifier?, repository: Repository, gav: Location): Result<Unit, ErrorResponse> =
        repositorySecurityProvider.canAccessResource(accessToken, repository, gav)

    fun findRepositories(accessToken: AccessTokenIdentifier?): DirectoryInfo =
        repositoryService.getRootDirectory(accessToken)

    fun listRepositories(accessToken: AccessTokenIdentifier?, query: RepositoryListQuery): RepositoryListResponse =
        RepositoryListResponse(repositoryService.listRepositories(accessToken, query))

    /** The repositories [repository] references through loopback mirrors, as far as the token may see them. */
    fun findMembers(accessToken: AccessTokenIdentifier?, repository: String): Result<RepositoryMembersResponse, ErrorResponse> =
        repositoryService.findMembers(accessToken, repository)

    fun getRepository(name: String) =
        repositoryService.repositoryProvider.getRepository(name)

    fun getRepositories(): Collection<Repository> =
        repositoryService.repositoryProvider.getRepositories()

    /** Bounds the lifetime of resolution entries, because a cluster bus is configured for this instance. See [resolutionCacheMaxAge]. */
    internal fun bindResolutionCacheToClusterBus() =
        repositoryService.repositoryProvider.bindToClusterBus()

    /** Usage figures of every repository as last computed, see [StorageUsageService]. */
    fun storageUsage(): StorageUsage =
        storageUsageService.current()

    /** Starts measuring the storage usage in the background, or joins a measurement that is already running. */
    fun refreshStorageUsage(): CompletableFuture<StorageUsage> =
        storageUsageService.refresh()

    /** Applies usage figures another instance computed, see [StorageUsageService.applyReceived]. */
    internal fun applyReceivedStorageUsage(event: StorageUsageRefreshedEvent): Boolean =
        storageUsageService.applyReceived(event)

    override fun getLogger(): Logger =
        journalist.logger

    private companion object {
        /** Strongest first. */
        val ENTITY_TAG_CHECKSUMS = listOf(Checksum.SHA256, Checksum.SHA1, Checksum.MD5)

        /** A hex digest and an optional file name fit well inside this. */
        const val CHECKSUM_READ_LIMIT = 256
    }

}
