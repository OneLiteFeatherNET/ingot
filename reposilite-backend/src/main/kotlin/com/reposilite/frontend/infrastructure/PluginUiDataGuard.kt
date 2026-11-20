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
import com.reposilite.shared.toErrorResponse
import com.reposilite.web.api.ReposiliteRoute
import com.reposilite.web.api.ReposiliteRoutes
import io.javalin.community.routing.Route.BEFORE
import io.javalin.http.HttpStatus.FORBIDDEN
import io.javalin.http.HttpStatus.NOT_FOUND
import panda.std.asError

/**
 * Requires the area of a plugin's page for every request below `/api/plugins/<plugin>/`, once the caller is known.
 * A plugin that registered no page is not touched, and neither is a route whose plugin name merely starts with the
 * name of a guarded plugin, because the plugin is the whole segment after `/api/plugins/`.
 *
 * A declared reader source is the one exception: a GET or HEAD of its exact address needs no sign in, see
 * [PluginUiReaderAccess]. While the readers switch is off that address answers 404 without running the handler. Any
 * other request to a declared source needs a signed in caller, even for a plugin without an admin page.
 *
 * A before route, not a check in each handler, so a plugin cannot forget it. The refusal skips the handler that
 * would have run. The answer is 401 for an anonymous caller and 403 for a caller without any of the areas.
 */
internal class PluginUiDataGuard(private val facade: PluginUiFacade) : ReposiliteRoutes() {

    private val guardPluginRoutes = ReposiliteRoute<Unit>("/api/plugins/*", BEFORE) {
        val path = ctx.path()
        val plugin = path.removePrefix(PREFIX).substringBefore('/')
        val sources = facade.readerSources(plugin)
        val areas = facade.areasGuarding(plugin)
        if (PluginUiReaderAccess.isExemptRead(ctx.method(), path, sources)) {
            if (!facade.readersSwitchOn()) {
                response = NOT_FOUND.toErrorResponse("The reader pages of plugin $plugin are switched off").asError()
                ctx.skipRemainingHandlers()
            } else {
                ctx.header("Vary", "Cookie, Authorization")
                ctx.header("Cache-Control", "private, no-cache")
            }
        } else if (areas.isNotEmpty() || path in sources) {
            authentication()
                .onError {
                    response = it.asError()
                    ctx.skipRemainingHandlers()
                }
                .peek {
                    if (areas.isNotEmpty() && areas.none { area -> requireArea(area.takeUnless { it == PluginUiModel.WILDCARD_AREA }).isOk }) {
                        response = FORBIDDEN.toErrorResponse("Only principals with one of the areas ${areas.sorted().joinToString()} can access the routes of plugin $plugin").asError()
                        ctx.skipRemainingHandlers()
                    }
                }
        }
    }

    override val routes = routes(guardPluginRoutes)

    private companion object {
        const val PREFIX = "/api/plugins/"
    }

}
