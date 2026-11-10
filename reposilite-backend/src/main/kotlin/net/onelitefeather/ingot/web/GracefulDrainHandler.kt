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

package net.onelitefeather.ingot.web

import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.Response
import org.eclipse.jetty.server.handler.GracefulHandler
import org.eclipse.jetty.util.Callback

/**
 * A graceful handler that keeps answering the two status paths while it refuses everything else during a drain.
 *
 * Once a drain has begun, the plain graceful handler answers every new request with `503`. A draining replica still
 * has to answer its liveness and readiness: an orchestrator that saw the liveness fail would restart the replica in the
 * middle of its work, and the readiness answer is where the drain becomes visible. Those two paths bypass the drain
 * counting, so they are not held up by the requests in flight either.
 */
class GracefulDrainHandler : GracefulHandler() {

    override fun handle(request: Request, response: Response, callback: Callback): Boolean {
        val path = request.httpURI.path
        val statusPath = path.endsWith(HEALTH_PATH) || path.endsWith(READINESS_PATH)

        if (isShutdown && statusPath) {
            return handler?.handle(request, response, callback) ?: false
        }

        return super.handle(request, response, callback)
    }

    private companion object {
        const val HEALTH_PATH = "/api/status/health"
        const val READINESS_PATH = "/api/status/ready"
    }

}
