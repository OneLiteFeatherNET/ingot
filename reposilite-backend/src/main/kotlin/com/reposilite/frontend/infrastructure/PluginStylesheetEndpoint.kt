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

import com.reposilite.frontend.api.PluginStylesheet
import com.reposilite.frontend.api.PluginUiFacade
import com.reposilite.shared.notFoundError
import com.reposilite.web.api.ReposiliteRoute
import com.reposilite.web.api.ReposiliteRoutes
import io.javalin.community.routing.Route.GET
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiContent
import io.javalin.openapi.OpenApiParam
import io.javalin.openapi.OpenApiResponse

/**
 * Serves the stylesheet of a plugin at the path that the plugin-ui manifest names. The path needs no sign in: the sheet is a
 * static file of the plugin's jar, and the manifest that names the path is for signed in callers only.
 */
internal class PluginStylesheetEndpoint(
    private val facade: PluginUiFacade,
) : ReposiliteRoutes() {

    @OpenApi(
        tags = ["Frontend"],
        path = "/api/frontend/plugin-styles/{plugin}/{file}",
        methods = [HttpMethod.GET],
        summary = "Fetch the dashboard stylesheet of a plugin",
        description = "Returns the stylesheet of the plugin, wrapped in the ingot-plugins layer. The file name is the first 16 hexadecimal characters of the SHA-256 of the served bytes, so the answer is cached for a year. Needs no sign in.",
        pathParams = [
            OpenApiParam(name = "plugin", description = "The name of the plugin"),
            OpenApiParam(name = "file", description = "The first 16 hexadecimal characters of the SHA-256 of the sheet, followed by .css")
        ],
        responses = [
            OpenApiResponse(
                status = "200",
                description = "The stylesheet",
                content = [OpenApiContent(from = String::class, mimeType = "text/css")]
            ),
            OpenApiResponse(status = "404", description = "Unknown plugin, unknown hash, or the plugin styles are switched off")
        ]
    )
    private val findStylesheet = ReposiliteRoute<Any>("/api/frontend/plugin-styles/{plugin}/{file}", GET) {
        val hash = PluginStylesheet.FILE_NAME.matchEntire(ctx.pathParam("file"))?.groupValues?.get(1)
        val stylesheet = hash?.let { facade.stylesheet(ctx.pathParam("plugin"), it) }
        if (stylesheet == null) {
            response = notFoundError<Any>("Stylesheet not found")
            return@ReposiliteRoute
        }
        // The bytes are written here, not as a response, because a response of bytes would be sent as JSON.
        ctx.contentType("text/css; charset=utf-8")
        // The /api routes carry the cache bypass headers of the server. This sheet is content-addressed, so they are withdrawn.
        ctx.res().setHeader("Pragma", null)
        ctx.res().setHeader("Expires", null)
        ctx.header("Cache-Control", "public, max-age=31536000, immutable")
        ctx.header("ETag", "\"${stylesheet.hash}\"")
        ctx.result(stylesheet.body)
    }

    override val routes = routes(findStylesheet)

}
