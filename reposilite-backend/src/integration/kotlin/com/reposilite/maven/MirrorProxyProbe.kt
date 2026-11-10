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
 * Child process of [MirrorProxyEnvironmentCharacterisationIntegrationTest]. It performs one request through the mirror
 * client exactly as a mirror without `httpProxy` does (proxy `null`), so the JVM and its environment decide the route.
 */
object MirrorProxyProbe {

    @JvmStatic
    fun main(args: Array<String>) {
        HttpRemoteClient(InMemoryLogger(), null).get(args[0], null, 2, 2)
        exitProcess(0)
    }

}
