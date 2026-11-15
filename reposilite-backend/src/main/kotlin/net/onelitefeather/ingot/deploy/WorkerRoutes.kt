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

package net.onelitefeather.ingot.deploy

import com.reposilite.web.api.ReposiliteRoute
import io.javalin.community.routing.Route.AFTER
import io.javalin.community.routing.Route.BEFORE

/**
 * Decides which registered routes an instance keeps for its deploy target. Targets `all` and `server` keep every route.
 * The worker keeps the routes marked `servesWorker` and the before and after handlers, which serve no request themselves.
 */
object WorkerRoutes {

    fun restrict(routes: Collection<ReposiliteRoute<*>>, target: DeployTarget): Collection<ReposiliteRoute<*>> =
        if (target != DeployTarget.WORKER) {
            routes
        } else {
            routes.filter { route -> route.servesWorker || route.methods.all { it == BEFORE || it == AFTER } }
        }
}
