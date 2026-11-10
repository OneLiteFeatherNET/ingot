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

import net.onelitefeather.ingot.repository.index.ArtifactIndexSink
import com.reposilite.journalist.Journalist
import com.reposilite.journalist.Logger
import com.reposilite.maven.StoragePolicy.PRIORITIZE_UPSTREAM_METADATA
import com.reposilite.maven.api.METADATA_FILE
import com.reposilite.maven.api.MirrorIntegrityEvent
import com.reposilite.maven.application.MirroredRepositorySettings
import com.reposilite.plugin.Extensions
import com.reposilite.shared.ErrorResponse
import com.reposilite.shared.http.RemoteClient
import com.reposilite.shared.internalServer
import com.reposilite.shared.notFound
import com.reposilite.status.FailureFacade
import com.reposilite.storage.api.FileDetails
import com.reposilite.storage.api.Location
import com.reposilite.token.AccessTokenIdentifier
import panda.std.Blank
import panda.std.Result
import panda.std.Result.error
import panda.std.Result.ok
import io.javalin.http.HttpStatus.BAD_GATEWAY
import io.javalin.http.HttpStatus.FORBIDDEN
import io.javalin.http.HttpStatus.UNAUTHORIZED
import java.io.IOException
import java.io.InputStream
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

internal const val NO_EXTENSION_MARKER = "<none>"

/** The algorithm named by an integrity event when the host published no checksum file at all. */
private const val NO_ALGORITHM = "none"

/** A checksum file is one digest and a file name, so anything longer is not read as one. */
private const val MAX_CHECKSUM_FILE_BYTES = 256

/** The algorithm as the integrity events name it, such as `sha256`: the extension of its checksum file without the dot. */
private fun ChecksumAlgorithm.label(): String = extension.removePrefix(".")

