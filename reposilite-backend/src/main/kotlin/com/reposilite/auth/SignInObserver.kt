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

package com.reposilite.auth

import com.reposilite.audit.api.AuditAction
import com.reposilite.audit.api.AuditActor
import com.reposilite.audit.api.AuditEvent
import com.reposilite.plugin.api.Event
import com.reposilite.plugin.api.LoginThrottledEvent
import com.reposilite.plugin.api.NewSignInEvent
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong

/** The paths a sign-in can take. [wireName] is the name that the audit trail and the plugin events carry. */
enum class SignInPath(val wireName: String) {
    DASHBOARD("dashboard"),
    BASIC("basic"),
    BEARER("bearer"),
    CONSOLE("console"),
    MFA("mfa"),
}

enum class SignInOutcome {
    SUCCEEDED,
    FAILED,
    THROTTLED,
}

/** In-memory counts of sign-in outcomes per path. They hold no name and no address, and start again with the instance. */
class SignInCounters {

    private val cells: Map<SignInPath, Map<SignInOutcome, AtomicLong>> =
        SignInPath.entries.associateWith { SignInOutcome.entries.associateWith { AtomicLong() } }

    fun count(path: SignInPath, outcome: SignInOutcome): Long =
        cells.getValue(path).getValue(outcome).get()

    internal fun increment(path: SignInPath, outcome: SignInOutcome) {
        cells.getValue(path).getValue(outcome).incrementAndGet()
    }

}

/**
 * Observes sign-ins while the account throttle switch is on: it counts the outcomes, audits a delayed request as
 * `LOGIN_THROTTLED`, and publishes the plugin events. While the switch is off [enabled] is `false` and every call is a no-op,
 * so nothing is counted, written or published. A listener that fails is logged and never changes the answer of a request.
 */
class SignInObserver(
    val enabled: Boolean,
    private val audit: (AuditEvent) -> Unit,
    private val publish: (Event) -> Unit,
    private val warn: (String) -> Unit,
) {

    val counters = SignInCounters()

    /** Counts one outcome of [path]. */
    fun record(path: SignInPath, outcome: SignInOutcome) {
        if (enabled) {
            counters.increment(path, outcome)
        }
    }

    /**
     * A request answered with a delay: counted as throttled, audited once as `LOGIN_THROTTLED` with the path and no secret,
     * and announced with the time left. The caller keeps `LOGIN_FAILED` for the request while [enabled] is `false`.
     */
    fun throttled(name: String, address: String, path: SignInPath, retryAfterSeconds: Long) {
        if (!enabled) {
            return
        }
        counters.increment(path, SignInOutcome.THROTTLED)
        audit(
            AuditEvent(
                action = AuditAction.LOGIN_THROTTLED,
                actor = AuditActor.named(name, address),
                details = mapOf("path" to path.wireName),
                success = false,
            )
        )
        announce(LoginThrottledEvent::class.simpleName) {
            publish(LoginThrottledEvent(name = name, address = address, path = path.wireName, retryAfterSeconds = retryAfterSeconds))
        }
    }

    /** A successful sign-in from an address that is new for [name]. The user agent leaves only as its hash. */
    fun newSignIn(name: String, address: String, path: SignInPath, userAgent: String?) {
        if (!enabled) {
            return
        }
        announce(NewSignInEvent::class.simpleName) {
            publish(NewSignInEvent(name = name, address = address, path = path.wireName, userAgentHash = userAgent?.let(::sha256Hex)))
        }
    }

    private fun announce(event: String?, block: () -> Unit) {
        runCatching(block).onFailure { warn("A listener of ${event ?: "a plugin event"} failed: ${it.message}") }
    }

    companion object {

        /** The observer before the switch is read: it counts, audits and publishes nothing. */
        val OFF: SignInObserver = SignInObserver(enabled = false, audit = {}, publish = {}, warn = {})

        private fun sha256Hex(value: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(value.toByteArray(Charsets.UTF_8))
                .joinToString(separator = "") { "%02x".format(it) }

    }

}
