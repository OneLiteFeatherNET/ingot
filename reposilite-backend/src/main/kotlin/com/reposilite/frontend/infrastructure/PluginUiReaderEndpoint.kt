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
import com.reposilite.frontend.api.PluginUiModel
import com.reposilite.frontend.api.PluginUiReaderManifest
import com.reposilite.web.api.ReposiliteRoute
import com.reposilite.web.api.ReposiliteRoutes
import io.javalin.community.routing.Route.GET
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiContent
import io.javalin.openapi.OpenApiResponse
import panda.std.asSuccess

/**
 * The reader pages of the dashboard, for every visitor. Anonymous and signed in callers get the same answer, and the
 * answer is private to the viewer, so a shared cache does not keep it.
 */
internal class PluginUiReaderEndpoint(private val facade: PluginUiFacade) : ReposiliteRoutes() {

    @OpenApi(
        tags = ["Frontend"],
        path = "/api/frontend/plugin-ui/readers",
        methods = [HttpMethod.GET],
        summary = "Fetch the reader pages contributed by plugins",
        description = "Returns the reader pages of every enabled plugin, for anonymous and signed in callers alike. The list is empty while INGOT_LOCAL_PLUGINREADERS is false.",
        responses = [
            OpenApiResponse(
                status = "200",
                description = "The reader manifest of version ${PluginUiModel.VERSION}",
                content = [OpenApiContent(from = PluginUiReaderManifest::class)]
            )
        ]
    )
    private val findReaderPages = ReposiliteRoute<PluginUiReaderManifest>("/api/frontend/plugin-ui/readers", GET) {
        ctx.header("Cache-Control", "private, no-cache")
        response = facade.readerManifest().asSuccess()
    }

    override val routes = routes(findReaderPages)

}
