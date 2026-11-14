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

package net.onelitefeather.ingot.readonly

import com.reposilite.audit.api.AuditActor
import com.reposilite.audit.api.auditActor
import com.reposilite.shared.ContextDsl
import com.reposilite.shared.ErrorResponse
import com.reposilite.shared.toErrorResponse
import com.reposilite.web.api.ReposiliteRoute
import com.reposilite.web.api.ReposiliteRoutes
import com.reposilite.web.api.SESSION_BEARER_SCHEME
import com.reposilite.web.api.SESSION_COOKIE_SCHEME
import com.reposilite.web.api.TOKEN_BASIC_SCHEME
import io.javalin.community.routing.Route.DELETE
import io.javalin.community.routing.Route.GET
import io.javalin.community.routing.Route.POST
import io.javalin.http.HttpStatus.CONFLICT
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiResponse
import io.javalin.openapi.OpenApiSecurity
import panda.std.Result
import panda.std.asError
import panda.std.asSuccess

/** The read-only state as the API reports it. [source] is `environment` or `stored`. */
data class ReadOnlyStateDto(val enabled: Boolean, val source: String)

/**
 * The toggle of the read-only state. Reading and changing it need the maintenance area, which the cleanup endpoints check too.
 */
internal class ReadOnlyEndpoints(private val facade: ReadOnlyFacade) : ReposiliteRoutes() {

    @OpenApi(
        security = [OpenApiSecurity(name = TOKEN_BASIC_SCHEME), OpenApiSecurity(name = SESSION_BEARER_SCHEME), OpenApiSecurity(name = SESSION_COOKIE_SCHEME)],
        tags = ["Maintenance"],
        path = "/api/admin/read-only",
        methods = [HttpMethod.GET],
        summary = "Reports whether the instance is read-only and where that state comes from. Note: Requires the maintenance area",
        responses = [
            OpenApiResponse("200", description = "The state and its source, environment or stored"),
            OpenApiResponse("403", description = "Token without the maintenance area (ingot.admin.maintenance) has been used")
        ]
    )
    val readState = ReposiliteRoute<ReadOnlyStateDto>("/api/admin/read-only", GET) {
        managerOnly("maintenance") {
            response = facade.state().toDto().asSuccess()
        }
    }

    @OpenApi(
        security = [OpenApiSecurity(name = TOKEN_BASIC_SCHEME), OpenApiSecurity(name = SESSION_BEARER_SCHEME), OpenApiSecurity(name = SESSION_COOKIE_SCHEME)],
        tags = ["Maintenance"],
        path = "/api/admin/read-only",
        methods = [HttpMethod.POST],
        summary = "Turns read-only on: writes are refused with 503 on every replica until it is turned off. Note: Requires the maintenance area",
        responses = [
            OpenApiResponse("200", description = "The state after the change"),
            OpenApiResponse("403", description = "Token without the maintenance area (ingot.admin.maintenance) has been used"),
            OpenApiResponse("409", description = "The environment forces the state, or another change happened at the same time")
        ]
    )
    val enableReadOnly = ReposiliteRoute<ReadOnlyStateDto>("/api/admin/read-only", POST) {
        managerOnly("maintenance") {
            response = toggle(enabled = true, actor = auditActor())
        }
    }

    @OpenApi(
        security = [OpenApiSecurity(name = TOKEN_BASIC_SCHEME), OpenApiSecurity(name = SESSION_BEARER_SCHEME), OpenApiSecurity(name = SESSION_COOKIE_SCHEME)],
        tags = ["Maintenance"],
        path = "/api/admin/read-only",
        methods = [HttpMethod.DELETE],
        summary = "Turns read-only off again. Note: Requires the maintenance area",
        responses = [
            OpenApiResponse("200", description = "The state after the change"),
            OpenApiResponse("403", description = "Token without the maintenance area (ingot.admin.maintenance) has been used"),
            OpenApiResponse("409", description = "The environment forces the state, or another change happened at the same time")
        ]
    )
    val disableReadOnly = ReposiliteRoute<ReadOnlyStateDto>("/api/admin/read-only", DELETE) {
        managerOnly("maintenance") {
            response = toggle(enabled = false, actor = auditActor())
        }
    }

    private fun ContextDsl<*>.toggle(enabled: Boolean, actor: AuditActor): Result<ReadOnlyStateDto, ErrorResponse> =
        when (facade.set(enabled, actor)) {
            is ToggleOutcome.Stored, is ToggleOutcome.Unchanged -> facade.state().toDto().asSuccess()
            ToggleOutcome.ForcedByEnvironment -> CONFLICT
                .toErrorResponse("INGOT_READ_ONLY forces the read-only state on this instance, so it cannot be changed here. Unset the variable and restart the instance.")
                .asError()
            ToggleOutcome.Conflict -> CONFLICT
                .toErrorResponse("The read-only state was changed by another request while this one was handled. Read it again and retry.")
                .asError()
        }

    override val routes = routes(readState, enableReadOnly, disableReadOnly)

    private fun ReadOnlyState.toDto() = ReadOnlyStateDto(enabled = enabled, source = source.name.lowercase())

}
