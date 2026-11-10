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

package com.reposilite.web

import com.reposilite.Reposilite
import com.reposilite.configuration.local.LocalConfiguration
import com.reposilite.shared.badRequest
import com.reposilite.shared.extensions.error
import com.reposilite.storage.api.UnsupportedLocationException
import com.reposilite.web.api.HttpServerDrainingEvent
import com.reposilite.web.api.HttpServerInitializationEvent
import com.reposilite.web.api.HttpServerStartedEvent
import com.reposilite.web.api.HttpServerStoppedEvent
import com.reposilite.web.application.JavalinConfiguration
import com.reposilite.web.infrastructure.HttpResponseExceptionHandler
import io.javalin.Javalin
import io.javalin.http.HttpResponseException
import io.javalin.util.ConcurrencyUtil
import java.time.Duration
import java.util.concurrent.TimeUnit
import net.onelitefeather.ingot.web.GracefulDrainHandler
import org.eclipse.jetty.io.EofException
import org.eclipse.jetty.server.Handler

class HttpServer {

    private var javalin: Javalin? = null

    /** The handler that refuses new requests during a drain. Installed only when the drain timeout is above zero. */
    private var drainHandler: GracefulDrainHandler? = null

    private var reposilite: Reposilite? = null

    /** The requests still in flight when the drain timeout ended in the last stop. Zero when the drain finished in time. */
    @Volatile
    private var requestsCutOff: Long = 0

    fun start(reposilite: Reposilite) {
        val extensionsManagement = reposilite.extensions
        val localConfiguration = extensionsManagement.facade<LocalConfiguration>()

        val webThreadPool = ConcurrencyUtil.jettyThreadPool(
            name = "Ingot | Web (${localConfiguration.webThreadPool.get()}) -",
            minThreads = localConfiguration.webThreadPool.get(),
            maxThreads = localConfiguration.webThreadPool.get(),
            useLoom = false
        )

        val drainHandler = GracefulDrainHandler().takeIf { reposilite.shutdownSettings.timeout > Duration.ZERO }
        this.drainHandler = drainHandler
        this.reposilite = reposilite

        val javalin = Javalin.start { config ->
            config.jetty.host = reposilite.parameters.hostname
            config.jetty.port = reposilite.parameters.port

            // The drain handler wraps the chain Javalin attaches to, so with the default timeout the chain is unchanged
            config.jetty.modifyServer { server -> drainHandler?.let { server.handler = it } }

            JavalinConfiguration.configure(reposilite, webThreadPool, config)

            config.routes.exception(HttpResponseException::class.java, HttpResponseExceptionHandler())

            config.routes.exception(EofException::class.java) { _, _ ->
                reposilite.logger.warn("Client closed connection")
            }

            config.routes.exception(UnsupportedLocationException::class.java) { exception, ctx ->
                ctx.error(badRequest(exception.message))
            }

            config.events.serverStopping { reposilite.logger.info("Server stopping...") }
            config.events.serverStopped { extensionsManagement.emitEvent(HttpServerStoppedEvent) }

            reposilite.extensions.emitEvent(HttpServerInitializationEvent(reposilite, config))
        }

        this.javalin = javalin
        // Port 0 asks the OS for a free port. Report the port that was bound, so that everything that reads
        // parameters.port afterwards, tests included, talks to the running server.
        reposilite.parameters.port = javalin.port()

        extensionsManagement.emitEvent(HttpServerStartedEvent)
    }

    fun stop() {
        val drainHandler = drainHandler
        val reposilite = reposilite
        if (drainHandler == null || reposilite == null) {
            javalin?.stop()
            return
        }

        // From here on the drain handler refuses new requests with 503, except the status answers
        val drained = drainHandler.shutdown()
        reposilite.extensions.emitEvent(HttpServerDrainingEvent)

        // Bounded by the timeout on its own. Jetty's stop timeout is not used, because its graceful wait would add a
        // second wait of the same length after this one.
        val timeout = reposilite.shutdownSettings.timeout
        val drainedInTime = runCatching { drained.get(timeout.toMillis(), TimeUnit.MILLISECONDS) }.isSuccess
        if (!drainedInTime) {
            requestsCutOff = drainHandler.currentRequestCount
            reposilite.logger.warn(
                "Drain timeout of ${timeout.seconds}s ended with $requestsCutOff request(s) still in flight, they are cut off"
            )
        }

        javalin?.stop()
    }

    /** Requests that the last stop cut off because the drain timeout ended first. */
    fun cutOffRequests(): Long =
        requestsCutOff

    /** The handler the server dispatches to. With a drain timeout it is the drain handler, otherwise the Javalin chain. */
    fun rootHandler(): Handler? =
        javalin?.jettyServer()?.server()?.handler

    fun isAlive(): Boolean =
        javalin?.jettyServer()?.server()?.isStarted ?: false

}
