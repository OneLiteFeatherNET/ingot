/*
 * Copyright (c) 2026 dzikoysk
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

import com.reposilite.journalist.Journalist
import com.reposilite.journalist.Logger
import com.reposilite.maven.api.ProxyRefusedEvent
import com.reposilite.plugin.Extensions
import com.reposilite.plugin.api.ResolutionInvalidatedEvent
import com.reposilite.shared.ErrorResponse
import com.reposilite.shared.notFoundError
import com.reposilite.storage.api.Location
import com.reposilite.token.AccessTokenIdentifier
import io.javalin.http.HttpStatus.TOO_MANY_REQUESTS
import panda.std.Result

internal class ResolutionProvider(
    private val journalist: Journalist,
    private val extensions: Extensions,
    private val operationCounters: MavenOperationCounters,
) : Journalist {

    private data class ResolveAttempt<T>(
        val result: Result<T, ErrorResponse>,
        val remote: MirrorResolution<T>?,
    )

    /**
     * Resolves [gav] from the local storage and then from the mirrored hosts. For a coordinate under a protected prefix of
     * the [repository], only the hosts that [isHostedMember] accepts are asked; the others are never contacted.
     */
    fun <T : Any> resolve(
        repository: Repository,
        gav: Location,
        accessToken: AccessTokenIdentifier?,
        mirrorFirst: Boolean,
        tryLocal: () -> Result<T, ErrorResponse>,
        tryRemote: (hosts: List<MirrorHost>) -> MirrorResolution<T>,
        isHostedMember: (MirrorHost) -> Boolean,
    ): Result<T, ErrorResponse> {
        val cache = repository.resolutionCache
        val authenticated = accessToken != null
        val cached = cache?.lookup(gav, authenticated)
        val isMetadata = repository.repositoryFormat.pathRules.isResolutionMetadata(gav)
        val notFoundMessage = "Cannot find '$gav' in local or remote repositories"

        val candidates = (cached as? ResolutionCache.Origin.Remote)
            ?.let { decision -> repository.mirrorHosts.filter { it.host == decision.host }.ifEmpty { repository.mirrorHosts } }
            ?: repository.mirrorHosts

        val protectedPrefix = NamespaceProtection.firstCovering(repository.protectedGroupPrefixes, gav)
        val hosts = if (protectedPrefix != null) candidates.filter(isHostedMember) else candidates
        val skipped = if (protectedPrefix != null) candidates.filterNot(isHostedMember) else emptyList()

        // A cached miss of a coordinate with a skipped proxy is a refusal again, so every refused request is counted
        if (isMetadata && cached == ResolutionCache.Origin.MissingMetadata) {
            recordRefusal(repository, gav, accessToken, protectedPrefix, skipped)
            return notFoundError(notFoundMessage)
        }

        // Without a hosted member left, the proxy is not asked at all, and the answer is the miss of an unresolved coordinate
        val lookup: (List<MirrorHost>) -> MirrorResolution<T> =
            if (skipped.isNotEmpty() && hosts.isEmpty()) { _ -> MirrorResolution.NotFound } else tryRemote

        val attempt = when {
            cached is ResolutionCache.Origin.Remote || mirrorFirst -> tryRemoteFirst(tryLocal, lookup, hosts, notFoundMessage)
            else -> tryLocalFirst(tryLocal, lookup, hosts, notFoundMessage)
        }

        // A refusal changed the outcome only when a proxy was skipped and the hosted members came back empty
        if (attempt.isRefusal()) {
            recordRefusal(repository, gav, accessToken, protectedPrefix, skipped)
        }

        if (cache != null && isMetadata) {
            attempt.recordTo(cache, gav, authenticated)
        }

        return attempt.result
    }

    fun invalidate(repository: Repository, gav: Location): Boolean {
        if (repository.repositoryFormat.pathRules.isResolutionMetadata(gav)) {
            repository.resolutionCache?.invalidate(gav)
            extensions.emitEvent(ResolutionInvalidatedEvent(repository.name, gav.toString()))
            return true
        }

        return false
    }

    /** The request failed and the remote side answered with a miss, not with a failure of an upstream. */
    private fun <T> ResolveAttempt<T>.isRefusal(): Boolean =
        !result.isOk && (remote is MirrorResolution.NotFound || remote is MirrorResolution.NoEligibleHosts)

    /** Counts and raises one refusal, when the coordinate is protected and at least one proxy member was skipped for it. */
    private fun recordRefusal(repository: Repository, gav: Location, accessToken: AccessTokenIdentifier?, protectedPrefix: String?, skipped: List<MirrorHost>) {
        if (protectedPrefix == null || skipped.isEmpty()) {
            return
        }
        operationCounters.recordNamespaceRefusal(repository.name)
        extensions.emitEvent(ProxyRefusedEvent(repository.name, gav, accessToken, protectedPrefix, skipped.map { it.host }))
    }

    private fun <T : Any> tryLocalFirst(
        tryLocal: () -> Result<T, ErrorResponse>,
        tryRemote: (List<MirrorHost>) -> MirrorResolution<T>,
        hosts: List<MirrorHost>,
        notFoundMessage: String,
    ): ResolveAttempt<T> =
        tryLocal().let { local ->
            when {
                local.isOk -> ResolveAttempt(local, remote = null)
                else -> {
                    val remote = tryRemote(hosts)
                    val result = when {
                        // A failing or throttled upstream is not a miss: answer with its error instead of the local not found
                        remote is MirrorResolution.Failed && (remote.error.status == TOO_MANY_REQUESTS.code || remote.error.status >= 500) -> Result.error(remote.error)
                        else -> remote.toResult(notFoundMessage).flatMapErr { local }
                    }
                    ResolveAttempt(result, remote)
                }
            }
        }

    private fun <T : Any> tryRemoteFirst(
        tryLocal: () -> Result<T, ErrorResponse>,
        tryRemote: (List<MirrorHost>) -> MirrorResolution<T>,
        hosts: List<MirrorHost>,
        notFoundMessage: String,
    ): ResolveAttempt<T> =
        when (val remote = tryRemote(hosts)) {
            is MirrorResolution.Resolved -> ResolveAttempt(Result.ok(remote.value), remote)
            else -> ResolveAttempt(tryLocal().flatMapErr { remote.toResult(notFoundMessage) }, remote)
        }

    private fun <T> ResolveAttempt<T>.recordTo(cache: ResolutionCache, gav: Location, authenticated: Boolean) {
        val prefix = gav.getParent()
        if (prefix == Location.empty()) {
            return
        }

        // Successful resolve but upstream actually failed — we served a local fallback.
        // Log it for visibility, but do not cache: the upstream may recover.
        if (result.isOk && remote is MirrorResolution.Failed) {
            logger.warn("Resolution | Upstream failed (${remote.error.status}: ${remote.error.message}) — served '$gav' from local fallback")
            return
        }

        // Failed resolve: only NotFound is a stable signal worth caching. Everything else is transient.
        if (!result.isOk) {
            if (remote is MirrorResolution.NotFound) {
                cache.record(prefix, authenticated, ResolutionCache.Origin.MissingMetadata)
            }
            return
        }

        // Successful resolve: pin the host only when the mirror won't keep a local copy; otherwise we served locally.
        val origin = when (remote) {
            is MirrorResolution.Resolved if !remote.mirror.configuration.store -> ResolutionCache.Origin.Remote(remote.mirror.host)
            else -> ResolutionCache.Origin.Local
        }

        cache.record(prefix, authenticated, origin)
    }

    override fun getLogger(): Logger =
        journalist.logger

}
