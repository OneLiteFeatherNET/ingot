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

package net.onelitefeather.ingot.nexusimport

import com.reposilite.web.api.ReposiliteRoute
import com.reposilite.web.api.ReposiliteRoutes
import io.javalin.community.routing.Route.POST
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiContent
import io.javalin.openapi.OpenApiRequestBody
import io.javalin.openapi.OpenApiResponse
import net.onelitefeather.ingot.nexusimport.nexus.ConnectionReport

/** The import routes. Every route needs instance admin, and none takes a credential in the query string. */
internal class NexusImportEndpoints(private val connections: NexusConnectionService) : ReposiliteRoutes() {

    @OpenApi(
        tags = ["Migration"],
        path = "/api/migration/nexus/connection/test",
        methods = [HttpMethod.POST],
        summary = "Probes a Nexus Repository 3 with a credential: reachability, version, credential and readable areas. Note: Requires instance admin",
        requestBody = OpenApiRequestBody([OpenApiContent(from = ConnectionRequest::class)]),
        responses = [
            OpenApiResponse("200", [OpenApiContent(from = ConnectionReport::class)], description = "The probe report"),
            OpenApiResponse("400", description = "The URL carries user information, or its host is not on the allow list"),
            OpenApiResponse("403", description = "The caller is not an instance admin"),
        ]
    )
    val connectionTest = ReposiliteRoute<ConnectionReport>("/api/migration/nexus/connection/test", POST) {
        managerOnly {
            response = connections.test(body<ConnectionRequest>())
        }
    }

    override val routes = routes(connectionTest)

}
