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

package net.onelitefeather.ingot.logging

import com.reposilite.Reposilite
import com.reposilite.createWithParameters
import com.reposilite.web.api.ReposiliteRoute
import com.reposilite.web.api.RoutingSetupEvent
import io.javalin.community.routing.Route.GET
import java.net.HttpURLConnection
import java.net.URI
import kotlin.system.exitProcess

/**
 * Child process of [OtelAgentLogIntegrationTest], started with the OpenTelemetry Java agent. It starts the server on the port
 * it is given, registers a route that logs one line, requests that route over HTTP, and stops. The line is therefore written
 * inside a request that the agent traces.
 */
object OtelAgentProbe {

    const val PATH = "/api/probe/log"
    const val MESSAGE = "probe inside request"

    @JvmStatic
    fun main(args: Array<String>) {
        val workingDirectory = args[0]
        val port = args[1]
        val instance: Reposilite = requireNotNull(createWithParameters("--working-directory", workingDirectory, "--port", port, "--no-color"))
        instance.extensions.registerEvent { event: RoutingSetupEvent ->
            event.register(ReposiliteRoute<String>(PATH, GET) {
                logger.info(MESSAGE)
                ctx.result("logged")
            })
        }
        instance.launch()

        val connection = URI("http://127.0.0.1:$port$PATH").toURL().openConnection() as HttpURLConnection
        val status = connection.responseCode
        connection.disconnect()
        instance.shutdown()
        exitProcess(if (status == 200) 0 else 2)
    }

}
