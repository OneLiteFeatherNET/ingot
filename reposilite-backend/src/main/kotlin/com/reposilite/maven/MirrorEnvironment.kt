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

import com.reposilite.shared.ErrorResponse
import com.reposilite.shared.http.RemoteClient
import com.reposilite.shared.http.RemoteCredentials
import com.reposilite.storage.api.FileDetails
import io.javalin.http.HttpStatus.BAD_GATEWAY
import panda.std.Result
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI
import java.net.URISyntaxException

/**
 * The environment that mirror clients may take their outgoing proxy from. [useEnvironmentProxy] is the switch
 * `INGOT_HTTP_USE_ENV_PROXY`, and [variables] are the process environment the proxy variables are read from.
 */
data class MirrorEnvironment(
    val useEnvironmentProxy: Boolean,
    val variables: Map<String, String>,
) {

    companion object {

        private const val SWITCH = "INGOT_HTTP_USE_ENV_PROXY"
        private const val LEGACY_SWITCH = "REPOSILITE_HTTP_USE_ENV_PROXY"

        /** The switch is off, so no mirror takes a proxy from the environment. */
        val NONE = MirrorEnvironment(useEnvironmentProxy = false, variables = emptyMap())

        /** Reads the switch `INGOT_HTTP_USE_ENV_PROXY`, with `REPOSILITE_HTTP_USE_ENV_PROXY` as fallback. Off unless it is `true`. */
        fun read(variables: Map<String, String> = System.getenv()): MirrorEnvironment {
            val switch = variables.valueOf(SWITCH) ?: variables.valueOf(LEGACY_SWITCH)
            return MirrorEnvironment(useEnvironmentProxy = switch?.equals("true", ignoreCase = true) == true, variables = variables)
        }

    }

}

/**
 * The proxy that a mirror request takes from the environment, or null when the environment does not decide. A
 * [configuredProxy] (the per-mirror `httpProxy`) always wins. Only `https` takes `HTTPS_PROXY` and only `http` takes
 * `HTTP_PROXY`. A host listed in `NO_PROXY` goes directly. A refusal carries a message that names the variable.
 */
internal fun selectEnvironmentProxy(configuredProxy: String, targetUrl: String, environment: MirrorEnvironment): Result<Proxy?, String> {
    if (!environment.useEnvironmentProxy || configuredProxy.isNotEmpty()) {
        return Result.ok(null)
    }

    val target = try {
        URI(targetUrl)
    } catch (exception: URISyntaxException) {
        // A mirror with an invalid address fails in its own request, which reports the address.
        return Result.ok(null)
    }

    val variable = when (target.scheme?.lowercase()) {
        "https" -> "HTTPS_PROXY"
        "http" -> "HTTP_PROXY"
        else -> return Result.ok(null)
    }

    val value = environment.variables.valueOf(variable) ?: return Result.ok(null)
    val targetHost = target.host ?: return Result.ok(null)
    if (environment.variables.valueOf("NO_PROXY").isBypassedBy(targetHost)) {
        return Result.ok(null)
    }

    val proxy = parseProxyValue(value)
        ?: return Result.error("$variable is not the address of a proxy, so it is not used")
    if (proxy.userInfo != null) {
        return Result.error("$variable carries credentials, which Ingot does not take from the environment; set httpProxy on the mirror instead")
    }

    val address = InetSocketAddress.createUnresolved(proxy.host, if (proxy.port == -1) DEFAULT_PROXY_PORT else proxy.port)
    return Result.ok(Proxy(Proxy.Type.HTTP, address))
}

/** The value of [name], or of its lower-case spelling, with blank values counted as unset. */
private fun Map<String, String>.valueOf(name: String): String? =
    (this[name] ?: this[name.lowercase()])?.trim()?.takeIf { it.isNotEmpty() }

/** `*` bypasses every host, a name bypasses that host, and a name with a leading dot bypasses every host below that domain. */
private fun String?.isBypassedBy(host: String): Boolean =
    this != null && split(",").map { it.trim() }.filter { it.isNotEmpty() }.any { entry ->
        entry == "*" ||
            host.equals(entry, ignoreCase = true) ||
            (entry.startsWith(".") && host.endsWith(entry, ignoreCase = true))
    }

private fun parseProxyValue(value: String): URI? {
    val withScheme = if ("://" in value) value else "http://$value"
    return try {
        URI(withScheme).takeIf { !it.host.isNullOrEmpty() }
    } catch (exception: URISyntaxException) {
        null
    }
}

private const val DEFAULT_PROXY_PORT = 80

/** The client of a mirror whose environment proxy was refused. Each request fails with the refusal, which names the variable. */
internal class RefusedRemoteClient(private val refusal: String) : RemoteClient {

    override fun head(uri: String, credentials: RemoteCredentials?, connectTimeoutInSeconds: Int, readTimeoutInSeconds: Int): Result<FileDetails, ErrorResponse> =
        Result.error(ErrorResponse(BAD_GATEWAY, refusal))

    override fun get(uri: String, credentials: RemoteCredentials?, connectTimeoutInSeconds: Int, readTimeoutInSeconds: Int): Result<InputStream, ErrorResponse> =
        Result.error(ErrorResponse(BAD_GATEWAY, refusal))

}
