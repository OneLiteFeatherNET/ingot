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

package com.reposilite.frontend.infrastructure

import com.reposilite.frontend.FrontendFacade
import com.reposilite.frontend.api.FrontendSettingsResponse
import com.reposilite.web.api.ReposiliteRoute
import com.reposilite.web.api.ReposiliteRoutes
import io.javalin.community.routing.Route.GET
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiContent
import io.javalin.openapi.OpenApiResponse
import panda.std.asSuccess

/**
 * Serves the dashboard's own presentation settings so it can run somewhere other than in
 * this process, for instance from its own container behind a proxy.
 *
 * Deliberately anonymous. Every value here is already handed to any unauthenticated visitor
 * of the bundled dashboard, substituted into the index page and the script bundle before
 * they are served, so publishing them under a URL discloses nothing that was not public
 * already. The authenticated /api/settings endpoints stay authenticated: those expose the
 * whole configuration, including the LDAP search password.
 */
internal class FrontendSettingsEndpoint(private val frontendFacade: FrontendFacade) : ReposiliteRoutes() {

    @OpenApi(
        tags = ["Frontend"],
        path = "/api/frontend/settings",
        methods = [HttpMethod.GET],
        summary = "Fetch the dashboard presentation settings",
        description = "Returns the values a dashboard needs to render itself. Public, because a bundled dashboard already exposes all of them to anonymous visitors.",
        responses = [
            OpenApiResponse(
                status = "200",
                description = "Presentation settings, keyed by placeholder token",
                content = [OpenApiContent(from = FrontendSettingsResponse::class)]
            )
        ]
    )
    private val findFrontendSettings = ReposiliteRoute<FrontendSettingsResponse>("/api/frontend/settings", GET) {
        response = FrontendSettingsResponse(
            placeholders = frontendFacade.createPlaceholders(escapeForJs = false)
        ).asSuccess()
    }

    override val routes = routes(findFrontendSettings)

}
