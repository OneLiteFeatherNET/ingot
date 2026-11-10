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

package com.reposilite.web.application

import io.javalin.plugin.Plugin as JavalinPlugin
import com.reposilite.Reposilite
import com.reposilite.ReposiliteObjectMapper
import com.reposilite.VERSION
import com.reposilite.configuration.local.LocalConfiguration
import com.reposilite.configuration.shared.SharedConfigurationFacade
import com.reposilite.frontend.application.FrontendSettings
import com.reposilite.journalist.Journalist
import com.reposilite.web.api.HttpServerConfigurationEvent
import io.javalin.http.Header
import com.reposilite.web.api.HttpServerStartedEvent
import com.reposilite.web.api.RoutingSetupEvent
import com.reposilite.web.api.SESSION_BEARER_SCHEME
import com.reposilite.web.api.TOKEN_BASIC_SCHEME
import com.reposilite.web.infrastructure.ApiCacheBypassHandler
import com.reposilite.web.infrastructure.EndpointAccessLoggingHandler
import com.reposilite.web.infrastructure.createReposiliteDsl
import io.javalin.community.routing.RoutingSetupScope
import io.javalin.community.ssl.SslPlugin
import io.javalin.compression.Brotli
import io.javalin.compression.CompressionStrategy
import io.javalin.config.JavalinConfig
import io.javalin.config.JavalinState
import io.javalin.json.JavalinJackson
import io.javalin.openapi.plugin.OpenApiPlugin
import io.javalin.plugin.bundled.SslRedirectPlugin
import kotlin.time.Duration.Companion.minutes
import net.onelitefeather.ingot.web.RequestLimitHandler
import net.onelitefeather.ingot.web.RequestLimiter
import net.onelitefeather.ingot.readonly.ReadOnlyFacade
import net.onelitefeather.ingot.web.TrustedProxies
import net.onelitefeather.ingot.web.SecurityHeaders
import net.onelitefeather.ingot.web.SecurityHeadersSettings
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.ServerConnector
import org.eclipse.jetty.util.thread.ThreadPool
import panda.std.reactive.Reference

internal object JavalinConfiguration {

    internal fun configure(reposilite: Reposilite, webThreadPool: ThreadPool, config: JavalinConfig) {
        val server = Server(webThreadPool)
        config.unsafe.jettyInternal.server = server
        reposilite.extensions.emitEvent(HttpServerConfigurationEvent(reposilite, config))

        val localConfiguration = reposilite.extensions.facade<LocalConfiguration>()
        val sharedConfigurationFacade = reposilite.extensions.facade<SharedConfigurationFacade>()
        val webSettings = sharedConfigurationFacade.getDomainSettings<WebSettings>()
        val frontendSettings = sharedConfigurationFacade.getDomainSettings<FrontendSettings>()

        reposilite.extensions.registerEvent { _: HttpServerStartedEvent ->
            server.connectors.filterIsInstance<ServerConnector>().forEach { it.idleTimeout = localConfiguration.idleTimeout.get() }
        }

        if (localConfiguration.bypassExternalCache.get()) {
            reposilite.extensions.registerEvent { event: RoutingSetupEvent ->
                event.registerRoutes(EndpointAccessLoggingHandler())
                event.registerRoutes(ApiCacheBypassHandler())
                reposilite.logger.debug("CacheBypassHandler has been registered")
            }
        }

        configureJavalin(reposilite, config, localConfiguration, webSettings)
        configureJsonSerialization(config)
        configureSSL(reposilite, localConfiguration, config)
        configureCors(config)
        configureSecurityHeaders(config)
        configureOpenApi(config, frontendSettings.get())
        configureDebug(reposilite, localConfiguration, config)
        configureRoutingPlugin(config, reposilite)
        configureRequestLimit(reposilite, localConfiguration, config)
    }

    /**
     * Installs the request limit only when it is set. It wraps the handler that is already on the server, so the drain
     * handler stays in the chain, and it sits outside the Javalin chain so that a refusal costs no routing.
     */
    private fun configureRequestLimit(reposilite: Reposilite, localConfiguration: LocalConfiguration, config: JavalinConfig) {
        val limit = reposilite.maxConcurrentRequests ?: return
        val limitHandler = RequestLimitHandler(RequestLimiter(limit, localConfiguration.basePath.get()))
        config.jetty.modifyServer { server ->
            limitHandler.handler = server.handler
            server.handler = limitHandler
        }
    }

    private fun configureJavalin(reposilite: Reposilite, config: JavalinConfig, localConfiguration: LocalConfiguration, webSettings: Reference<WebSettings>) {
        config.startup.showJavalinBanner = false
        config.http.asyncTimeout = 10.minutes.inWholeMilliseconds
        val trustedProxies = TrustedProxies.fromEnvironment()
        TrustedProxies.startupWarning(trustedProxies, webSettings.get().forwardedIp)?.let { reposilite.logger.warn(it) }
        config.contextResolver.ip = { trustedProxies.resolve(it.req().remoteAddr, it.header(webSettings.get().forwardedIp)) }

        System.getProperty("javalin.maxRequestSize")?.let {
            config.http.maxRequestSize = it.toLong()
        }

        config.http.compressionStrategy = when (localConfiguration.compressionStrategy.get().lowercase()) {
            "none" -> CompressionStrategy.NONE
            "gzip" -> CompressionStrategy.GZIP
            "brotli" -> CompressionStrategy(Brotli(4), null)
            else -> error("Unknown compression strategy ${localConfiguration.compressionStrategy.get()}")
        }
    }

