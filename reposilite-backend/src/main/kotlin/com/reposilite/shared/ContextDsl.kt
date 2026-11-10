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

package com.reposilite.shared

import com.reposilite.journalist.Logger
import com.reposilite.shared.extensions.uri
import com.reposilite.token.AccessTokenFacade
import net.onelitefeather.ingot.identity.model.Action
import net.onelitefeather.ingot.readonly.ReadOnlyRoutes
import com.reposilite.token.api.AccessTokenDto
import io.javalin.http.Context
import io.javalin.http.HandlerType
import io.javalin.http.HttpStatus.FORBIDDEN
import io.javalin.http.HttpStatus.SERVICE_UNAVAILABLE
import panda.std.Result
import panda.std.asError
import panda.std.mapToUnit

class ContextDsl<R>(
    val logger: Logger,
    val ctx: Context,
    private val accessTokenFacade: AccessTokenFacade,
    private val authenticationResult: Lazy<Result<out AccessTokenDto, ErrorResponse>>,
    /** Whether the instance is read-only. Writes are refused with 503 once the caller is authenticated and authorized. */
    private val readOnly: () -> Boolean = { false },
) {

    private companion object {
        private val METHOD_ACTIONS = mapOf(
            HandlerType.HEAD to Action.READ,
            HandlerType.GET to Action.READ,
            HandlerType.PUT to Action.WRITE,
            HandlerType.POST to Action.WRITE,
            HandlerType.DELETE to Action.DELETE
        )

        private val WRITE_METHODS = setOf(HandlerType.PUT, HandlerType.POST, HandlerType.PATCH, HandlerType.DELETE)

        /** Seconds a client is told to wait before it retries a refused write. */
        private const val RETRY_AFTER_SECONDS = "30"
    }

    val uri = ctx.uri()

    /**
     * Response to send at the end of the dsl call
     */
    var response: Result<out R, ErrorResponse>? = null

    /**
     * Request was created by either anonymous user or through authenticated token
     */
    fun accessed(init: AccessTokenDto?.() -> Unit) {
        if (refuseWhileReadOnly()) return
        init(authenticationResult.value.orNull())
    }

    /**
     * Request was created by valid access token
     */
    fun authenticated(init: AccessTokenDto.() -> Unit) {
        authenticatedBeforeRefusal {
            if (!refuseWhileReadOnly()) init()
        }
    }

    /**
     * Request was created by valid access token and the token has access to the requested path
     */
    fun authorized(to: String = ctx.uri(), init: AccessTokenDto.() -> Unit) {
        // The read-only check runs after the authorization, so a caller without access still gets 401 or 403
        authenticatedBeforeRefusal {
            isAuthorized(to)
                .peek { if (!refuseWhileReadOnly()) init(this) }
                .onError { response = it.asError() }
        }
    }

    /**
     * Request was created by a principal that may administer [area], through the node `ingot.admin.<area>`. Without an
     * area the instance admin is required. Every admin endpoint names its area through [requireArea].
     */
    fun managerOnly(area: String? = null, block: AccessTokenDto.() -> Unit) {
        authenticatedBeforeRefusal {
            requireArea(area)
                .peek { if (!refuseWhileReadOnly()) block(this) }
                .onError { response = it.asError() }
        }
    }

    private fun authenticatedBeforeRefusal(init: AccessTokenDto.() -> Unit) {
        authenticationResult.value
            .peek { init(it) }
            .onError { response = it.asError() }
    }

    /**
     * Answers a write with 503 and Retry-After while the instance is read-only. Reads and the exempt routes pass. The check
     * is only called after the identity and the permission of the caller are known.
     */
    fun refuseWhileReadOnly(): Boolean {
        if (ctx.method() !in WRITE_METHODS || !readOnly()) return false
        if (ReadOnlyRoutes.isExempt(ctx.method().name, ctx.endpoint().path)) return false
        ctx.header("Retry-After", RETRY_AFTER_SECONDS)
        response = SERVICE_UNAVAILABLE.toErrorResponse("The instance is read-only, writes are refused until read-only mode is turned off").asError()
        return true
    }

    fun requireParameter(name: String): String =
        parameter(name)!!

    fun parameter(name: String): String? =
        ctx.pathParamMap()[name]

    fun queryParameter(name: String): String? =
        ctx.queryParam(name)

    /**
     * The bytes of the body, when the route read them within its body limit. Javalin reads the body itself otherwise.
     */
    internal var boundedBody: ByteArray? = null

    inline fun <reified T : Any> body() =
        parseBody(T::class.java)

    fun <T : Any> parseBody(type: Class<T>): T {
        val bytes = boundedBody ?: return ctx.bodyAsClass(type)
        return ctx.jsonMapper().fromJsonString(String(bytes, Charsets.UTF_8), type)
    }

    private fun isAuthorized(to: String): Result<Unit, ErrorResponse> =
        requireArea(null)
            .flatMapErr { _ ->
                authenticationResult.value
                    .filter(
                        { accessTokenFacade.hasAccessTo(it.identifier, to, METHOD_ACTIONS[ctx.method()]!!) },
                        { FORBIDDEN.toErrorResponse("This token is not authorized to access this path") }
                    )
                    .mapToUnit()
            }

    /**
     * The one admin check of the HTTP API: the principal may administer [area], the node `ingot.admin.<area>`, or it is an
     * instance admin when [area] is null. An instance admin is never restricted by an area denial, only by token narrowing.
     */
    fun requireArea(area: String?): Result<Unit, ErrorResponse> =
        authenticationResult.value
            .filter(
                { accessTokenFacade.hasAdminArea(it.identifier, area) },
                { FORBIDDEN.toErrorResponse(if (area == null) "Only instance admins can access this endpoint" else "Only principals with the $area area can access this endpoint") }
            )
            .mapToUnit()

    fun getSessionIdentifier(): String =
        authenticationResult.value.fold({ "${it.name}@${ctx.ip()}" }, { ctx.ip() })

    fun authentication(): Result<out AccessTokenDto, ErrorResponse> =
        authenticationResult.value

}
