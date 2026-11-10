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

package com.reposilite.maven

import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.shared.http.HttpRemoteClient
import kotlin.system.exitProcess

/**
 * Child process of [MirrorProxyEnvironmentIntegrationTest]. It performs one request for a mirror without `httpProxy`:
 * the proxy is the one that [selectEnvironmentProxy] picks from the environment of this process, or none.
 */
object MirrorProxyProbe {

    @JvmStatic
    fun main(args: Array<String>) {
        val proxy = selectEnvironmentProxy(configuredProxy = "", targetUrl = args[0], environment = MirrorEnvironment.read())
            .fold({ it }, { null })
        HttpRemoteClient(InMemoryLogger(), proxy).get(args[0], null, 2, 2)
        exitProcess(0)
    }

}
