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
package net.onelitefeather.ingot.jobs

import com.reposilite.plugin.api.jobs.JobPage
import com.reposilite.plugin.api.jobs.JobQuery
import com.reposilite.shared.ErrorResponse
import com.reposilite.shared.notFoundError
import com.reposilite.shared.toErrorResponse
import com.reposilite.token.AccessTokenFacade
import com.reposilite.token.api.AccessTokenDto
import com.reposilite.web.api.ReposiliteRoute
import com.reposilite.web.api.ReposiliteRoutes
import com.reposilite.web.api.SESSION_BEARER_SCHEME
import com.reposilite.web.api.SESSION_COOKIE_SCHEME
import com.reposilite.web.api.TOKEN_BASIC_SCHEME
import io.javalin.community.routing.Route.GET
import io.javalin.http.HttpStatus.BAD_REQUEST
import io.javalin.http.HttpStatus.FORBIDDEN
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiContent
import io.javalin.openapi.OpenApiParam
import io.javalin.openapi.OpenApiResponse
import io.javalin.openapi.OpenApiSecurity
import panda.std.Result

/**
 * The Jobs page API: one paged list of the runs of every visible source, and the detail of one run. Both are read only; a run is
 * cancelled on its source page. A source the caller may not read is absent from every answer.
 */
internal class JobsEndpoints(
    private val aggregator: JobAggregator,
    private val accessTokens: AccessTokenFacade,
) : ReposiliteRoutes() {

    @OpenApi(
        security = [OpenApiSecurity(name = TOKEN_BASIC_SCHEME), OpenApiSecurity(name = SESSION_BEARER_SCHEME), OpenApiSecurity(name = SESSION_COOKIE_SCHEME)],
        tags = ["Jobs"],
        path = "/api/jobs",
        methods = [HttpMethod.GET],
        summary = "List background runs",
        description = "The runs of every source the caller may read, newest first, in pages of at most 100. Filters: kind, state and trigger, each may repeat. Pass the nextCursor back as cursor for the next page.",
        responses = [
            OpenApiResponse(status = "200", content = [OpenApiContent(from = JobPage::class)]),
            OpenApiResponse(status = "400", description = "An unknown state or trigger, or a malformed cursor"),
            OpenApiResponse(status = "401", description = "The caller is not authenticated"),
            OpenApiResponse(status = "403", description = "The caller may read none of the sources (ingot.admin.maintenance or ingot.admin.*)"),
        ]
    )
    private val list = ReposiliteRoute<JobPage>("/api/jobs", GET) {
        authenticated {
            val unknownState = ctx.queryParams("state").firstOrNull { value -> JobState.entries.none { it.name == value } }
            val unknownTrigger = ctx.queryParams("trigger").firstOrNull { value -> JobTrigger.entries.none { it.name == value } }
            when {
                unknownState != null -> response = Result.error(BAD_REQUEST.toErrorResponse("Unknown job state: $unknownState"))
                unknownTrigger != null -> response = Result.error(BAD_REQUEST.toErrorResponse("Unknown job trigger: $unknownTrigger"))
                else -> {
                    val query = JobQuery(
                        kinds = ctx.queryParams("kind").toSet(),
                        states = ctx.queryParams("state").mapTo(mutableSetOf()) { JobState.valueOf(it) },
                        triggers = ctx.queryParams("trigger").mapTo(mutableSetOf()) { JobTrigger.valueOf(it) },
                    )
                    val size = queryParameter("size")?.toIntOrNull()
                    val cursor = queryParameter("cursor")?.takeIf { it.isNotBlank() }
                    response = guarded { aggregator.page(query, accessOf(this), size, cursor) }
                }
            }
        }
    }

    @OpenApi(
        security = [OpenApiSecurity(name = TOKEN_BASIC_SCHEME), OpenApiSecurity(name = SESSION_BEARER_SCHEME), OpenApiSecurity(name = SESSION_COOKIE_SCHEME)],
        tags = ["Jobs"],
        path = "/api/jobs/{id}",
        methods = [HttpMethod.GET],
        pathParams = [OpenApiParam(name = "id", description = "The run id, <source>:<local id>, URL-encoded", required = true)],
        summary = "Get one background run",
        description = "The counts and the failures of one run. A run of a source the caller may not read answers 404, as a run that does not exist.",
        responses = [
            OpenApiResponse(status = "200", content = [OpenApiContent(from = JobRun::class)]),
            OpenApiResponse(status = "401", description = "The caller is not authenticated"),
            OpenApiResponse(status = "404", description = "The run does not exist or the caller may not read its source"),
        ]
    )
    private val detail = ReposiliteRoute<JobRun>("/api/jobs/{id}", GET) {
        authenticated {
            val run = aggregator.detail(requireParameter("id"), accessOf(this))
            response = if (run != null) Result.ok(run) else notFoundError("The job run does not exist or is not visible to the caller")
        }
    }

    override val routes = routes(list, detail)

    private fun accessOf(caller: AccessTokenDto) = JobAccess { node ->
        accessTokens.hasAdminArea(caller.identifier, areaOf(node))
    }

    /** The area of a node: `ingot.admin.maintenance` is the area maintenance, `ingot.admin.*` is the instance admin (null). */
    private fun areaOf(node: String): String? = node.removePrefix("ingot.admin.").takeUnless { it == "*" }

    private fun <T> guarded(action: () -> T): Result<T, ErrorResponse> = try {
        Result.ok(action())
    } catch (exception: JobsForbiddenException) {
        Result.error(FORBIDDEN.toErrorResponse(exception.message))
    } catch (exception: IllegalArgumentException) {
        Result.error(BAD_REQUEST.toErrorResponse(exception.message))
    }

}
