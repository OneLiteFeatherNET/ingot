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

package net.onelitefeather.ingot.web

import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Counts the requests in progress against the limit. It never waits: a request that finds no free slot is refused at
 * once. A slot is held by a [Lease], which the caller releases when the response is complete, not when the handler
 * returns, so a streamed body keeps its slot until its last byte is written.
 *
 * The liveness and readiness paths are exempt and never take a slot, whatever the load.
 */
class RequestLimiter(
    val limit: Int,
    basePath: String = "/",
) {

    private val permits = Semaphore(limit)
    private val prefix = basePath.trimEnd('/')

    /** The requests in progress that hold a slot. */
    val inFlight: Int
        get() = limit - permits.availablePermits()

    fun admit(path: String): Admission {
        if (relativePath(path) in EXEMPT_PATHS) {
            return Admission.Exempt
        }

        if (!permits.tryAcquire()) {
            return Admission.Refused
        }

        return Admission.Admitted(Lease())
    }

    private fun relativePath(path: String): String =
        if (prefix.isNotEmpty() && path.startsWith("$prefix/")) path.removePrefix(prefix) else path

    sealed interface Admission {

        /** The request is served without a slot. */
        object Exempt : Admission

        /** No slot is free. The request is answered at once with the refusal. */
        object Refused : Admission

        /** The request holds a slot until [Lease.release] is called. */
        class Admitted(val lease: Lease) : Admission

    }

    /** A slot held by one request. Releasing it more than once frees nothing more. */
    inner class Lease {

        private val released = AtomicBoolean(false)

        fun release() {
            if (released.compareAndSet(false, true)) {
                permits.release()
            }
        }

    }

    companion object {

        /** The probe paths that are answered whatever the load. Matched exactly, so no longer path counts as one. */
        val EXEMPT_PATHS: Set<String> = setOf("/api/status/health", "/api/status/ready")

    }

}
