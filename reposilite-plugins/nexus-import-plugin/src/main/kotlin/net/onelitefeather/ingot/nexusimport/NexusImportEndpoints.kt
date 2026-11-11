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
package net.onelitefeather.ingot.nexusimport

import com.reposilite.shared.ContextDsl
import com.reposilite.shared.ErrorResponse
import com.reposilite.shared.toErrorResponse
import com.reposilite.web.api.ReposiliteRoute
import com.reposilite.web.api.ReposiliteRoutes
import io.javalin.community.routing.Route.DELETE
import io.javalin.community.routing.Route.GET
import io.javalin.community.routing.Route.POST
import io.javalin.community.routing.Route.PUT
import io.javalin.http.ContentType
import io.javalin.http.Header
import io.javalin.http.HttpStatus.ACCEPTED
import io.javalin.http.HttpStatus.BAD_REQUEST
import io.javalin.http.HttpStatus.NO_CONTENT
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiContent
import io.javalin.openapi.OpenApiRequestBody
import io.javalin.openapi.OpenApiResponse
import net.onelitefeather.ingot.nexusimport.jobs.ConflictView
import net.onelitefeather.ingot.nexusimport.jobs.DiscoverReport
import net.onelitefeather.ingot.nexusimport.jobs.JobCreated
import net.onelitefeather.ingot.nexusimport.jobs.JobRequest
import net.onelitefeather.ingot.nexusimport.jobs.JobView
import net.onelitefeather.ingot.nexusimport.jobs.NexusImportService
import net.onelitefeather.ingot.nexusimport.jobs.ReportView
import net.onelitefeather.ingot.nexusimport.nexus.ConnectionReport
import net.onelitefeather.ingot.nexusimport.plan.LossReport
import panda.std.Result
import panda.std.asError

/** The body that supplies a password again. [toString] hides it. */
data class CredentialsRequest(val password: String = "") {
    override fun toString(): String = "CredentialsRequest(password=********)"
}

/**
 * The import routes under `/api/migration/nexus`. Every route needs instance admin, and none takes a credential in the query
 * string. A write answers `503` while read-only mode is on, through the same check as every other manager route.
 */
internal class NexusImportEndpoints(
    private val connections: NexusConnectionService,
    private val service: NexusImportService,
) : ReposiliteRoutes() {

    @OpenApi(
        tags = ["Migration"],
        path = "/api/migration/nexus/connection/test",
        methods = [HttpMethod.POST],
        summary = "Probes a Nexus Repository 3 with a credential: reachability, version, credential and readable areas. Note: Requires instance admin",
        requestBody = OpenApiRequestBody([OpenApiContent(from = ConnectionRequest::class)]),
        responses = [
            OpenApiResponse("200", [OpenApiContent(from = ConnectionReport::class)], description = "The probe report"),
            OpenApiResponse("400", description = "The URL carries user information, or its host is not on the allow list"),
            OpenApiResponse("403", description = "The caller is not an instance admin"),
        ]
    )
    val connectionTest = ReposiliteRoute<ConnectionReport>("/api/migration/nexus/connection/test", POST) {
        managerOnly {
            response = connections.test(body<ConnectionRequest>())
        }
    }

    private val discover = ReposiliteRoute<DiscoverReport>("/api/migration/nexus/discover", POST) {
        managerOnly {
            response = service.discover(body<ConnectionRequest>())
        }
    }

    private val createJob = ReposiliteRoute<JobCreated>("/api/migration/nexus/jobs", POST) {
        managerOnly {
            response = service.create(body<JobRequest>()).peek { ctx.status(ACCEPTED) }
        }
    }

    private val listJobs = ReposiliteRoute<List<JobView>>("/api/migration/nexus/jobs", GET) {
        managerOnly {
            response = Result.ok(service.jobs())
        }
    }

    private val getJob = ReposiliteRoute<JobView>("/api/migration/nexus/jobs/{id}", GET) {
        managerOnly {
            response = withJob { service.job(it) }
        }
    }

    private val deleteJob = ReposiliteRoute<Unit>("/api/migration/nexus/jobs/{id}", DELETE) {
        managerOnly {
            response = withJob { service.delete(it) }.peek { ctx.status(NO_CONTENT) }
        }
    }

    private val pauseJob = ReposiliteRoute<Unit>("/api/migration/nexus/jobs/{id}/pause", POST) {
        managerOnly {
            response = withJob { service.pause(it) }.peek { ctx.status(NO_CONTENT) }
        }
    }

    private val resumeJob = ReposiliteRoute<Unit>("/api/migration/nexus/jobs/{id}/resume", POST) {
        managerOnly {
            response = withJob { service.resume(it) }.peek { ctx.status(NO_CONTENT) }
        }
    }

    private val cancelJob = ReposiliteRoute<Unit>("/api/migration/nexus/jobs/{id}/cancel", POST) {
        managerOnly {
            response = withJob { service.cancel(it) }.peek { ctx.status(NO_CONTENT) }
        }
    }

    private val supplyCredentials = ReposiliteRoute<Unit>("/api/migration/nexus/jobs/{id}/credentials", PUT) {
        managerOnly {
            val request = body<CredentialsRequest>()
            response = withJob { service.supplyCredentials(it, request.password) }.peek { ctx.status(NO_CONTENT) }
        }
    }

    private val jobReport = ReposiliteRoute<Any>("/api/migration/nexus/jobs/{id}/report", GET) {
        managerOnly {
            val format = ctx.queryParam("format") ?: "json"
            if (format != "json" && format != "csv") {
                response = BAD_REQUEST.toErrorResponse("The format must be json or csv").asError()
                return@managerOnly
            }
            response = withJob { service.report(it) }.map<Any> { view: ReportView ->
                if (format == "csv") {
                    ctx.contentType(ContentType.TEXT_CSV)
                    ctx.header(Header.CONTENT_DISPOSITION, "attachment; filename=\"nexus-import-report.csv\"")
                    LossReport.csv(view.rows).byteInputStream(Charsets.UTF_8)
                } else {
                    view
                }
            }
        }
    }

    private val jobConflicts = ReposiliteRoute<List<ConflictView>>("/api/migration/nexus/jobs/{id}/conflicts", GET) {
        managerOnly {
            response = withJob { service.conflicts(it) }
        }
    }

    override val routes = routes(
        connectionTest, discover, createJob, listJobs, getJob, deleteJob, pauseJob, resumeJob, cancelJob,
        supplyCredentials, jobReport, jobConflicts,
    )

    /** Runs [action] for the job named in the path, or answers 400 when the id is not a number. */
    private fun <T> ContextDsl<*>.withJob(action: (Long) -> Result<T, ErrorResponse>): Result<T, ErrorResponse> {
        val id = parameter("id")?.toLongOrNull() ?: return BAD_REQUEST.toErrorResponse("The job id is not a number").asError()
        return action(id)
    }
}