internal class MirrorService(
    private val journalist: Journalist,
    private val failureFacade: FailureFacade,
    private val clock: Clock,
    private val mirrorCache: MirrorCacheService,
    private val artifactIndexSink: ArtifactIndexSink,
    private val operationCounters: MavenOperationCounters = MavenOperationCounters(),
    private val extensions: Extensions = Extensions(journalist),
) : Journalist {

    private data class FetchKey(
        val repository: String,
        val gav: Location,
        /**
         * Authenticated and anonymous callers are bucketed separately,
         * because a host configured `authenticatedFetchingOnly` is only reachable for authenticated callers.
         * Collapsing the two would let an anonymous "not found" poison the dedup result for a privileged caller.
         */
        val authenticated: Boolean,
    )

    private val inFlightFetches = ConcurrentHashMap<FetchKey, CompletableFuture<MirrorResolution<Unit>>>()

    fun shouldPrioritizeMirrorRepository(repository: Repository, gav: Location): Boolean =
        when {
            repository.storagePolicy == PRIORITIZE_UPSTREAM_METADATA && gav.getSimpleName().contains(METADATA_FILE) ->
                repository.metadataMaxAgeInSeconds <= 0 || !isMetadataFileValid(repository, gav)
            else ->
                false
        }

    private fun isMetadataFileValid(repository: Repository, gav: Location): Boolean =
        repository.storageProvider.getLastModifiedTime(gav)
            .map { it.toInstant().plus(repository.metadataMaxAgeInSeconds, ChronoUnit.SECONDS) }
            .matches { it.isAfter(Instant.now(clock)) }

    fun findRemoteDetails(
        repository: Repository,
        gav: Location,
        accessToken: AccessTokenIdentifier? = null,
        hosts: List<MirrorHost> = repository.mirrorHosts,
    ): MirrorResolution<FileDetails> =
        searchInRemoteRepositories(repository, gav, accessToken, hosts) { (host, config, client) ->
            client.head("${host.removeSuffix("/")}/$gav", config.authorization, config.connectTimeout, config.readTimeout)
        }

    fun findRemoteFile(
        repository: Repository,
        gav: Location,
        accessToken: AccessTokenIdentifier? = null,
        hosts: List<MirrorHost> = repository.mirrorHosts,
    ): MirrorResolution<InputStream> {
        // Single-flight only kicks in when every host stores; if any host streams without persisting,
        // waiters can't share the result and we fall back to per-caller fetches.
        if (hosts.isEmpty() || !hosts.all { it.configuration.store }) {
            return fetchFromRemoteHosts(repository, gav, accessToken, hosts)
        }

        val key = FetchKey(
            repository = repository.name,
            gav = gav,
            authenticated = accessToken != null,
        )

        return singleFlight(key, repository, gav) {
            fetchAndStoreFromRemoteHosts(repository, gav, accessToken, hosts)
        }.openLocal(repository, gav)
    }

    /**
     * Runs [fetch] once for [key] at a time. The first caller is the leader: it fetches on its own thread and completes
     * the shared future. Callers that arrive meanwhile wait for that future. The fetch never waits on a queued task, so
     * a fetch that itself needs another fetch (a group whose member is a proxy) cannot block the pool it runs on.
     */
    private fun singleFlight(
        key: FetchKey,
        repository: Repository,
        gav: Location,
        fetch: () -> MirrorResolution<Unit>,
    ): MirrorResolution<Unit> {
        val leader = CompletableFuture<MirrorResolution<Unit>>()
        inFlightFetches.putIfAbsent(key, leader)?.let { return it.join() }

        try {
            leader.complete(
                try {
                    fetch()
                } catch (throwable: Throwable) {
                    failureFacade.throwException("Mirror fetch ${repository.name}/$gav", throwable)
                    MirrorResolution.Failed(internalServer("Mirror fetch failed: ${throwable.message}"))
                }
            )
        } catch (throwable: Throwable) {
            // Followers must not hang on a leader that failed outside the fetch itself.
            leader.completeExceptionally(throwable)
            throw throwable
        } finally {
            inFlightFetches.remove(key, leader)
        }
        return leader.join()
    }

    private fun MirrorResolution<Unit>.openLocal(repository: Repository, gav: Location): MirrorResolution<InputStream> =
        when (this) {
            is MirrorResolution.Resolved -> repository.storageProvider.getFile(gav).fold({ MirrorResolution.Resolved(it, mirror) }, { MirrorResolution.Failed(it) })
            is MirrorResolution.Failed -> this
            MirrorResolution.NotFound -> MirrorResolution.NotFound
            MirrorResolution.NoEligibleHosts -> MirrorResolution.NoEligibleHosts
        }

    private fun fetchFromRemoteHosts(repository: Repository, gav: Location, accessToken: AccessTokenIdentifier?, hosts: List<MirrorHost>): MirrorResolution<InputStream> =
        searchInRemoteRepositories(repository, gav, accessToken, hosts) { (host, config, client) ->
            when {
                config.store -> fetchAndStoreFromHost(repository, gav, host, config, client)
                    .flatMap { repository.storageProvider.getFile(gav) }
                else -> client
                    .get("${host.removeSuffix("/")}/$gav", config.authorization, config.connectTimeout, config.readTimeout)
                    .mapErr { error -> error.updateMessage { "$host: $it" } }
            }
        }

    private fun fetchAndStoreFromRemoteHosts(repository: Repository, gav: Location, accessToken: AccessTokenIdentifier?, hosts: List<MirrorHost>): MirrorResolution<Unit> =
        searchInRemoteRepositories(repository, gav, accessToken, hosts) { (host, config, client) ->
            fetchAndStoreFromHost(repository, gav, host, config, client)
        }

    /**
     * Fetches an artifact from one host and stores it, under the checksum policy of the host. Metadata and checksum
     * files are never checked. Under [ChecksumPolicy.FAIL] the copy is verified while it is written, so a copy that does
     * not match is refused before the storage commits it.
     */
    private fun fetchAndStoreFromHost(repository: Repository, gav: Location, host: String, config: MirroredRepositorySettings, client: RemoteClient): Result<Unit, ErrorResponse> {
        val policy = if (gav.getSimpleName().isMetadataOrChecksumFileName()) ChecksumPolicy.IGNORE else config.checksumPolicy
        val response = client.get("${host.removeSuffix("/")}/$gav", config.authorization, config.connectTimeout, config.readTimeout)
            .mapErr { error -> error.updateMessage { "$host: $it" } }
        return when (policy) {
            ChecksumPolicy.IGNORE -> response
                .flatMap { data -> putMirroredFile(repository, gav, data, host) }
                .mapErr { error -> error.updateMessage { "$host: $it" } }
            ChecksumPolicy.WARN -> response
                .flatMap { data -> storeAndCompare(repository, gav, data, host, config, client) }
                .mapErr { error -> error.updateMessage { "$host: $it" } }
            ChecksumPolicy.FAIL -> response
                .flatMap { data -> storeVerified(repository, gav, data, host, config, client) }
        }
    }

    /** Stores the copy as it is, then compares it with the checksum of the same host and logs what differs. */
    private fun storeAndCompare(repository: Repository, gav: Location, data: InputStream, host: String, config: MirroredRepositorySettings, client: RemoteClient): Result<Unit, ErrorResponse> {
        val digest = DigestingInputStream(data)
        return putMirroredFile(repository, gav, digest, host)
            .peek {
                val expected = readExpectedChecksum(host, gav, config, client)
                val actual = expected?.let { digest.digestOf(it.algorithm) }
                when {
                    expected == null -> {
                        logger.warn("MirrorService | No checksum file for '$gav' on '$host', the copy is stored without a check")
                        raiseIntegrityEvent(repository, gav, host, ChecksumPolicy.WARN, ChecksumResult.MISSING, NO_ALGORITHM, CopyOutcome.STORED)
                    }
                    actual != null && actual != expected.hex -> {
                        logger.warn("MirrorService | Upstream checksum mismatch for '$gav' on '$host', the copy is stored as it is")
                        raiseIntegrityEvent(repository, gav, host, ChecksumPolicy.WARN, ChecksumResult.MISMATCH, expected.algorithm.label(), CopyOutcome.STORED)
                    }
                    actual != null -> operationCounters.recordChecksumResult(ChecksumPolicy.WARN, ChecksumResult.VERIFIED)
                }
            }
    }

    /**
     * Stores the copy only when it matches the checksum of the same host. The host is refused when it publishes no
     * checksum, and a copy that does not match is refused while it is read, before the storage commits it.
     */
    private fun storeVerified(repository: Repository, gav: Location, data: InputStream, host: String, config: MirroredRepositorySettings, client: RemoteClient): Result<Unit, ErrorResponse> {
        val expected = readExpectedChecksum(host, gav, config, client)
        if (expected == null) {
            data.close()
            raiseIntegrityEvent(repository, gav, host, ChecksumPolicy.FAIL, ChecksumResult.MISSING, NO_ALGORITHM, CopyOutcome.REFUSED)
            return refuseCopy(gav, host, "no checksum file")
        }
        val verifying = DigestingInputStream(data, expected)
        val stored = try {
            putMirroredFile(repository, gav, verifying, host)
        } catch (failure: IOException) {
            if (verifying.mismatched) return refuseMismatch(repository, gav, host, expected)
            return refuseCopy(gav, host, failure.message ?: "the copy could not be read")
        }
        return when {
            stored.isOk -> stored.also { operationCounters.recordChecksumResult(ChecksumPolicy.FAIL, ChecksumResult.VERIFIED) }
            verifying.mismatched -> refuseMismatch(repository, gav, host, expected)
            else -> stored
        }
    }

    /** A copy that does not match its checksum under fail is refused, and the mismatch is recorded as refused. */
    private fun refuseMismatch(repository: Repository, gav: Location, host: String, expected: ExpectedChecksum): Result<Unit, ErrorResponse> {
        raiseIntegrityEvent(repository, gav, host, ChecksumPolicy.FAIL, ChecksumResult.MISMATCH, expected.algorithm.label(), CopyOutcome.REFUSED)
        return refuseCopy(gav, host, "upstream copy does not match its ${expected.algorithm.extension} checksum")
    }

    /** Counts a mismatch or a missing checksum and raises the event that the audit log maps. */
    private fun raiseIntegrityEvent(repository: Repository, gav: Location, host: String, policy: ChecksumPolicy, result: ChecksumResult, algorithm: String, outcome: CopyOutcome) {
        operationCounters.recordChecksumResult(policy, result)
        extensions.emitEvent(MirrorIntegrityEvent(result, repository.name, gav.toString(), host, policy, algorithm, outcome))
    }

    private fun refuseCopy(gav: Location, host: String, reason: String): Result<Unit, ErrorResponse> {
        logger.warn("MirrorService | Refused copy of '$gav' from '$host': $reason")
        return error(ErrorResponse(BAD_GATEWAY, "Upstream copy could not be verified against its checksum"))
    }

    /** The first checksum file of [host] that holds a digest, SHA-256 before SHA-1. Null when the host has none. */
    private fun readExpectedChecksum(host: String, gav: Location, config: MirroredRepositorySettings, client: RemoteClient): ExpectedChecksum? =
        ChecksumAlgorithm.entries.firstNotNullOfOrNull { algorithm ->
            val response = client.get("${host.removeSuffix("/")}/$gav${algorithm.extension}", config.authorization, config.connectTimeout, config.readTimeout)
            if (!response.isOk) {
                return@firstNotNullOfOrNull null
            }
            val content = try {
                response.get().use { String(it.readNBytes(MAX_CHECKSUM_FILE_BYTES), Charsets.UTF_8) }
            } catch (failure: IOException) {
                return@firstNotNullOfOrNull null
            }
            parseChecksumFile(content, algorithm)?.let { ExpectedChecksum(algorithm, it) }
        }

    /**
     * Stores a fetched copy and records that its version directory holds a mirror copy, together with the upstream
     * [host] it came from. A directory that already held deployed artifacts is not recorded, and metadata files never
     * mark a directory.
     */
    private fun putMirroredFile(repository: Repository, gav: Location, data: InputStream, host: String): Result<Unit, ErrorResponse> {
        val directory = gav.getParent()
        val holdsHostedArtifacts = holdsArtifacts(repository, directory)
        val sourceHost = upstreamSourceHost(host)
        return repository.storageProvider.putFile(gav, data)
            .peek {
                if (!gav.getSimpleName().isMetadataOrChecksumFileName()) {
                    mirrorCache.recordStored(repository.name, directory.provenancePath(), holdsHostedArtifacts, sourceHost)
                }
                artifactIndexSink.indexChanged(repository.name, gav)
            }
    }

    private fun holdsArtifacts(repository: Repository, directory: Location): Boolean {
        if (directory == Location.empty()) {
            return false
        }
        val files = repository.storageProvider.getFiles(directory)
        return files.isOk && files.get().any { !it.getSimpleName().isMetadataOrChecksumFileName() }
    }

    private fun <V> searchInRemoteRepositories(
        repository: Repository,
        gav: Location,
        accessToken: AccessTokenIdentifier?,
        hosts: List<MirrorHost>,
        fetch: (MirrorHost) -> Result<V, ErrorResponse>,
    ): MirrorResolution<V> {
        val eligibleHosts = hosts
            .filter { !it.configuration.authenticatedFetchingOnly || accessToken != null }
            .filter { (_, config) ->
                isAllowed(config, gav).fold(
                    { true },
                    { reason ->
                        logger.debug("MirrorService | Cannot request '$gav' from remote repository '${repository.name}' (reason: illegal $reason)")
                        false
                    }
                )
            }
        if (eligibleHosts.isEmpty()) {
            return MirrorResolution.NoEligibleHosts
        }

        var allMissing = true
        var lastUpstreamError: ErrorResponse? = null
        for (host in eligibleHosts) {
            val result = fetch(host)
            operationCounters.recordUpstream(UpstreamOutcome.of(result))
            if (result.isOk) {
                return MirrorResolution.Resolved(result.get(), host)
            }
            if (result.error.status != 404) {
                allMissing = false
                lastUpstreamError = result.error.asRemoteFailure(host.host)
            }
        }
        return when {
            allMissing -> MirrorResolution.NotFound
            else -> MirrorResolution.Failed(lastUpstreamError ?: notFound("Cannot find '$gav' in remote repositories"))
        }
    }

    /**
     * A remote host that refuses the configured credentials is a failure of this instance, not of the caller. Relaying its
     * 401 or 403 would reach the browser as a refused session, so it is answered as a bad gateway.
     */
    private fun ErrorResponse.asRemoteFailure(host: String): ErrorResponse =
        if (status == UNAUTHORIZED.code || status == FORBIDDEN.code) ErrorResponse(BAD_GATEWAY, "$host refused the configured credentials") else this

    private enum class DisallowedReason {
        EXTENSION,
        GROUP
    }

    private fun isAllowed(config: MirroredRepositorySettings, gav: Location): Result<Blank, DisallowedReason> =
        isAllowedExtension(config, gav).flatMap { isAllowedGroup(config, gav) }

    private fun isAllowedExtension(config: MirroredRepositorySettings, gav: Location): Result<Blank, DisallowedReason> =
        when {
            config.allowedExtensions.none { it.isNotBlank() } -> ok()
            config.allowedExtensions.any { matchesExtension(it, gav) } -> ok()
            else -> error(DisallowedReason.EXTENSION)
        }

    private fun matchesExtension(entry: String, gav: Location): Boolean =
        when (val trimmed = entry.trim()) {
            NO_EXTENSION_MARKER -> gav.getExtension().isEmpty()
            else -> gav.endsWith(trimmed)
        }

    private fun isAllowedGroup(config: MirroredRepositorySettings, gav: Location): Result<Blank, DisallowedReason> =
        when {
            config.allowedGroups.none { it.isNotBlank() } -> ok()
            config.allowedGroups.any { gav.toString().startsWith(it.replace('.', '/')) } -> ok()
            else -> error(DisallowedReason.GROUP)
        }

    override fun getLogger(): Logger =
        journalist.logger

}
