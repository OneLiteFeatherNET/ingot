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
@file:OptIn(net.onelitefeather.ingot.format.ExperimentalIngotFormatApi::class)

package com.reposilite.maven

import net.onelitefeather.ingot.repository.index.ArtifactIndexSink
import com.reposilite.journalist.Journalist
import com.reposilite.journalist.Logger
import com.reposilite.maven.api.DeleteEvent
import com.reposilite.maven.api.DeleteRequest
import com.reposilite.maven.api.DeployEvent
import com.reposilite.maven.api.DeployRequest
import com.reposilite.maven.api.Identifier
import com.reposilite.maven.api.LookupRequest
import com.reposilite.maven.api.PreResolveEvent
import com.reposilite.maven.api.RepositoryListQuery
import com.reposilite.maven.api.RepositoryMembersResponse
import com.reposilite.maven.api.RepositorySummary
import com.reposilite.maven.api.ResolvedDocument
import com.reposilite.maven.api.ResolvedFileDataEvent
import com.reposilite.maven.api.ResolvedFileEvent
import com.reposilite.maven.api.UploadChecksumMismatchEvent
import com.reposilite.plugin.Extensions
import com.reposilite.shared.ErrorResponse
import com.reposilite.shared.errorResponse
import com.reposilite.shared.notFound
import com.reposilite.shared.notFoundError
import com.reposilite.shared.unauthorizedError
import com.reposilite.statistics.StatisticsFacade
import com.reposilite.statistics.api.IncrementResolvedRequest
import com.reposilite.storage.api.DirectoryInfo
import com.reposilite.storage.api.DocumentInfo
import com.reposilite.storage.api.FileDetails
import com.reposilite.storage.api.FileType.DIRECTORY
import com.reposilite.storage.api.Location
import com.reposilite.storage.api.RepositoryDirectoryInfo
import com.reposilite.token.AccessTokenIdentifier
import io.javalin.http.HttpStatus.CONFLICT
import panda.std.Result
import panda.std.asSuccess
import panda.std.ok
import java.io.InputStream
import java.util.Optional

