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

import com.reposilite.ReposiliteObjectMapper
import com.reposilite.shared.ErrorResponse
import com.reposilite.shared.extensions.PROBLEM_JSON
import com.reposilite.shared.extensions.prefersProblemJson
import java.nio.ByteBuffer
import org.eclipse.jetty.http.HttpHeader
import org.eclipse.jetty.server.Handler
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.Response
import org.eclipse.jetty.util.Callback

/**
 * Refuses a request at the Jetty level when the limit is reached, before Javalin routes it or reads its body.
 *
 * An admitted request keeps its slot until its completion callback fires, so a streamed body counts until it is written.
 * A refused request is answered with `503`, `Retry-After: 1` and the error body, as problem details when the client
 * prefers them.
 */
class RequestLimitHandler(
    private val limiter: RequestLimiter,
) : Handler.Wrapper() {

    override fun handle(request: Request, response: Response, callback: Callback): Boolean =
        when (val admission = limiter.admit(request.httpURI.path)) {
            is RequestLimiter.Admission.Exempt -> next(request, response, callback)

            is RequestLimiter.Admission.Admitted -> {
                val lease = admission.lease
                val handled = try {
                    next(request, response, Callback.from(callback, Runnable { lease.release() }))
                } catch (exception: Throwable) {
                    lease.release()
                    throw exception
                }
                // A handler that declines the request never completes the callback, so the slot is freed here
                if (!handled) {
                    lease.release()
                }
                handled
            }

            is RequestLimiter.Admission.Refused -> {
                refuse(request, response, callback)
                true
            }
        }

    private fun next(request: Request, response: Response, callback: Callback): Boolean =
        handler?.handle(request, response, callback) ?: false

    private fun refuse(request: Request, response: Response, callback: Callback) {
        val problem = prefersProblemJson(request.headers.get(HttpHeader.ACCEPT))
        val error = ErrorResponse(SERVICE_UNAVAILABLE, MESSAGE).also { it.instance = request.httpURI.path }
        val body = ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER.writeValueAsBytes(error)

        response.status = SERVICE_UNAVAILABLE
        response.headers.put(HttpHeader.RETRY_AFTER, RETRY_AFTER_SECONDS)
        response.headers.put(HttpHeader.CONTENT_TYPE, if (problem) PROBLEM_JSON else "application/json")
        response.headers.put(HttpHeader.VARY, "Accept")
        response.write(true, ByteBuffer.wrap(body), callback)
    }

    private companion object {
        const val SERVICE_UNAVAILABLE = 503
        const val RETRY_AFTER_SECONDS = "1"
        const val MESSAGE = "The server is busy with other requests, retry in a moment"
    }

}
