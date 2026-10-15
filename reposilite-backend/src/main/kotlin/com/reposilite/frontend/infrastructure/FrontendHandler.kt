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

package com.reposilite.frontend.infrastructure

import com.reposilite.frontend.DashboardPaths
import com.reposilite.frontend.DashboardRouteDecision
import com.reposilite.frontend.DashboardRouteTarget.DASHBOARD
import com.reposilite.frontend.DashboardRouteTarget.MAVEN
import com.reposilite.frontend.FrontendFacade
import com.reposilite.frontend.Source
import com.reposilite.maven.MavenFacade
import com.reposilite.maven.infrastructure.MavenEndpoints
import com.reposilite.shared.ContextDsl
import com.reposilite.shared.ErrorResponse
import com.reposilite.shared.extensions.encoding
import com.reposilite.shared.notFoundError
import com.reposilite.status.FailureFacade
import com.reposilite.storage.api.Location
import com.reposilite.storage.api.toLocation
import com.reposilite.storage.getExtension
import com.reposilite.storage.inputStream
import com.reposilite.web.api.ReposiliteRoute
import com.reposilite.web.api.ReposiliteRoutes
import io.javalin.community.routing.Route.GET
import io.javalin.community.routing.Route.HEAD
import io.javalin.http.ContentType
import io.javalin.http.Context
import io.javalin.http.HttpStatus.INTERNAL_SERVER_ERROR
import panda.std.Result
import panda.std.asError
import panda.std.asSuccess
import java.io.InputStream
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.useDirectoryEntries

internal sealed class FrontendHandler(
    private val frontendFacade: FrontendFacade,
    private val failureFacade: FailureFacade,
) : ReposiliteRoutes() {

    protected fun respondWithResource(ctx: Context, uri: String, source: Source): Result<InputStream, ErrorResponse> {
        val contentType = ContentType.contentTypeByExtension(uri.getExtension())
        ctx.contentType(contentType?.mimeType ?: ContentType.OCTET_STREAM)

        return when (uri.contains(".html") || uri.contains(".js")) {
            true -> respondWithProcessedResource(ctx, uri, source)
            else -> respondWithRawResource(source)
        }
    }

    private fun respondWithProcessedResource(ctx: Context, uri: String, source: Source): Result<InputStream, ErrorResponse> =
        frontendFacade.resolve(uri) { source.get() }
            ?.let { resource ->
                resource
                    .supply()
                    .peek { ctx.encoding(UTF_8) }
                    .onError { failureFacade.throwException(uri, it) }
                    .mapErr { ErrorResponse(INTERNAL_SERVER_ERROR, "Cannot serve resource") }
            }
            ?: notFoundError("Resource not found")

    private fun respondWithRawResource(source: Source): Result<InputStream, ErrorResponse> =
        source.get()
            ?.asSuccess()
            ?: notFoundError("Resource not found")

}

internal class ResourcesFrontendHandler(
    failureFacade: FailureFacade,
    frontendFacade: FrontendFacade,
    private val resourcesDirectory: String,
    private val mavenFacade: MavenFacade,
    private val mavenEndpoints: MavenEndpoints,
) : FrontendHandler(frontendFacade, failureFacade) {

    private val notFoundHandler = NotFoundHandler(frontendFacade)

    private val defaultHandler = ReposiliteRoute<InputStream>("/", GET, HEAD) {
        response = respondWithBundledResource(ctx, "index.html")
    }

    private val indexHandler = ReposiliteRoute<InputStream>("/index.html", GET, HEAD) {
        response = respondWithBundledResource(ctx, "index.html")
    }

    private val assetsHandler = ReposiliteRoute<InputStream>("/assets/<path>", GET) {
        response = respondWithBundledResource(ctx, "assets/${ctx.pathParam("path")}")
    }

    // Javalin answers a request with the first endpoint that matches it, and never falls through to the next one.
    // So every app route needs HEAD as well, and a route whose first segment names a repository has to hand the
    // request to Maven itself when DashboardRouteDecision says so. Without that hand-over, a repository "settings"
    // would receive the dashboard for every Maven and Gradle request under /settings/.
    private val appRoutes = DashboardPaths.APP_ROUTES.map { path ->
        val repository = path.removePrefix("/").substringBefore('/')

        ReposiliteRoute<InputStream>(path, GET, HEAD) {
            val target = DashboardRouteDecision.decide(
                repositoryExists = mavenFacade.getRepository(repository) != null,
                method = ctx.method(),
                secFetchMode = ctx.header("Sec-Fetch-Mode"),
                secFetchDest = ctx.header("Sec-Fetch-Dest"),
            )

            when (target) {
                DASHBOARD -> response = respondWithBundledResource(ctx, "index.html")
                MAVEN -> answerFromMaven(repository)
            }
        }
    }

    /**
     * Answers like the Maven route for `/{repository}/<gav>` does, with the same access check and the same
     * response bodies. Maven matches only paths that have a gav, so a bare app path such as `/settings` is no
     * Maven request: it gets the not-found page that any unclaimed path gets. The DSL route wrapper turns a thrown
     * exception into a 500, so that page is written directly rather than thrown as a NotFoundResponse.
     */
    private fun ContextDsl<InputStream>.answerFromMaven(repository: String) {
        val gav = ctx.pathParamMap().values.singleOrNull()
        if (gav == null) {
            notFoundHandler.handle(ctx)
            return
        }

        accessed {
            Location.ofRequest(gav)
                .peek { mavenEndpoints.findFile(ctx, this?.identifier, repository, it) }
                .onError { response = it.asError() }
        }
    }

    private fun respondWithBundledResource(ctx: Context, uri: String): Result<InputStream, ErrorResponse> =
        respondWithResource(ctx, uri) {
            FrontendFacade::class.java.getResourceAsStream("/$resourcesDirectory/$uri") ?: "".toByteArray().inputStream()
        }

    override val routes = routes(defaultHandler, indexHandler, assetsHandler, *appRoutes.toTypedArray())

}

internal class CustomFrontendHandler(
    failureFacade: FailureFacade,
    frontendFacade: FrontendFacade,
    directory: Path
) : FrontendHandler(frontendFacade, failureFacade) {

    private fun rootFileHandler(file: Path) = ReposiliteRoute("/${file.name}", GET) {
        response = respondWithResource(ctx, file.name) {
            file.inputStream().orNull()
        }
    }

    private fun directoryHandler(directory: Path) = ReposiliteRoute("/${directory.fileName}/<path>", GET) {
        response = respondWithResource(ctx, directory.name) {
            parameter("path")
                .toLocation()
                .toPath()
                .let { directory.resolve(it) }
                .inputStream()
                .orNull()
        }
    }

    private fun indexHandler(directory: Path) = ReposiliteRoute("/", GET) {
        response = respondWithResource(ctx, "index.html") {
            directory
                .resolve("index.html")
                .inputStream()
                .orNull()
        }
    }

    override val routes =
        directory.useDirectoryEntries { staticDirectoryStream ->
            staticDirectoryStream.map {
                    when {
                        it.isDirectory() -> directoryHandler(it)
                        else -> rootFileHandler(it)
                    }
                }
                .toMutableSet()
                .also { it.add(indexHandler(directory)) }
                .let { routes(*it.toTypedArray()) }
        }

}
