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

package com.reposilite.audit.infrastructure

import com.reposilite.ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER
import com.reposilite.audit.AuditFacade
import com.reposilite.audit.AuditRecord
import com.reposilite.audit.api.AuditEventDto
import com.reposilite.audit.api.AuditPageDto
import com.reposilite.shared.badRequest
import com.reposilite.web.api.ReposiliteRoute
import com.reposilite.web.api.ReposiliteRoutes
import io.javalin.community.routing.Route.GET
import io.javalin.http.ContentType
import io.javalin.http.Header
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiResponse
import java.io.InputStream
import java.time.Instant

internal class AuditEndpoints(private val auditFacade: AuditFacade) : ReposiliteRoutes() {

    @OpenApi(
        tags = ["Audit"],
        path = "/api/audit",
        methods = [HttpMethod.GET],
        summary = "Returns audited events, newest first. Filters: actor, action, repository, q, from, to. Pages with cursor and limit. Note: Requires manager",
        responses = [
            OpenApiResponse("200", description = "A page of events and the cursor of the next page, or null on the last page"),
            OpenApiResponse("400", description = "A filter, limit or cursor is not valid"),
            OpenApiResponse("401", description = "Token without manager permission has been used")
        ]
    )
    val findAuditEvents = ReposiliteRoute<AuditPageDto>("/api/audit", GET) {
        managerOnly {
            response = AuditQueryParser.parse { ctx.queryParam(it) }
                .mapErr { badRequest(it) }
                .map { query -> auditFacade.find(query).let { page -> AuditPageDto(page.records.map { it.toDto() }, page.nextCursor) } }
        }
    }

    @OpenApi(
        tags = ["Audit"],
        path = "/api/audit/export",
        methods = [HttpMethod.GET],
        summary = "Exports the audited events that match the filters of /api/audit as CSV, newest first. Note: Requires manager",
        responses = [
            OpenApiResponse("200", description = "The matching events as text/csv"),
            OpenApiResponse("400", description = "A filter is not valid"),
            OpenApiResponse("401", description = "Token without manager permission has been used")
        ]
    )
    val exportAuditEvents = ReposiliteRoute<InputStream>("/api/audit/export", GET) {
        managerOnly {
            response = AuditQueryParser.parse { ctx.queryParam(it) }
                .mapErr { badRequest(it) }
                .map { query ->
                    val csv = StringBuilder()
                    auditFacade.writeCsv(query, csv)
                    ctx.contentType(ContentType.TEXT_CSV)
                    ctx.header(Header.CONTENT_DISPOSITION, "attachment; filename=\"ingot-audit.csv\"")
                    csv.toString().byteInputStream(Charsets.UTF_8)
                }
        }
    }

    override val routes = routes(findAuditEvents, exportAuditEvents)

}

private fun AuditRecord.toDto(): AuditEventDto =
    AuditEventDto(
        id = id,
        timestamp = Instant.ofEpochMilli(timestamp).toString(),
        actor = actor,
        actorAddress = actorAddress,
        action = action,
        repository = repository,
        path = path,
        details = details?.let { DEFAULT_OBJECT_MAPPER.readTree(it) },
        success = success,
        occurrences = occurrences,
    )
