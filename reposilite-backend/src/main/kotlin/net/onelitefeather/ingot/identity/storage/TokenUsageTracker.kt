/*
 * Copyright (c) 2026 OneLiteFeather
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
package net.onelitefeather.ingot.identity.storage

import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/** The stored last use of a persistent token. Both fields are null for a token that was never used. */
data class TokenUsage(
    val lastUsedAt: Instant?,
    val lastUsedIp: String?,
) {

    companion object {
        val NONE = TokenUsage(lastUsedAt = null, lastUsedIp = null)
    }

}

/**
 * Records and reads the use of persistent tokens. The access token facade depends on this interface and not on the
 * identity storage, so the authentication path does not know how the use is kept.
 */
interface TokenUsageSource {

    /** Records a successful authentication with the token. A use inside the throttle window writes nothing. */
    fun recordUsage(tokenId: Int, address: String?)

    fun lastUsedOf(tokenId: Int): TokenUsage?

}

/**
 * Writes the last use of a token and of its principal, at most once per [WRITE_INTERVAL] for each of them. The in-memory
 * guard keeps the authentication path free of writes between two stored uses. The database has the final word: the
 * conditional update writes only when the stored time is older than the window, so several instances sharing one database
 * cannot write more often than the window allows, and cannot move a stored time backwards.
 */
internal class TokenUsageTracker(private val store: IdentityStore) : TokenUsageSource {

    companion object {
        val WRITE_INTERVAL: Duration = Duration.ofMinutes(5)
    }

    private val lastTokenWrite = ConcurrentHashMap<Int, Instant>()
    private val lastPrincipalWrite = ConcurrentHashMap<Int, Instant>()

    override fun recordUsage(tokenId: Int, address: String?) {
        val now = store.now()
        if (!claim(lastTokenWrite, tokenId, now)) return

        val token = store.findTokenById(tokenId) ?: return
        val notBefore = now.minus(WRITE_INTERVAL)
        store.touchToken(tokenId, now, address, notBefore)
        if (claim(lastPrincipalWrite, token.principalId, now)) {
            store.touchPrincipal(token.principalId, now, notBefore)
        }
    }

    override fun lastUsedOf(tokenId: Int): TokenUsage? =
        store.findTokenById(tokenId)?.let { TokenUsage(lastUsedAt = it.lastUsedAt, lastUsedIp = it.lastUsedIp) }

    /** Whether [key] may be written at [now] under the window, and records the write if so. */
    private fun claim(writes: ConcurrentHashMap<Int, Instant>, key: Int, now: Instant): Boolean {
        var claimed = false
        writes.compute(key) { _, last ->
            if (last == null || Duration.between(last, now) >= WRITE_INTERVAL) {
                claimed = true
                now
            } else {
                last
            }
        }
        return claimed
    }

}
