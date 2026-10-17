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
import com.reposilite.token.api.AccessTokenDto
import io.javalin.http.Context
import io.javalin.http.HandlerType
import io.javalin.http.HttpStatus.FORBIDDEN
import panda.std.Result
import panda.std.asError
import panda.std.mapToUnit

class ContextDsl<R>(
    val logger: Logger,
    val ctx: Context,
    private val accessTokenFacade: AccessTokenFacade,
    private val authenticationResult: Lazy<Result<out AccessTokenDto, ErrorResponse>>
) {

    private companion object {
        private val METHOD_ACTIONS = mapOf(
            HandlerType.HEAD to Action.READ,
            HandlerType.GET to Action.READ,
            HandlerType.PUT to Action.WRITE,
            HandlerType.POST to Action.WRITE,
            HandlerType.DELETE to Action.DELETE
        )
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
        init(authenticationResult.value.orNull())
    }

    /**
     * Request was created by valid access token
     */
    fun authenticated(init: AccessTokenDto.() -> Unit) {
        authenticationResult.value
            .peek { init(it) }
            .onError { response = it.asError() }
    }

    /**
     * Request was created by valid access token and the token has access to the requested path
     */
    fun authorized(to: String = ctx.uri(), init: AccessTokenDto.() -> Unit) {
        authenticated {
            isAuthorized(to)
                .peek { init(this) }
                .onError { response = it.asError() }
        }
    }

    /**
     * Request was created by a principal that may administer [area], through the node `ingot.admin.<area>`. Without an
     * area the instance admin is required. Every admin endpoint names its area through [requireArea].
     */
    fun managerOnly(area: String? = null, block: AccessTokenDto.() -> Unit) {
        authenticated {
            requireArea(area)
                .peek { block(this) }
                .onError { response = it.asError() }
        }
    }

    fun requireParameter(name: String): String =
        parameter(name)!!

    fun parameter(name: String): String? =
        ctx.pathParamMap()[name]

    fun queryParameter(name: String): String? =
        ctx.queryParam(name)

    inline fun <reified T : Any> body() =
        ctx.bodyAsClass(T::class.java)

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

    /** Whether the principal is an instance admin, the node ingot.admin.*. Kept for the endpoints not yet on [requireArea]. */
    fun isManager(): Result<Unit, ErrorResponse> = requireArea(null)

    fun getSessionIdentifier(): String =
        authenticationResult.value.fold({ "${it.name}@${ctx.ip()}" }, { ctx.ip() })

    fun authentication(): Result<out AccessTokenDto, ErrorResponse> =
        authenticationResult.value

}
