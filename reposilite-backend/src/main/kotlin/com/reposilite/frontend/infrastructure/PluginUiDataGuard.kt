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
import panda.std.asError

/**
 * Requires the area of a plugin's page for every request below `/api/plugins/<plugin>/`, once the caller is known.
 * A plugin that registered no page is not touched, and neither is a route whose plugin name merely starts with the
 * name of a guarded plugin, because the plugin is the whole segment after `/api/plugins/`.
 *
 * A before route, not a check in each handler, so a plugin cannot forget it. The refusal skips the handler that
 * would have run. The answer is 401 for an anonymous caller and 403 for a caller without any of the areas.
 */
internal class PluginUiDataGuard(private val facade: PluginUiFacade) : ReposiliteRoutes() {

    private val guardPluginRoutes = ReposiliteRoute<Unit>("/api/plugins/*", BEFORE) {
        val plugin = ctx.path().removePrefix(PREFIX).substringBefore('/')
        val areas = facade.areasGuarding(plugin)
        if (areas.isNotEmpty()) {
            authentication()
                .onError {
                    response = it.asError()
                    ctx.skipRemainingHandlers()
                }
                .peek {
                    if (areas.none { area -> requireArea(area.takeUnless { it == PluginUiModel.WILDCARD_AREA }).isOk }) {
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
