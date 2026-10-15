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

package com.reposilite.auth

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.reposilite.audit.api.AuditAction
import com.reposilite.audit.api.AuditActor
import com.reposilite.audit.api.AuditEvent
import com.reposilite.auth.api.Credentials
import com.reposilite.auth.api.SessionDetails
import com.reposilite.auth.api.SessionGrant
import com.reposilite.auth.api.SessionPrincipal
import com.reposilite.auth.application.BruteForceProtectionSettings
import com.reposilite.journalist.Journalist
import com.reposilite.journalist.Logger
import com.reposilite.plugin.Extensions
import com.reposilite.plugin.api.Facade
import com.reposilite.shared.ErrorResponse
import com.reposilite.shared.notFoundError
import com.reposilite.shared.unauthorizedError
import com.reposilite.token.AccessTokenFacade
import com.reposilite.token.AccessTokenIdentifier
import com.reposilite.token.api.AccessTokenDto
import panda.std.Result
import panda.std.asSuccess
import panda.std.reactive.Reference
import java.time.Clock
import java.util.SortedSet
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.TimeUnit.HOURS
import java.util.concurrent.TimeUnit.MINUTES

private data class FailedAttempt(
    val count: Int,
    val firstFailureMillis: Long
)

class AuthenticationFacade(
    private val journalist: Journalist,
    private val authenticators: SortedSet<Authenticator>,
    private val accessTokenFacade: AccessTokenFacade,
    private val bruteForceProtectionSettings: Reference<BruteForceProtectionSettings> = Reference.reference(BruteForceProtectionSettings()),
    private val extensions: Extensions = Extensions(journalist),
    private val clock: Clock = Clock.systemUTC(),
) : Journalist, Facade {

    /**
     * Successful authentications, keyed by the presented credentials. An entry is only a shortcut: every change of its token
     * made on this instance drops the entries of that token at once (see [forgetCachedCredentials]), and every hit is checked
     * against the expiry of the token. Other instances of a cluster are not told about a change, so their entries can keep
     * working for up to one minute after the change was made elsewhere.
     */
    private val authenticationCache: Cache<Credentials, AccessTokenDto> =
        CacheBuilder
            .newBuilder()
            .maximumSize(16)
            .concurrencyLevel(2)
            .expireAfterAccess(1, MINUTES)
            .build()

    /**
     * Guards the pairing of a cache write with the generation it was computed under, so that a result read before a
     * change cannot be cached after that change dropped the entries.
     */
    private val cacheLock = Any()
    private val tokenGeneration = AtomicLong()

    private val failedAttempts: Cache<String, FailedAttempt> by lazy {
        CacheBuilder
            .newBuilder()
            .concurrencyLevel(2)
            .expireAfterWrite(1, HOURS)
            .build()
        }

    init {
        accessTokenFacade.onTokenChanged { forgetCachedCredentials(it) }
    }

    fun registerAuthenticator(authenticator: Authenticator) {
        this.authenticators.add(authenticator)
    }

    fun authenticateByCredentials(credentials: Credentials): Result<out AccessTokenDto, ErrorResponse> {
        if (isBruteForceBlocked(credentials.ip)) {
            recordRejectedLogin(credentials)
            return unauthorizedError("Invalid authorization credentials")
        }

        val generation = tokenGeneration.get()
        val result =
            cachedAuthentication(credentials)
                ?.asSuccess()
                ?: authenticators
                    .asSequence()
                    .filter { it.enabled() }
                    .map { authenticator ->
                        authenticator
                            .authenticate(credentials)
                            .onError { logger.debug("${credentials.name}@${credentials.ip} failed to authenticate with ${authenticator.realm()} realm due to $it") }
                    }
                    .firstOrNull { it.isOk }
                    ?.peek { cacheAuthentication(credentials, it, generation) }
                ?: unauthorizedError("Invalid authorization credentials")

        trackBruteForceAttempt(ip = credentials.ip, success = result.isOk)
        if (result.isErr) {
            recordRejectedLogin(credentials)
        }
        return result
    }

    private fun cachedAuthentication(credentials: Credentials): AccessTokenDto? {
        val cached = authenticationCache.getIfPresent(credentials) ?: return null
        if (cached.isExpired(clock)) {
            authenticationCache.invalidate(credentials)
            return null
        }
        return cached
    }

    private fun cacheAuthentication(credentials: Credentials, accessToken: AccessTokenDto, generation: Long) {
        synchronized(cacheLock) {
            if (tokenGeneration.get() == generation) {
                authenticationCache.put(credentials, accessToken)
            }
        }
    }

    /**
     * Called after every change of a token. Drops the cached credentials of that name, whichever secret or address they
     * were presented with, and bumps the generation so that an authentication still in flight is not cached.
     */
    private fun forgetCachedCredentials(tokenName: String) {
        synchronized(cacheLock) {
            tokenGeneration.incrementAndGet()
            authenticationCache.asMap().keys.removeIf { it.name == tokenName }
        }
    }

    /**
     * Only the attempted name and address are recorded. The secret is part of the credentials and
     * stays out of the audit log.
     */
    private fun recordRejectedLogin(credentials: Credentials) {
        extensions.emitEvent(
            AuditEvent(
                action = AuditAction.LOGIN_FAILED,
                actor = AuditActor.named(credentials.name, credentials.ip),
                success = false,
            )
        )
    }

    private fun isBruteForceBlocked(ip: String): Boolean {
        if (bruteForceProtectionSettings.map { !it.enabled }) {
            return false
        }

        val attempt = failedAttempts.getIfPresent(ip) ?: return false

        if (attempt.count < bruteForceProtectionSettings.map { it.maxAttempts }) {
            return false
        }

        val banDuration = bruteForceProtectionSettings.map { it.banDurationSeconds } * 1000
        val trackedDuration = System.currentTimeMillis() - attempt.firstFailureMillis

        if (trackedDuration < banDuration) {
            return true // Host is still blocked
        }

        failedAttempts.invalidate(ip) // Ban duration has passed, unblocking the host
        return false
    }

    private fun trackBruteForceAttempt(ip: String, success: Boolean) {
        if (bruteForceProtectionSettings.map { !it.enabled }) {
            return
        }

        if (success) {
            failedAttempts.invalidate(ip)
            return
        }

        val current = failedAttempts.getIfPresent(ip)
        val updated =
            FailedAttempt(
                count = (current?.count ?: 0) + 1,
                firstFailureMillis = current?.firstFailureMillis ?: System.currentTimeMillis()
            )
        failedAttempts.put(ip, updated)

        if (updated.count == bruteForceProtectionSettings.map { it.maxAttempts }) {
            logger.warn("Brute force protection: IP $ip has been blocked after ${bruteForceProtectionSettings.map { it.maxAttempts }} failed authentication attempts")
        }
    }

    fun getSessionDetails(identifier: AccessTokenIdentifier): Result<SessionDetails, ErrorResponse> =
        accessTokenFacade.getAccessTokenById(identifier)
            ?.let {
                val subject = accessTokenFacade.subjectOf(it.identifier)
                SessionDetails(
                    accessToken = it,
                    permissions = accessTokenFacade.getPermissions(it.identifier),
                    routes = accessTokenFacade.getRoutes(it.identifier),
                    principal = SessionPrincipal(id = subject.principalId, kind = subject.kind.name, name = subject.principalName ?: it.name),
                    teams = subject.teams,
                    grants = subject.grants.map { grant ->
                        SessionGrant(team = grant.team, repository = grant.repository, pathPrefix = grant.pathPrefix, role = grant.role.name)
                    },
                )
            }
            ?.asSuccess()
            ?: notFoundError("Token $identifier not found")

    override fun getLogger(): Logger =
        journalist.logger

}
