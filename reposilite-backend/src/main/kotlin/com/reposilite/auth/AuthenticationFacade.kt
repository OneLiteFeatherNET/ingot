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

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.Expiry
import com.github.benmanes.caffeine.cache.Cache as CaffeineCache
import com.github.benmanes.caffeine.cache.Ticker as CaffeineTicker
import com.google.common.cache.Cache
import com.google.common.base.Ticker
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
import com.reposilite.shared.errorResponse
import com.reposilite.shared.notFoundError
import com.reposilite.shared.unauthorizedError
import io.javalin.http.HttpStatus
import com.reposilite.token.AccessTokenFacade
import net.onelitefeather.ingot.identity.evaluation.PermissionEvaluator
import com.reposilite.token.AccessTokenIdentifier
import com.reposilite.token.api.AccessTokenDto
import net.onelitefeather.ingot.identity.storage.AuthorizationRevision
import panda.std.Result
import panda.std.asSuccess
import panda.std.reactive.Reference
import panda.std.reactive.Subscriber
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.SortedSet
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.TimeUnit.NANOSECONDS

/** A successful authentication and the policy revision the replica had seen when it was verified. */
private data class CachedAuthentication(
    val accessToken: AccessTokenDto,
    val revision: Long,
)

private data class FailedAttempt(
    val count: Int,
    /** Clock time of the attempt that reached the limit, null while the limit has not been reached. */
    val limitReachedMillis: Long?,
    /** Clock time of the last failed attempt, from which the lifetime of the record is counted. */
    val writtenAtMillis: Long,
)