    private fun configureRoutingPlugin(config: JavalinConfig, reposilite: Reposilite) {
        val extensionManager = reposilite.extensions

        val readOnlyFacade = extensionManager.facade<ReadOnlyFacade>()

        val reposiliteDsl = createReposiliteDsl(
            journalist = reposilite.logger,
            failureFacade = extensionManager.facade(),
            accessTokenFacade = extensionManager.facade(),
            authenticationFacade = extensionManager.facade(),
            loginFacade = extensionManager.facade(),
            basePath = extensionManager.facade<LocalConfiguration>().basePath,
            readOnly = readOnlyFacade::isReadOnly,
        )

        val dslRoutes = extensionManager.emitEvent(RoutingSetupEvent(reposilite))
            .getRoutes()
            .asSequence()
            .flatMap { it.toDslRoutes() }
            .distinctBy { "${it.method.name}:${it.path}" }
            .toSet()

        config.registerPlugin(object : JavalinPlugin<Unit?>() {
            override fun onStart(state: JavalinState) {
                reposiliteDsl.initialize(state) { register(dslRoutes) }
            }
        })
    }

    private fun configureJsonSerialization(config: JavalinConfig) {
        val objectMapper = ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER
        config.jsonMapper(JavalinJackson(objectMapper))
    }

    private fun configureSSL(reposilite: Reposilite, localConfiguration: LocalConfiguration, config: JavalinConfig) {
        if (localConfiguration.sslEnabled.get()) {
            reposilite.logger.info("Enabling SSL connector at ::${localConfiguration.sslPort.get()}")

            config.registerPlugin(SslPlugin {
                it.insecure = true
                it.insecurePort = localConfiguration.port.get()

                it.secure = true
                it.securePort = localConfiguration.sslPort.get()

                val keyConfiguration = localConfiguration.keyPath.map { path ->
                    path.replace(
                        "\${WORKING_DIRECTORY}",
                        reposilite.parameters.workingDirectory.toAbsolutePath().toString()
                    )
                }
                val keyPassword = localConfiguration.keyPassword.get()

                when {
                    keyConfiguration.endsWith(".pem") -> {
                        val (certPath, keyPath) = keyConfiguration.split(" ")
                        it.pemFromPath(certPath, keyPath, keyPassword)
                    }
                    keyConfiguration.endsWith(".jks") -> it.keystoreFromPath(keyConfiguration, keyPassword)
                    else -> throw IllegalArgumentException("Provided key extension is not supported.")
                }

                it.configConnectors { connector -> connector.idleTimeout = localConfiguration.idleTimeout.get() }
                it.sniHostCheck = false
            })
        }

        if (localConfiguration.enforceSsl.get()) {
            config.registerPlugin(SslRedirectPlugin {
                it.redirectOnLocalhost = true
                it.sslPort = localConfiguration.sslPort.get()
            })
        }
    }

    private fun configureCors(config: JavalinConfig) {
        config.bundledPlugins.enableCors { cors ->
            cors.addRule {
                it.anyHost()
                // Separately deployed dashboards read entity tags for optimistic concurrency, browsers hide them otherwise
                it.exposeHeader(Header.ETAG)
            }
        }
    }

    private fun configureSecurityHeaders(config: JavalinConfig) {
        val securityHeaders = SecurityHeaders(SecurityHeadersSettings.resolve())
        config.routes.after { ctx -> securityHeaders.apply(ctx) }
    }

    private fun configureOpenApi(config: JavalinConfig, frontendSettings: FrontendSettings) {
        config.registerPlugin(OpenApiPlugin { openapi ->
            openapi.withDefinitionConfiguration { _, builder ->
                builder.info {
                    it.title(frontendSettings.title)
                    it.description(frontendSettings.description)
                    it.version(VERSION)
                }
                // The two credential forms the API accepts: an access token as HTTP Basic, and the bearer session of a login.
                // Routes that need a login refer to them by name, so a generated client knows to send either one.
                builder.withBasicAuth(TOKEN_BASIC_SCHEME) { }
                builder.withBearerAuth(SESSION_BEARER_SCHEME) { }
            }
        })
    }

    private fun configureDebug(journalist: Journalist, localConfiguration: LocalConfiguration, config: JavalinConfig) {
        if (localConfiguration.debugEnabled.get()) {
            // config.requestCacheSize = FilesUtils.displaySizeToBytesCount(System.getProperty("reposilite.requestCacheSize", "8MB"));
            // Reposilite.getLogger().debug("requestCacheSize set to " + config.requestCacheSize + " bytes");
            journalist.logger.info("Debug enabled")
            config.bundledPlugins.enableDevLogging()
        }
    }

}
