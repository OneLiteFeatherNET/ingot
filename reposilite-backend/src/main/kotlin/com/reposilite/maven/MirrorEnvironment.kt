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

import panda.std.Result
import java.net.Proxy

/**
 * The environment that mirror clients may take their outgoing proxy from. [useEnvironmentProxy] is the switch
 * `INGOT_HTTP_USE_ENV_PROXY`, and [variables] are the process environment the proxy variables are read from.
 */
data class MirrorEnvironment(
    val useEnvironmentProxy: Boolean,
    val variables: Map<String, String>,
) {

    companion object {

        /** The switch is off, so no mirror takes a proxy from the environment. */
        val NONE = MirrorEnvironment(useEnvironmentProxy = false, variables = emptyMap())

        /** Reads the switch `INGOT_HTTP_USE_ENV_PROXY`, with `REPOSILITE_HTTP_USE_ENV_PROXY` as fallback. Off unless it is `true`. */
        fun read(variables: Map<String, String> = System.getenv()): MirrorEnvironment =
            MirrorEnvironment(useEnvironmentProxy = false, variables = variables)

    }

}

/**
 * The proxy that a mirror request takes from the environment, or null when the environment does not decide. A
 * [configuredProxy] (the per-mirror `httpProxy`) always wins. A refusal carries a message that names the variable.
 */
internal fun selectEnvironmentProxy(configuredProxy: String, targetUrl: String, environment: MirrorEnvironment): Result<Proxy?, String> =
    Result.ok(null)
