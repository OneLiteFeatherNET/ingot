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

package net.onelitefeather.ingot.search.backend

import com.reposilite.shared.ErrorResponse
import io.javalin.http.HttpStatus.SERVICE_UNAVAILABLE

/** Seconds a client should wait before it asks again: the `Retry-After` of every 503 of a backend that is not available. */
const val SEARCH_BACKEND_RETRY_AFTER_SECONDS: Long = 30

/** The title of the problem answer of a backend that is not available (RFC 9457). */
const val SEARCH_BACKEND_UNAVAILABLE_TITLE: String = "Search backend not available"

private val USER_INFORMATION = Regex("://[^/\\s]*@")

/**
 * The problem answer of a backend that is not available (search backend registration, D4). The detail names the backend and
 * the reason; [redact] is applied to both first, so that no answer shows a password or a secret read by the backend.
 */
internal fun backendUnavailableProblem(
    backendId: String,
    state: SearchBackendState,
    reason: String,
    redact: (String) -> String = ::redactBackendDetail,
): ErrorResponse {
    val detail = "The search backend `${redact(backendId)}` is ${state.phrase()}: ${redact(reason)}"
    return ErrorResponse(SERVICE_UNAVAILABLE.code, detail).also { it.title = SEARCH_BACKEND_UNAVAILABLE_TITLE }
}

/** Removes the user information of every URL in [text], keeping the scheme, the host and the rest. */
internal fun redactBackendDetail(text: String): String = text.replace(USER_INFORMATION, "://***@")

private fun SearchBackendState.phrase(): String = when (this) {
    SearchBackendState.AVAILABLE -> "available"
    SearchBackendState.NOT_INSTALLED -> "not installed"
    SearchBackendState.REFUSED -> "refused"
    SearchBackendState.OPEN_FAILED -> "not open, its last attempt failed"
    SearchBackendState.DOWN -> "down"
    SearchBackendState.CLUSTER_MISMATCH -> "not the backend of this cluster"
}
