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

package com.reposilite.maven.infrastructure

import com.reposilite.maven.MavenFacade
import com.reposilite.maven.RepositoryStorageUsage
import com.reposilite.maven.StorageUsage
import com.reposilite.web.api.ReposiliteRoute
import com.reposilite.web.api.ReposiliteRoutes
import io.javalin.community.routing.Route.GET
import io.javalin.community.routing.Route.POST
import io.javalin.http.HttpStatus.ACCEPTED
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiContent
import io.javalin.openapi.OpenApiResponse
import panda.std.asSuccess
import java.time.format.DateTimeFormatter

/**
 * Storage usage of the repositories. Both routes are manager only.
 */
internal class MavenStorageEndpoints(private val mavenFacade: MavenFacade) : ReposiliteRoutes() {

    @OpenApi(
        tags = ["Maven"],
        path = "/api/maven/storage",
        methods = [HttpMethod.GET],
        summary = "Get the storage usage of the repositories",
        description = "Returns the last computed usage. The first request starts a computation in the background, and computing is true until it finishes. " +
            "The quota is reported as configured, and quotaBytes is its size in bytes when that is known.",
        responses = [
            OpenApiResponse(status = "200", content = [OpenApiContent(from = StorageUsageResponse::class)]),
            OpenApiResponse(status = "403", description = "Only principals with the maintenance area (ingot.admin.maintenance) can access this endpoint"),
        ]
    )
    private val getStorageUsage = ReposiliteRoute<StorageUsageResponse>("/api/maven/storage", GET) {
        managerOnly("maintenance") {
            if (mavenFacade.storageUsage().computedAt == null) {
                mavenFacade.refreshStorageUsage()
            }
            response = mavenFacade.storageUsage().toResponse().asSuccess()
        }
    }

    @OpenApi(
        tags = ["Maven"],
        path = "/api/maven/storage/refresh",
        methods = [HttpMethod.POST],
        summary = "Recompute the storage usage",
        description = "Starts a new computation in the background and answers at once with the previous figures, flagged as computing. " +
            "A computation that is already running is not started twice.",
        responses = [
            OpenApiResponse(status = "202", content = [OpenApiContent(from = StorageUsageResponse::class)]),
            OpenApiResponse(status = "403", description = "Only principals with the maintenance area (ingot.admin.maintenance) can access this endpoint"),
        ]
    )
    private val refreshStorageUsage = ReposiliteRoute<StorageUsageResponse>("/api/maven/storage/refresh", POST) {
        managerOnly("maintenance") {
            mavenFacade.refreshStorageUsage()
            ctx.status(ACCEPTED)
            response = mavenFacade.storageUsage().toResponse().asSuccess()
        }
    }

    override val routes = routes(getStorageUsage, refreshStorageUsage)

}

data class StorageUsageResponse(
    val computedAt: String?,
    val computing: Boolean,
    val totalBytes: Long,
    val repositories: List<RepositoryStorageUsage>,
)

private fun StorageUsage.toResponse(): StorageUsageResponse =
    StorageUsageResponse(
        computedAt = computedAt?.let { DateTimeFormatter.ISO_INSTANT.format(it) },
        computing = computing,
        totalBytes = totalBytes,
        repositories = repositories,
    )
