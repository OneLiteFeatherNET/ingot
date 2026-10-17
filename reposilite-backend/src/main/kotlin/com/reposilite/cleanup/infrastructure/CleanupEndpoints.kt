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

package com.reposilite.cleanup.infrastructure

import com.reposilite.cleanup.CleanupActor
import com.reposilite.cleanup.CleanupPreview
import com.reposilite.cleanup.CleanupRun
import com.reposilite.cleanup.CleanupRunStatus
import com.reposilite.cleanup.CleanupService
import com.reposilite.cleanup.CleanupTrigger
import com.reposilite.cleanup.PlannedDeletion
import com.reposilite.web.api.ReposiliteRoute
import com.reposilite.web.api.ReposiliteRoutes
import io.javalin.community.routing.Route.GET
import io.javalin.community.routing.Route.POST
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiContent
import io.javalin.openapi.OpenApiParam
import io.javalin.openapi.OpenApiResponse
import panda.std.asSuccess
import java.time.format.DateTimeFormatter

/**
 * The cleanup API. Every route is manager only.
 */
internal class CleanupEndpoints(private val cleanupService: CleanupService) : ReposiliteRoutes() {

    @OpenApi(
        tags = ["Cleanup"],
        path = "/api/cleanup/policies/{id}/dry-run",
        methods = [HttpMethod.POST],
        pathParams = [OpenApiParam(name = "id", description = "Id of the cleanup policy", required = true)],
        summary = "Preview a cleanup policy",
        description = "Lists the versions the policy would delete, with totals and warnings. Nothing is deleted.",
        responses = [
            OpenApiResponse(status = "200", content = [OpenApiContent(from = CleanupPreviewResponse::class)]),
            OpenApiResponse(status = "403", description = "Only principals with the maintenance area (ingot.admin.maintenance) can access this endpoint"),
            OpenApiResponse(status = "404", description = "The policy does not exist"),
        ]
    )
    private val dryRun = ReposiliteRoute<CleanupPreviewResponse>("/api/cleanup/policies/{id}/dry-run", POST) {
        managerOnly("maintenance") {
            response = cleanupService.preview(requireParameter("id")).map { it.toResponse() }
        }
    }

    @OpenApi(
        tags = ["Cleanup"],
        path = "/api/cleanup/policies/{id}/run",
        methods = [HttpMethod.POST],
        pathParams = [OpenApiParam(name = "id", description = "Id of the cleanup policy", required = true)],
        summary = "Run a cleanup policy",
        description = "Deletes the versions the policy plans right now, through the repository delete service, and records the run.",
        responses = [
            OpenApiResponse(status = "200", content = [OpenApiContent(from = CleanupRunResponse::class)]),
            OpenApiResponse(status = "403", description = "Only principals with the maintenance area (ingot.admin.maintenance) can access this endpoint"),
            OpenApiResponse(status = "404", description = "The policy does not exist"),
            OpenApiResponse(status = "409", description = "The policy is already running"),
        ]
    )
    private val run = ReposiliteRoute<CleanupRunResponse>("/api/cleanup/policies/{id}/run", POST) {
        managerOnly("maintenance") {
            val actor = CleanupActor(by = getSessionIdentifier(), accessToken = identifier)
            response = cleanupService.run(requireParameter("id"), actor, CleanupTrigger.MANUAL).map { it.toResponse() }
        }
    }

    @OpenApi(
        tags = ["Cleanup"],
        path = "/api/cleanup/runs",
        methods = [HttpMethod.GET],
        summary = "List cleanup runs",
        description = "The recorded runs, newest first. Each policy keeps its last 20 runs.",
        responses = [
            OpenApiResponse(status = "200", description = "Returns the runs as an array of CleanupRunResponse"),
            OpenApiResponse(status = "403", description = "Only principals with the maintenance area (ingot.admin.maintenance) can access this endpoint"),
        ]
    )
    private val runs = ReposiliteRoute<List<CleanupRunResponse>>("/api/cleanup/runs", GET) {
        managerOnly("maintenance") {
            response = cleanupService.runs(queryParameter("policy")?.takeIf { it.isNotBlank() })
                .map { it.toResponse() }
                .asSuccess()
        }
    }

    override val routes = routes(dryRun, run, runs)

}

data class CleanupPreviewResponse(
    val policyId: String,
    val evaluatedAt: String,
    val count: Int,
    val totalBytes: Long,
    val deletions: List<PlannedDeletion>,
    val warnings: List<String>,
)

data class CleanupRunResponse(
    val id: String,
    val policyId: String,
    val trigger: CleanupTrigger,
    val startedAt: String,
    val finishedAt: String,
    val status: CleanupRunStatus,
    val deletedCount: Int,
    val deletedBytes: Long,
    val failures: List<String>,
)

private val ISO_INSTANT: DateTimeFormatter = DateTimeFormatter.ISO_INSTANT

private fun CleanupPreview.toResponse() =
    CleanupPreviewResponse(
        policyId = policyId,
        evaluatedAt = ISO_INSTANT.format(evaluatedAt),
        count = count,
        totalBytes = totalBytes,
        deletions = deletions,
        warnings = warnings,
    )

private fun CleanupRun.toResponse() =
    CleanupRunResponse(
        id = id,
        policyId = policyId,
        trigger = trigger,
        startedAt = ISO_INSTANT.format(startedAt),
        finishedAt = ISO_INSTANT.format(finishedAt),
        status = status,
        deletedCount = deletedCount,
        deletedBytes = deletedBytes,
        failures = failures,
    )
