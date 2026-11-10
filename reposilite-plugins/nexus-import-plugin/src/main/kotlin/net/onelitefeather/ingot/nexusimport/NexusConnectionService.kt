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

import com.reposilite.shared.ErrorResponse
import com.reposilite.shared.toErrorResponse
import io.javalin.http.HttpStatus.BAD_REQUEST
import net.onelitefeather.ingot.nexusimport.nexus.ConnectionReport
import net.onelitefeather.ingot.nexusimport.nexus.NexusClient
import net.onelitefeather.ingot.nexusimport.nexus.NexusConnection
import net.onelitefeather.ingot.nexusimport.nexus.NexusConnectionProbe
import net.onelitefeather.ingot.nexusimport.nexus.NexusTransport
import net.onelitefeather.ingot.nexusimport.nexus.NexusUrlPolicy
import net.onelitefeather.ingot.nexusimport.nexus.Secret
import panda.std.Result
import panda.std.asError
import panda.std.asSuccess
import java.time.Clock
import java.time.Duration

/** The body of a request that names a Nexus. [toString] hides the password, so a logged request cannot leak it. */
data class ConnectionRequest(
    val url: String = "",
    val user: String = "",
    val password: String = "",
) {
    override fun toString(): String = "ConnectionRequest(url=$url, user=$user, password=********)"
}

/** Runs the connection test: validates the URL, then probes Nexus with the given credential. */
class NexusConnectionService(
    private val settings: NexusImportSettings,
    private val transport: NexusTransport,
    private val clock: Clock,
    private val sleeper: (Duration) -> Unit,
) {

    fun test(request: ConnectionRequest): Result<ConnectionReport, ErrorResponse> {
        val outcome = NexusUrlPolicy(settings.allowedHosts).check(request.url)
        val uri = outcome.uri
        if (outcome.refusal != null || uri == null) {
            return BAD_REQUEST.toErrorResponse(outcome.refusal ?: "The Nexus URL is not valid").asError()
        }
        if (request.user.isBlank() || request.password.isEmpty()) {
            return BAD_REQUEST.toErrorResponse("A user name and a password are required").asError()
        }

        val connection = NexusConnection(uri, request.user, Secret(request.password))
        val client = NexusClient(connection, transport, clock, sleeper, settings.maxRequestsPerSecond)
        return NexusConnectionProbe(client, connection).run().asSuccess()
    }
}
