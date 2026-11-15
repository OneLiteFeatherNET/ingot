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

package net.onelitefeather.ingot.format

import com.reposilite.plugin.api.Facade
import com.reposilite.shared.ContextDsl
import com.reposilite.shared.ErrorResponse
import com.reposilite.storage.api.Location
import com.reposilite.web.api.ReposiliteRoute
import com.reposilite.web.api.ReposiliteRoutes
import io.javalin.community.routing.Route.DELETE
import io.javalin.community.routing.Route.GET
import io.javalin.community.routing.Route.HEAD
import io.javalin.community.routing.Route.POST
import io.javalin.community.routing.Route.PUT
import net.onelitefeather.ingot.repository.RepositoryRegistry
import panda.std.Result
import panda.std.asError

/**
 * Serves `/{repository}/<gav>` for every format that is mounted on repository paths. A request is checked for its authentication
 * and authorization exactly as the Maven routes were, then handed to the handler of the repository's format. A repository that
 * does not exist is handled by the default format, so its answer is the one the Maven code gives.
 *
 * The routes have the patterns, the methods and the access checks of the Maven routes they replace, and they are registered at the
 * same point, so the order of matching does not change.
 */
@ExperimentalIngotFormatApi
class RepositoryRouteDispatcher(
    private val registry: RepositoryRegistry,
    private val formats: RepositoryFormats,
    private val context: FormatContext,
) : ReposiliteRoutes(), Facade {

    /** The handler of each installed format that is mounted on repository paths. Built when the first request arrives. */
    private val handlers: Map<String, RepositoryRequestHandler> by lazy {
        formats.all
            .filter { it.mount == FormatMount.RepositoryPath }
            .associate { format -> format.id to format.handler(context) }
    }

    /**
     * The handler that answers requests for [repository]: the handler of its format, or of the default format when no repository has
     * that name.
     */
    fun handlerFor(repository: String): RepositoryRequestHandler {
        val formatId = registry.repository(repository)?.format ?: RepositoryFormats.DEFAULT_ID
        return handlers[formatId]
            ?: error("No handler is mounted for the format '$formatId' of repository '$repository'")
    }

    /** Hands the request to the handler of its repository and returns the answer of that handler. */
    fun dispatch(request: RepositoryRequest): Result<Unit, ErrorResponse> =
        handlerFor(request.repository).handle(request)

    /** Reads answer what the handler wrote into the context, so their result is not sent again. */
    private val readRoute = ReposiliteRoute<Unit>("/{repository}/<gav>", HEAD, GET) {
        accessed {
            locationOf { gav ->
                dispatch(RepositoryRequest(ctx, this?.identifier, requireParameter("repository"), gav, getSessionIdentifier()))
            }
        }
    }

    /**
     * Writes are authorized on the canonical path and handled at the same repository and location, so the path that is judged
     * is the path that is written.
     */
    private val uploadRoute = ReposiliteRoute<Unit>("/{repository}/<gav>", POST, PUT) {
        canonicalPathOf { path ->
            authorized(path.authorizationPath) {
                response = dispatch(RepositoryRequest(ctx, this.identifier, path.repository, path.location, getSessionIdentifier()))
            }
        }
    }

    private val deleteRoute = ReposiliteRoute<Unit>("/{repository}/<gav>", DELETE) {
        canonicalPathOf { path ->
            authorized(path.authorizationPath) {
                response = dispatch(RepositoryRequest(ctx, this.identifier, path.repository, path.location, getSessionIdentifier()))
            }
        }
    }

    override val routes = routes(readRoute, uploadRoute, deleteRoute)

    /** A path that is not a valid location is answered as a bad request, as the Maven routes answered it. */
    private fun ContextDsl<Unit>.locationOf(block: (Location) -> Unit) {
        Location.ofRequest(requireParameter("gav"))
            .peek { block(it) }
            .onError { response = it.asError() }
    }

    /** A write path that has no canonical form is answered as a bad request before any authorization. */
    private fun ContextDsl<Unit>.canonicalPathOf(block: (RepositoryPath) -> Unit) {
        RepositoryPath.canonicalize(ctx.path().removePrefix(ctx.contextPath()))
            .peek { block(it) }
            .onError { response = it.asError() }
    }

}
