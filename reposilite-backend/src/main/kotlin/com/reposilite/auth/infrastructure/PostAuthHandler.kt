/*
 * Copyright (c) 2023 dzikoysk
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
package com.reposilite.auth.infrastructure

import com.reposilite.web.api.ReposiliteRoute
import com.reposilite.web.api.ReposiliteRoutes
import io.javalin.community.routing.Route
import io.javalin.http.HttpStatus.UNAUTHORIZED

private const val WWW_AUTHENTICATE = "www-authenticate"
private const val WWW_BASIC_REALM = """Basic realm="Ingot", charset="UTF-8""""
// Browsers show a login popup for Basic only. The API is used by the dashboard, which signs in itself, so it gets Bearer.
private const val WWW_BEARER_REALM = """Bearer realm="Ingot""""

internal class PostAuthHandler : ReposiliteRoutes() {

    private val realmDescription = ReposiliteRoute<Unit>("/{repository}/<*>", Route.AFTER) {
        if (ctx.status() == UNAUTHORIZED) {
            ctx.header(WWW_AUTHENTICATE, if (uri.startsWith("/api")) WWW_BEARER_REALM else WWW_BASIC_REALM)
        }
    }

    override val routes = routes(realmDescription)

}
