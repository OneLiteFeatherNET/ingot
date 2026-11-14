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

package net.onelitefeather.ingot.backup

import com.reposilite.audit.api.auditActor
import com.reposilite.web.api.ReposiliteRoute
import com.reposilite.web.api.ReposiliteRoutes
import com.reposilite.web.api.SESSION_BEARER_SCHEME
import com.reposilite.web.api.SESSION_COOKIE_SCHEME
import com.reposilite.web.api.TOKEN_BASIC_SCHEME
import io.javalin.community.routing.Route.POST
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiContent
import io.javalin.openapi.OpenApiResponse
import io.javalin.openapi.OpenApiSecurity
import java.time.format.DateTimeFormatter

/** The database API. Only principals with the maintenance area can take a snapshot. */
internal class DatabaseBackupEndpoints(private val snapshotService: DatabaseSnapshotService) : ReposiliteRoutes() {

    @OpenApi(
        security = [OpenApiSecurity(name = TOKEN_BASIC_SCHEME), OpenApiSecurity(name = SESSION_BEARER_SCHEME), OpenApiSecurity(name = SESSION_COOKIE_SCHEME)],
        tags = ["Database"],
        path = "/api/database/snapshots",
        methods = [HttpMethod.POST],
        summary = "Take a database snapshot",
        description = "Writes a consistent copy of the SQLite database into the backups directory of the working directory while the instance keeps serving requests. The answer names the file only. Other databases are refused: use their dump tool.",
        responses = [
            OpenApiResponse(status = "200", content = [OpenApiContent(from = DatabaseSnapshotResponse::class)]),
            OpenApiResponse(status = "403", description = "Only principals with the maintenance area (ingot.admin.maintenance) can access this endpoint"),
            OpenApiResponse(status = "409", description = "The database is not SQLite, or the file name is already taken"),
            OpenApiResponse(status = "500", description = "The snapshot could not be written. The partial file is removed."),
        ]
    )
    private val snapshot = ReposiliteRoute<DatabaseSnapshotResponse>("/api/database/snapshots", POST) {
        managerOnly("maintenance") {
            response = snapshotService.snapshot(auditActor()).map { it.toResponse() }
        }
    }

    override val routes = routes(snapshot)

}

data class DatabaseSnapshotResponse(
    val fileName: String,
    val sizeBytes: Long,
    val createdAt: String,
)

private val ISO_INSTANT: DateTimeFormatter = DateTimeFormatter.ISO_INSTANT

private fun SnapshotInfo.toResponse() =
    DatabaseSnapshotResponse(
        fileName = fileName,
        sizeBytes = sizeBytes,
        createdAt = ISO_INSTANT.format(createdAt),
    )