internal class RepositoryService(
    private val journalist: Journalist,
    val repositoryProvider: RepositoryProvider,
    private val securityProvider: RepositorySecurityProvider,
    private val mirrorService: MirrorService,
    private val resolutionProvider: ResolutionProvider,
    private val statisticsFacade: StatisticsFacade,
    private val extensions: Extensions,
    private val artifactIndexSink: ArtifactIndexSink,
) : Journalist {

    private val uploadChecksumVerifier = UploadChecksumVerifier(
        journalist = journalist,
        publish = { extensions.emitEvent(it) },
    )

    fun deployFile(deployRequest: DeployRequest): Result<Unit, ErrorResponse> =
        with(deployRequest) {
            when {
                repository.acceptsDeploymentOf(gav) ->
                    verifyUpload(repository, this)
                        .flatMap { content -> repository.storageProvider.putFile(gav, content) }
                        .peek { logger.info("DEPLOY | Artifact $gav successfully deployed to ${repository.name} by ${deployRequest.by}") }
                        .peek { resolutionProvider.invalidate(repository, gav) }
                        .peek { extensions.emitEvent(DeployEvent(repository, gav, deployRequest.by)) }
                        .flatMap { _ ->
                            when {
                                deployRequest.generateChecksums ->
                                    repository.storageProvider
                                        .getFile(gav)
                                        .peek { logger.info("DEPLOY | Generating checksums for $gav") }
                                        .flatMap { repository.writeFileChecksums(gav, it) }
                                        .peek { artifactIndexSink.indexChanged(repository.name, gav, repository.repositoryFormat.pathRules) }
                                else -> {
                                    logger.debug("DEPLOY | Skipping checksums generation for $gav")
                                    ok()
                                }
                            }
                        }
                else -> errorResponse(CONFLICT, "Redeployment is not allowed")
            }
        }

    /**
     * Checks a checksum file against the artifact stored before it, under the policy of the repository. Runs after the
     * redeployment check, so a conflict is still answered first, and before anything is written.
     */
    private fun verifyUpload(repository: Repository, deployRequest: DeployRequest): Result<InputStream, ErrorResponse> =
        with(deployRequest) {
            uploadChecksumVerifier.verify(
                repository = repository.name,
                policy = repository.uploadChecksumPolicy,
                gav = gav,
                by = by,
                content = content,
                storedFile = StoredFileReader { location ->
                    repository.storageProvider.takeIf { it.exists(location) }?.getFile(location)?.orNull()
                },
            )
        }

    fun deleteFile(deleteRequest: DeleteRequest): Result<Unit, ErrorResponse> =
        with(deleteRequest) {
            when {
                securityProvider.canDeleteResource(accessToken, repository, gav) -> deleteUnchecked(repository, gav, by, cleanupRunId)
                else -> unauthorizedError("Unauthorized access request")
            }
        }

    /**
     * Removes a file without checking an access token. Only server side jobs that act for the operator use it, such as
     * the scheduled cleanup policies; every request goes through [deleteFile].
     */
    internal fun deleteUnchecked(
        repository: Repository,
        gav: Location,
        by: String,
        cleanupRunId: String? = null,
    ): Result<Unit, ErrorResponse> =
        repository.storageProvider
            .removeFile(gav)
            .peek { logger.info("DELETE | File $gav has been deleted from ${repository.name} by $by") }
            .peek { resolutionProvider.invalidate(repository, gav) }
            .peek { extensions.emitEvent(DeleteEvent(repository, gav, by, cleanupRunId)) }

    fun findDetails(lookupRequest: LookupRequest): Result<FileDetails, ErrorResponse> =
        resolve(
            lookupRequest = lookupRequest,
            onDenied = { repository, gav, denied -> findDirectoryOnTheWayToReadableSubtree(lookupRequest.accessToken, repository, gav, denied) },
        ) { repository, gav -> findDetails(lookupRequest.accessToken, repository, gav) }

    /**
     * A directory the token may not read can still be opened when it lies on the way to a subtree the token may read through
     * artifact nodes. Only the directory of the repository itself answers; anything else, a file or a missing path, is
     * refused exactly as the read was, so the refusal reveals nothing about the path.
     */
    private fun findDirectoryOnTheWayToReadableSubtree(accessToken: AccessTokenIdentifier?, repository: Repository, gav: Location, denied: ErrorResponse): Result<FileDetails, ErrorResponse> =
        if (securityProvider.canBrowseResource(accessToken, repository, gav).isOk) {
            repository.storageProvider.getFileDetails(gav).orNull()
                ?.takeIf { it.type == DIRECTORY }
                ?.asSuccess()
                ?: Result.error(denied)
        } else {
            Result.error(denied)
        }

    fun findFile(lookupRequest: LookupRequest): Result<ResolvedDocument, ErrorResponse> =
        resolve(lookupRequest) { repository, gav ->
            findFile(lookupRequest.accessToken, repository, gav).map { (details, stream) ->
                ResolvedDocument(
                    document = details,
                    cachable = repository.acceptsCachingOf(gav),
                    content = stream
                )
            }
        }

    /** Runs [block] when the token may read the path. When it may not, [onDenied] answers; by default the answer is the refusal. */
    private fun <T> resolve(
        lookupRequest: LookupRequest,
        onDenied: (Repository, Location, ErrorResponse) -> Result<T, ErrorResponse> = { _, _, denied -> Result.error(denied) },
        block: (Repository, Location) -> Result<T, ErrorResponse>,
    ): Result<T, ErrorResponse> {
        val (accessToken, repositoryName, gav) = lookupRequest
        val repository = repositoryProvider.getRepository(lookupRequest.repository) ?: return notFoundError("Repository $repositoryName not found")

        val access = canAccessResource(lookupRequest.accessToken, repository.name, gav)
        if (access.isErr) {
            logger.debug("ACCESS | Unauthorized attempt of access (token: $accessToken) to $gav from ${repository.name}")
            return onDenied(repository, gav, access.error)
        }
        extensions.emitEvent(PreResolveEvent(accessToken, repository, gav))
        return block(repository, gav)
    }

    fun canAccessResource(accessToken: AccessTokenIdentifier?, repository: String, gav: Location): Result<Unit, ErrorResponse> =
        repositoryProvider.findRepository(repository)
            .flatMap { securityProvider.canAccessResource(accessToken, it, gav) }

    private fun findFile(accessToken: AccessTokenIdentifier?, repository: Repository, gav: Location): Result<Pair<DocumentInfo, InputStream>, ErrorResponse> =
        findDetails(accessToken, repository, gav)
            .`is`(DocumentInfo::class.java) { notFound("Requested file is a directory") }
            .flatMap { details -> findInputStream(repository, gav, accessToken).map { details to it } }
            .let { extensions.emitEvent(ResolvedFileEvent(accessToken, repository, gav, it)).result }

    fun findInputStream(lookupRequest: LookupRequest): Result<InputStream, ErrorResponse> =
        resolve(lookupRequest) { repository, gav -> findInputStream(repository, gav, lookupRequest.accessToken) }

    /**
     * [length] bytes from [offset] of a file held by the repository's own storage, or an empty answer when the file
     * has to come from a mirror (not stored yet, or mirrors take precedence). The caller then serves the whole file.
     */
    fun findInputStreamRange(lookupRequest: LookupRequest, offset: Long, length: Long): Result<Optional<InputStream>, ErrorResponse> =
        resolve(lookupRequest) { repository, gav ->
            when {
                // Without mirrors nothing can replace the stored file, so metadata is ranged like any other file
                (repository.mirrorHosts.isNotEmpty() && mirrorService.shouldPrioritizeMirrorRepository(repository, gav)) || !repository.storageProvider.exists(gav) ->
                    Result.ok<Optional<InputStream>, ErrorResponse>(Optional.empty())
                else ->
                    repository.storageProvider.getFile(gav, offset, length).map { Optional.of(it) }
            }
        }

    private fun findInputStream(repository: Repository, gav: Location, accessToken: AccessTokenIdentifier? = null): Result<InputStream, ErrorResponse> {
        val result = resolutionProvider.resolve(
            repository = repository,
            gav = gav,
            accessToken = accessToken,
            mirrorFirst = mirrorService.shouldPrioritizeMirrorRepository(repository, gav),
            tryLocal = { repository.storageProvider.getFile(gav) },
            tryRemote = { hosts -> mirrorService.findRemoteFile(repository, gav, accessToken, hosts) },
            isHostedMember = ::isHostedMember,
        )
        return extensions.emitEvent(ResolvedFileDataEvent(accessToken, repository, gav, result)).result
    }

    private fun findDetails(accessToken: AccessTokenIdentifier?, repository: Repository, gav: Location): Result<FileDetails, ErrorResponse> =
        resolutionProvider.resolve(
            repository = repository,
            gav = gav,
            accessToken = accessToken,
            mirrorFirst = mirrorService.shouldPrioritizeMirrorRepository(repository, gav),
            tryLocal = { findLocalDetails(accessToken, repository, gav) },
            tryRemote = { hosts -> mirrorService.findRemoteDetails(repository, gav, accessToken, hosts) },
            isHostedMember = ::isHostedMember,
        ).peek {
            recordResolvedRequest(Identifier(repository.name, gav.toString()), it, repository.repositoryFormat.pathRules.countsAsDownload(gav))
        }

    private fun findLocalDetails(accessToken: AccessTokenIdentifier?, repository: Repository, gav: Location): Result<FileDetails, ErrorResponse> =
        repository.storageProvider.getFileDetails(gav)
            .flatMap {
                it.takeIf { it.type == DIRECTORY }
                    ?.let { securityProvider.canBrowseResource(accessToken, repository, gav).map { _ -> it } }
                    ?: it.asSuccess()
            }

    /**
     * A member a protected group may ask: a loopback reference to a hosted repository, or to a group whose members are all
     * hosted, see [isHostedOnly]. An external reference, which is a proxy, is never hosted. A member that does not exist
     * is not hosted either, so it is skipped rather than guessed at.
     */
    private fun isHostedMember(mirror: MirrorHost): Boolean =
        mirror.client is RepositoryLoopbackClient &&
            repositoryProvider.getRepository(mirror.host)?.let { isHostedOnly(it, path = emptySet()) } == true

    /**
     * Whether [repository] holds only hosted content. A group is hosted when every member is hosted, checked recursively.
     * [path] holds the groups already on the way down; reaching one of them again is a loop, which is not hosted, so a
     * cycle of groups ends here and does not count as a source of its own. Proxies and mixed repositories are not hosted.
     */
    private fun isHostedOnly(repository: Repository, path: Set<String>): Boolean =
        when (repository.effectiveType) {
            EffectiveRepositoryType.HOSTED -> true
            EffectiveRepositoryType.GROUP ->
                repository.name !in path &&
                    repository.mirrorHosts.isNotEmpty() &&
                    repository.mirrorHosts.all { mirror ->
                        mirror.client is RepositoryLoopbackClient &&
                            repositoryProvider.getRepository(mirror.host)?.let { isHostedOnly(it, path + repository.name) } == true
                    }
            EffectiveRepositoryType.PROXY, EffectiveRepositoryType.MIXED -> false
        }

    private fun recordResolvedRequest(identifier: Identifier, fileDetails: FileDetails, countsAsDownload: Boolean) {
        if (fileDetails is DocumentInfo && countsAsDownload) {
            statisticsFacade.incrementResolvedRequest(IncrementResolvedRequest(identifier))
        }
    }

    /**
     * Lists the repositories the given token may see and that match the query, sorted by id. Visibility is applied
     * before the query, so a filter cannot reveal a repository the caller is not allowed to list.
     */
    fun listRepositories(accessToken: AccessTokenIdentifier?, query: RepositoryListQuery): List<RepositorySummary> =
        repositoryProvider.getRepositories()
            .filter { securityProvider.canListRepository(accessToken, it) }
            .map { RepositorySummary(id = it.name, visibility = it.visibility, description = it.description, tags = it.tags, type = it.effectiveType.wireName, typeExplicit = it.typeExplicit) }
            .filter { query.matches(it) }
            .sortedBy { it.id }

    /**
     * Lists the repositories a group references through loopback mirrors, in configuration order. The group itself
     * has to be browsable by the token, and every reference goes through the same visibility rule as the repository
     * list, so a reference the caller may not list is left out. External proxies are never part of the answer.
     */
    fun findMembers(accessToken: AccessTokenIdentifier?, repositoryName: String): Result<RepositoryMembersResponse, ErrorResponse> =
        repositoryProvider.findRepository(repositoryName)
            .flatMap { repository ->
                securityProvider.canBrowseResource(accessToken, repository, Location.empty())
                    .map {
                        RepositoryMembersResponse(
                            members = if (repository.effectiveType.hasMembers) {
                                repository.mirrorHosts
                                    .filter { it.client is RepositoryLoopbackClient }
                                    .mapNotNull { repositoryProvider.getRepository(it.host) }
                                    .filter { securityProvider.canListRepository(accessToken, it) }
                                    .map { it.name }
                                    .distinct()
                            } else {
                                emptyList()
                            }
                        )
                    }
            }

    /**
     * Lists the repositories the given token is allowed to see, each with its visibility.
     *
     * Exposing the visibility leaks nothing: entries the caller may not reach are dropped by
     * [RepositorySecurityProvider.canAccessRepository] before the response is assembled, so an
     * anonymous caller only ever learns that public repositories are public.
     */
    fun getRootDirectory(accessToken: AccessTokenIdentifier?): DirectoryInfo =
        repositoryProvider.getRepositories()
            .filter { securityProvider.canAccessRepository(accessToken, it) }
            .map { RepositoryDirectoryInfo(name = it.name, visibility = it.visibility) }
            .let { DirectoryInfo("/", it) }

    /** Only a group, and a mixed repository, proxy local repositories, so only they have members. */
    private val EffectiveRepositoryType.hasMembers: Boolean
        get() = this == EffectiveRepositoryType.GROUP || this == EffectiveRepositoryType.MIXED

    override fun getLogger(): Logger =
        journalist.logger

}