class AuthenticationFacade(
    private val journalist: Journalist,
    private val authenticators: SortedSet<Authenticator>,
    private val accessTokenFacade: AccessTokenFacade,
    private val bruteForceProtectionSettings: Reference<BruteForceProtectionSettings> = Reference.reference(BruteForceProtectionSettings()),
    private val extensions: Extensions = Extensions(journalist),
    private val clock: Clock = Clock.systemUTC(),
    private val ticker: Ticker = Ticker.systemTicker(),
    /** The policy revision of this replica. A cached credential is dropped once the revision it was cached under has moved. */
    private val authorizationRevision: AuthorizationRevision? = null,
) : Journalist, Facade {

    companion object {
        /**
         * Upper bound for a cached successful authentication, counted from the moment it was cached. Measured from the
         * write and not from the last hit, so that a credential in continuous use is checked again once per bound.
         */
        val CREDENTIAL_CACHE_MAX_AGE: Duration = Duration.ofMinutes(1)

        /** Lower bound for the lifetime of a failure record, which is longer whenever the ban is longer. */
        private val FAILED_ATTEMPT_MIN_LIFETIME: Duration = Duration.ofHours(1)
    }

    /**
     * Successful authentications, keyed by the presented credentials. An entry is only a shortcut: every change of its token
     * made on this instance drops the entries of that token at once (see [forgetCachedCredentials]), and every hit is checked
     * against the expiry of the token. Other instances are not told about a change directly: an entry is also dropped once the
     * policy revision it was cached under has moved, so a change made elsewhere is refused within one refresh interval. Without
     * a revision source, their entries can keep working for up to one minute after the change was made elsewhere.
     */
    private val authenticationCache: Cache<Credentials, CachedAuthentication> =
        CacheBuilder
            .newBuilder()
            .maximumSize(16)
            .concurrencyLevel(2)
            .expireAfterWrite(CREDENTIAL_CACHE_MAX_AGE.toNanos(), NANOSECONDS)
            .ticker(ticker)
            .build()

    /**
     * Guards the pairing of a cache write with the generation it was computed under, so that a result read before a
     * change cannot be cached after that change dropped the entries.
     */
    private val cacheLock = Any()
    private val tokenGeneration = AtomicLong()

    /** The account guard of the login plugin. Read on every request, so an installed guard applies at once. */
    @Volatile
    private var accountGuard: AccountLoginGuard = AccountLoginGuard.NONE

    /** The sign-in observer of the login plugin. Until one is installed, nothing is counted, audited or announced. */
    @Volatile
    private var installedSignInObserver: SignInObserver = SignInObserver.OFF

    /** The failure counter of this instance. It is used unless a shared counter is installed. */
    private val localFailedAddresses = LocalFailedAddresses()

    /** The shared failure counter of the login tables. Installed whenever there is a database, see [installSharedFailedAddresses]. */
    @Volatile
    private var sharedFailedAddresses: FailedAddressStore? = null

    private fun failedAddresses(): FailedAddressStore =
        sharedFailedAddresses ?: localFailedAddresses

    /** The ban rules, read on every use, so that a changed setting applies at once. */
    private fun failedAttemptRules(): FailedAddressRules =
        bruteForceProtectionSettings.map {
            val banDuration = Duration.ofSeconds(it.banDurationSeconds)
            FailedAddressRules(
                maxAttempts = it.maxAttempts,
                banDuration = banDuration,
                lifetime = maxOf(FAILED_ATTEMPT_MIN_LIFETIME, banDuration),
            )
        }

    /**
     * The failure counter of one instance. Records sit in a Caffeine cache on the injected clock. A record lives at least as
     * long as the current ban, so it is never forgotten while the ban it started is still running, also when the ban duration
     * is changed at runtime.
     */
    private inner class LocalFailedAddresses : FailedAddressStore {

        private val expiry = object : Expiry<String, FailedAttempt> {

            override fun expireAfterCreate(key: String, value: FailedAttempt, currentTime: Long): Long =
                failedAttemptRules().lifetime.toNanos()

            override fun expireAfterUpdate(key: String, value: FailedAttempt, currentTime: Long, currentDuration: Long): Long =
                failedAttemptRules().lifetime.toNanos()

            override fun expireAfterRead(key: String, value: FailedAttempt, currentTime: Long, currentDuration: Long): Long {
                val remainingMillis = value.writtenAtMillis + failedAttemptRules().lifetime.toMillis() - clock.millis()
                return maxOf(0L, remainingMillis) * 1_000_000L
            }

        }

        private val attempts: CaffeineCache<String, FailedAttempt> by lazy {
            Caffeine
                .newBuilder()
                .ticker(CaffeineTicker { clock.millis() * 1_000_000L })
                .expireAfter(expiry)
                .build()
        }

        override fun read(address: String, now: Instant, rules: FailedAddressRules): FailedAddressAttempt? =
            attempts.getIfPresent(address)?.toAttempt(rules)

        override fun countFailure(address: String, now: Instant, rules: FailedAddressRules): FailedAddressAttempt {
            val current = attempts.getIfPresent(address)
            val count = (current?.count ?: 0) + 1
            val nowMillis = now.toEpochMilli()
            val updated = FailedAttempt(
                count = count,
                limitReachedMillis = if (count >= rules.maxAttempts) current?.limitReachedMillis ?: nowMillis else null,
                writtenAtMillis = nowMillis,
            )
            attempts.put(address, updated)
            return updated.toAttempt(rules)
        }

        override fun forget(address: String) {
            attempts.invalidate(address)
        }

        /** Rewrites every record, so that its lifetime is counted again with the current ban duration. */
        fun refreshLifetimes() {
            attempts.asMap().forEach { (address, attempt) -> attempts.put(address, attempt) }
        }

        private fun FailedAttempt.toAttempt(rules: FailedAddressRules): FailedAddressAttempt =
            FailedAddressAttempt(
                count = count,
                bannedUntil = limitReachedMillis?.let { Instant.ofEpochMilli(it).plus(rules.banDuration) },
                writtenAt = Instant.ofEpochMilli(writtenAtMillis),
            )

    }

    /**
     * Installs the shared failure counter of the login tables, so that every replica on the database counts the same
     * failures. Until one is installed, each instance counts its own failures in memory.
     */
    fun installSharedFailedAddresses(store: FailedAddressStore) {
        sharedFailedAddresses = store
    }

    init {
        accessTokenFacade.onTokenChanged { forgetCachedCredentials(it) }
        // A longer ban must also keep the records that were written before the change
        bruteForceProtectionSettings.subscribe(Subscriber { localFailedAddresses.refreshLifetimes() })
    }

    fun registerAuthenticator(authenticator: Authenticator) {
        this.authenticators.add(authenticator)
    }

    /**
     * Installs the account guard of the login plugin. Until one is installed, no name is delayed.
     */
    fun installAccountGuard(guard: AccountLoginGuard) {
        accountGuard = guard
    }

    /** Installs the sign-in observer of the login plugin. Read on every request, so an installed observer applies at once. */
    fun installSignInObserver(observer: SignInObserver) {
        installedSignInObserver = observer
    }

    /** The sign-in observer currently installed, which the status endpoint reads its counters from. */
    fun signInObserver(): SignInObserver =
        installedSignInObserver

    /**
     * Authenticates a Basic, Bearer or console credential. A [guarded] request is first checked against the account guard,
     * and a delayed name is answered `429` without its secret being checked. [onThrottled] receives the seconds to wait, so
     * that the caller can send them as `Retry-After`. The loopback mirror is not guarded, and is neither counted nor announced.
     * [path] and [userAgent] only describe the request to the sign-in observer.
     */
    fun authenticateByCredentials(
        credentials: Credentials,
        guarded: Boolean = true,
        path: SignInPath = SignInPath.BASIC,
        userAgent: String? = null,
        onThrottled: (retryAfterSeconds: Long) -> Unit = {},
    ): Result<out AccessTokenDto, ErrorResponse> {
        val observer = if (guarded) installedSignInObserver else SignInObserver.OFF

        if (isBruteForceBlocked(credentials.ip)) {
            recordRejectedLogin(credentials)
            observer.record(path, SignInOutcome.FAILED)
            return unauthorizedError("Invalid authorization credentials")
        }

        val guard = if (guarded) accountGuard else AccountLoginGuard.NONE
        guard.retryAfter(credentials.name, credentials.ip)?.let { wait ->
            val retryAfterSeconds = ((wait.toMillis() + 999) / 1000).coerceAtLeast(1)
            onThrottled(retryAfterSeconds)
            if (observer.enabled) {
                observer.throttled(credentials.name, credentials.ip, path, retryAfterSeconds)
            } else {
                recordRejectedLogin(credentials)
            }
            return errorResponse(HttpStatus.TOO_MANY_REQUESTS, "Too many failed login attempts, try again later")
        }

        val generation = tokenGeneration.get()
        val revisionAtStart = currentRevision()
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
                    ?.peek { cacheAuthentication(credentials, it, generation, revisionAtStart) }
                ?: unauthorizedError("Invalid authorization credentials")

        // Asked before recordSuccess, which records the address as known
        val newAddress = result.isOk && guard.isNewAddress(credentials.name, credentials.ip)
        trackBruteForceAttempt(ip = credentials.ip, success = result.isOk)
        if (result.isErr) {
            recordRejectedLogin(credentials)
            observer.record(path, SignInOutcome.FAILED)
            guard.recordFailure(credentials.name)
        } else {
            observer.record(path, SignInOutcome.SUCCEEDED)
            if (newAddress) {
                observer.newSignIn(credentials.name, credentials.ip, path, userAgent)
            }
            guard.recordSuccess(credentials.name, credentials.ip)
        }
        // Cache hits count as use too: the facade throttles the write, so a busy cache does not write more often.
        result.peek { accessTokenFacade.recordTokenUsage(it.identifier, credentials.ip) }
        return result
    }

    private fun cachedAuthentication(credentials: Credentials): AccessTokenDto? {
        val cached = authenticationCache.getIfPresent(credentials) ?: return null
        if (cached.revision != currentRevision() || cached.accessToken.isExpired(clock)) {
            authenticationCache.invalidate(credentials)
            return null
        }
        return cached.accessToken
    }

    /**
     * The revision this replica has seen, or zero without a revision source. It is read before the authentication runs, so a
     * revocation that commits during the check cannot be cached as valid under a newer revision than the check saw.
     */
    private fun currentRevision(): Long =
        authorizationRevision?.lastSeen ?: 0L

    private fun cacheAuthentication(credentials: Credentials, accessToken: AccessTokenDto, generation: Long, revision: Long) {
        synchronized(cacheLock) {
            if (tokenGeneration.get() == generation) {
                authenticationCache.put(credentials, CachedAuthentication(accessToken, revision))
            }
        }
    }

    /**
     * Called after every change of a token. Drops the cached credentials of that name, whichever secret or address they
     * were presented with, and bumps the generation so that an authentication still in flight is not cached.
     */
    /**
     * Forgets every cached credential of this instance. Used when a second factor changes: a directory login that was accepted
     * before the change must be checked again, because the factor now decides it.
     */
    fun forgetAllCachedCredentials() {
        synchronized(cacheLock) {
            tokenGeneration.incrementAndGet()
            authenticationCache.invalidateAll()
        }
    }

    internal fun forgetCachedCredentials(tokenName: String) {
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

        val rules = failedAttemptRules()
        val store = failedAddresses()
        val now = clock.instant()
        val attempt = store.read(ip, now, rules) ?: return false

        if (attempt.count < rules.maxAttempts) {
            return false
        }

        val bannedUntil = attempt.bannedUntil ?: return false

        if (now.isBefore(bannedUntil)) {
            return true // Host is still blocked
        }

        store.forget(ip) // Ban duration has passed, unblocking the host
        return false
    }

    private fun trackBruteForceAttempt(ip: String, success: Boolean) {
        if (bruteForceProtectionSettings.map { !it.enabled }) {
            return
        }

        val rules = failedAttemptRules()
        val store = failedAddresses()
        val now = clock.instant()

        if (success) {
            val current = store.read(ip, now, rules)
            // A valid login from a shared address must not lift a ban that is still running
            if (current == null || !current.isBannedAt(now)) {
                store.forget(ip)
            }
            return
        }

        val updated = store.countFailure(ip, now, rules)

        if (updated.count == rules.maxAttempts) {
            logger.warn("Brute force protection: IP $ip has been blocked after ${rules.maxAttempts} failed authentication attempts")
        }
    }

    /** Whether the ban of this record is still running at [now]. */
    private fun FailedAddressAttempt.isBannedAt(now: Instant): Boolean =
        bannedUntil?.let { now.isBefore(it) } ?: false

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
                    nodes = PermissionEvaluator.effectiveNodes(subject),
                    instanceAdmin = PermissionEvaluator.isInstanceAdmin(subject),
                    grants = subject.levels.flatMap { it.grants }.map { grant ->
                        SessionGrant(team = grant.team, repository = grant.repository, pathPrefix = grant.pathPrefix, role = grant.role.name)
                    },
                )
            }
            ?.asSuccess()
            ?: notFoundError("Token $identifier not found")

    override fun getLogger(): Logger =
        journalist.logger

}
