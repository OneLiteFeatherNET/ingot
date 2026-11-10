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

import com.reposilite.frontend.api.PluginUiFacade
import com.reposilite.frontend.api.PluginUiManifest
import com.reposilite.frontend.api.PluginUiModel
import com.reposilite.web.api.ReposiliteRoute
import com.reposilite.web.api.ReposiliteRoutes
import io.javalin.community.routing.Route.GET
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiContent
import io.javalin.openapi.OpenApiResponse
import panda.std.asSuccess

/**
 * The manifest of the dashboard pages a plugin contributed, filtered for the signed in caller. Signed in callers only:
 * the pages say what the plugins do, so an anonymous visitor learns nothing from them.
 */
internal class PluginUiEndpoint(
    private val facade: PluginUiFacade,
    private val enabledPlugins: () -> List<String>,
) : ReposiliteRoutes() {

    @OpenApi(
        tags = ["Frontend"],
        path = "/api/frontend/plugin-ui",
        methods = [HttpMethod.GET],
        summary = "Fetch the dashboard pages and settings sections contributed by plugins",
        description = "Returns the pages and settings sections the caller may open, and the names of the enabled plugins. Requires a signed in caller.",
        responses = [
            OpenApiResponse(
                status = "200",
                description = "The manifest of version ${PluginUiModel.VERSION}",
                content = [OpenApiContent(from = PluginUiManifest::class)]
            ),
            OpenApiResponse(status = "401", description = "The caller is not signed in")
        ]
    )
    private val findPluginUi = ReposiliteRoute<PluginUiManifest>("/api/frontend/plugin-ui", GET) {
        authenticated {
            response = facade.manifest(
                canOpen = { area -> requireArea(area.takeUnless { it == PluginUiModel.WILDCARD_AREA }).isOk },
                enabledPlugins = enabledPlugins(),
            ).asSuccess()
        }
    }

    override val routes = routes(findPluginUi)

}
