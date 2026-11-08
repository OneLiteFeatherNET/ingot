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

package com.reposilite.web.api

import com.reposilite.shared.ContextDsl
import com.reposilite.web.infrastructure.ReposiliteDslRoute
import com.reposilite.web.routing.RouteMethod
import io.javalin.community.routing.Route
import io.javalin.community.routing.dsl.DefaultDslRoute
import io.javalin.community.routing.dsl.DslContainer
import io.javalin.community.routing.dsl.DslRoute
import panda.std.asError

abstract class ReposiliteRoutes : DslContainer<DslRoute<ContextDsl<*>, Unit>, ContextDsl<*>, Unit> {

    abstract val routes: Set<ReposiliteRoute<*>>

    @Suppress("UNCHECKED_CAST")
    fun routes(vararg reposiliteRoutes: ReposiliteRoute<*>): Set<ReposiliteRoute<Any>> =
        reposiliteRoutes
            .map { it as ReposiliteRoute<Any> }
            .toSet()

    override fun routes(): Collection<ReposiliteDslRoute> =
        routes.flatMap { it.toDslRoutes() }

}

class ReposiliteRoute<R>(
    val path: String,
    vararg val methods: Route,
    /** Largest request body the route reads; a larger one is answered with 413 before the handler runs. */
    val bodyLimit: Long? = null,
    val handler: ContextDsl<R>.() -> Unit
) {

    @Deprecated("Use io.javalin.community.routing.Route instead of RouteMethod")
    constructor(
        path: String,
        vararg methods: RouteMethod,
        handler: ContextDsl<R>.() -> Unit
    ) : this(
        path = path,
        methods = methods
            .map { Route.valueOf(it.name) }
            .toTypedArray(),
        handler = handler
    )

    @Suppress("UNCHECKED_CAST")
    fun toDslRoutes(): Set<ReposiliteDslRoute> {
        val route = handler as ContextDsl<*>.() -> Unit
        val guarded = bodyLimit?.let { limit -> withBodyLimit(limit, route) } ?: route
        return methods.mapTo(HashSet()) { method ->
            DefaultDslRoute(
                path = path,
                method = method,
                handler = guarded
            )
        }
    }

    /** Reads the body within [limit] before [route] runs, and hands the bytes to the route's body parsing. */
    private fun withBodyLimit(limit: Long, route: ContextDsl<*>.() -> Unit): ContextDsl<*>.() -> Unit = {
        readBoundedBody(ctx.req().contentLengthLong, ctx.bodyInputStream(), limit)
            .onError { response = it.asError() }
            .peek { bytes ->
                boundedBody = bytes
                route()
            }
    }

}
