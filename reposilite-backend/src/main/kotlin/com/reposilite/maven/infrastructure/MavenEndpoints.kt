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

@file:OptIn(net.onelitefeather.ingot.format.ExperimentalIngotFormatApi::class)

package com.reposilite.maven.infrastructure

import com.reposilite.frontend.FrontendFacade
import com.reposilite.maven.MavenFacade
import com.reposilite.maven.api.DeleteRequest
import com.reposilite.maven.api.DeployRequest
import com.reposilite.maven.api.LookupRequest
import com.reposilite.shared.ErrorResponse
import com.reposilite.shared.extensions.NO_STORE_CACHE_CONTROL
import com.reposilite.shared.extensions.applyErrorCacheControl
import com.reposilite.shared.extensions.applyListingHeaders
import com.reposilite.shared.extensions.error
import com.reposilite.shared.extensions.attachmentHeaders
import com.reposilite.shared.extensions.clearContentLength
import com.reposilite.shared.extensions.contentLength
import com.reposilite.shared.http.ByteRanges
import com.reposilite.shared.http.ConditionalRequests
import com.reposilite.shared.http.RangeDecision
import com.reposilite.shared.extensions.uri
import com.reposilite.shared.notFoundError
import com.reposilite.storage.api.DirectoryInfo
import com.reposilite.storage.api.DocumentInfo
import com.reposilite.storage.api.Location
import com.reposilite.token.AccessTokenIdentifier
import com.reposilite.web.api.SESSION_BEARER_SCHEME
import com.reposilite.web.api.TOKEN_BASIC_SCHEME
import io.javalin.http.Context
import io.javalin.http.Header
import io.javalin.http.HttpStatus
import io.javalin.http.HandlerType
import io.javalin.http.HandlerType.HEAD as HEAD_METHOD
import io.javalin.openapi.ContentType.FORM_DATA_MULTIPART
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiContent
import io.javalin.openapi.OpenApiParam
import io.javalin.openapi.OpenApiResponse
import io.javalin.openapi.OpenApiSecurity
import net.onelitefeather.ingot.format.RepositoryRequest
import net.onelitefeather.ingot.format.RepositoryRequestHandler
import net.onelitefeather.ingot.web.SecurityHeaders
import panda.std.Result
import panda.std.asSuccess
import java.io.InputStream
import java.util.Optional

const val X_GENERATE_CHECKSUMS = "X-Generate-Checksums"

/** The methods a proxy or a group still answers. Both take no uploads, so only the reads remain. */
private const val READ_METHODS = "GET, HEAD"

/**
 * The Maven handlers of the repository routes. The routes themselves are served by the repository dispatcher, which hands the
 * requests of the `maven2` format to [MavenRepositoryRequestHandler]. The annotations describe the paths of the OpenAPI document.
 */
internal class MavenEndpoints(
    private val mavenFacade: MavenFacade,
    private val frontendFacade: FrontendFacade,
    private val compressionStrategy: String
) {

    @OpenApi(
        path = "/{repository}/{gav}",
        methods = [HttpMethod.GET],
        tags = ["Maven"],
        summary = "Browse the contents of repositories",
        description = "The route may return various responses to properly handle Maven specification and frontend application using the same path.",
        pathParams = [
            OpenApiParam(name = "repository", description = "Destination repository", required = true),
            OpenApiParam(name = "gav", description = "Artifact path qualifier", required = true, allowEmptyValue = true)
        ],
        responses = [
            OpenApiResponse(status = "200", description = "Input stream of requested file", content = [OpenApiContent(type = FORM_DATA_MULTIPART)]),
            OpenApiResponse(status = "404", description = "Returns 404 (for Maven) with frontend (for user) as a response if requested resource is not located in the current repository")
        ]
    )
    fun findFile(ctx: Context, identifier: AccessTokenIdentifier?, repository: String, gav: Location): Result<Unit, ErrorResponse> {
        val request = LookupRequest(accessToken = identifier, repository = repository, gav = gav)
        val allDetails = mavenFacade.findDetails(request)

        return allDetails.flatMap { details ->
            when (details) {
                is DocumentInfo -> serveDocument(ctx, request, details)
                is DirectoryInfo -> {
                    SecurityHeaders.markRepositoryContent(ctx)
                    ctx.applyListingHeaders()
                    ctx.html(
                        createDirectoryIndexPage(
                            basePath = frontendFacade.resolveBasePath(ctx.header(frontendFacade.forwardedPrefixHeader.get())),
                            uri = ctx.uri(),
                            authenticatedFiles = mavenFacade.getAvailableFiles(request, details),
                        )
                    )
                    Unit.asSuccess()
                }
                else ->
                    throw IllegalStateException("Expected file details, but got $details")
            }
        }
        .onError {
            if (ctx.path().startsWith("/api/")) {
                // No repository is called "api": this is a request for an API route that does not exist
                ctx.error(it)
                return@onError
            }
            if (it.status == HttpStatus.UNAUTHORIZED.code) {
                // A refused request is answered as not cacheable, so that a shared cache never keeps the refusal
                ctx.header(Header.CACHE_CONTROL, NO_STORE_CACHE_CONTROL)
            }
            // A missing file is revalidated by a shared cache before it is served again, so a later upload shows at once
            ctx.applyErrorCacheControl()
            ctx.status(it.status).html(
                frontendFacade.createNotFoundPage(
                    originUri = ctx.uri(),
                    details = it.message,
                    forwardedPrefix = ctx.header(frontendFacade.forwardedPrefixHeader.get()),
                ),
            )
            mavenFacade.logger.debug("FIND | Could not find file due to $it")
        }
    }

    /**
     * Serves an artifact with an entity tag and byte range support (RFC 9110, sections 13 and 14). Requests that carry
     * none of the conditional or range headers get the whole body exactly as before. Cache-Control is still decided by
     * the repository, so snapshots and metadata stay uncached, also on 206 and 304 answers.
     *
     * Ranges are served from the repository's own storage. A file that is not stored yet (a first fetch through a
     * proxy) is answered with the whole body. A ranged answer carries its own Content-Length for the stored bytes.
     */
    private fun serveDocument(ctx: Context, request: LookupRequest, details: DocumentInfo): Result<Unit, ErrorResponse> {
        val etag = mavenFacade.findEntityTag(request, details)
        val rangesSupported = details.contentLength >= 0

        fun writeHeaders() {
            ctx.attachmentHeaders(
                name = details.name,
                contentType = details.contentType,
                contentLength = details.contentLength,
                lastTimeModified = details.lastModifiedTime,
                compressionStrategy = compressionStrategy,
                cache = mavenFacade.acceptsCachingOf(request),
                cacheScope = mavenFacade.cacheScopeOf(request.repository),
            )
            etag?.let { ctx.header("ETag", it) }
            if (rangesSupported) {
                ctx.header("Accept-Ranges", "bytes")
            }
        }

        if (ConditionalRequests.isNotModified(ctx.header("If-None-Match"), ctx.header("If-Modified-Since"), etag, details.lastModifiedTime)) {
            writeHeaders()
            ctx.clearContentLength()
            ctx.status(HttpStatus.NOT_MODIFIED)
            return Unit.asSuccess()
        }

        val rangeHeader = ctx.header("Range")
        if (rangesSupported && rangeHeader != null && ConditionalRequests.rangeApplies(ctx.header("If-Range"), etag, details.lastModifiedTime)) {
            when (val decision = ByteRanges.resolve(rangeHeader, details.contentLength)) {
                RangeDecision.Unsatisfiable -> {
                    ctx.header("Accept-Ranges", "bytes")
                    ctx.header("Content-Range", "bytes */${details.contentLength}")
                    ctx.status(HttpStatus.RANGE_NOT_SATISFIABLE)
                    return Unit.asSuccess()
                }
                is RangeDecision.Partial -> {
                    // A HEAD answers with the status and headers of the GET, so it needs no stream
                    val stream = when (ctx.method()) {
                        HEAD_METHOD -> Optional.empty<InputStream>()
                        else -> mavenFacade.findDataRange(request, decision.start, decision.length)
                            .let { if (it.isErr) return it.map { } else it.get() }
                    }
                    if (stream.isPresent || ctx.method() == HEAD_METHOD) {
                        writeHeaders()
                        ctx.header("Content-Range", "bytes ${decision.start}-${decision.endInclusive}/${details.contentLength}")
                        ctx.contentLength(decision.length)
                        ctx.status(HttpStatus.PARTIAL_CONTENT)
                        stream.ifPresent { ctx.result(it) }
                        return Unit.asSuccess()
                    }
                }
                RangeDecision.Full -> Unit
            }
        }

        return when (ctx.method()) {
            HEAD_METHOD -> Result.ok<Unit, ErrorResponse>(Unit).also { writeHeaders() }
            else -> mavenFacade.findData(request).map { data ->
                ctx.result(data)
                writeHeaders()
            }
        }
    }

    @OpenApi(
        security = [OpenApiSecurity(name = TOKEN_BASIC_SCHEME), OpenApiSecurity(name = SESSION_BEARER_SCHEME)],
        tags = [ "Maven" ],
        path = "/{repository}/{gav}",
        methods = [HttpMethod.POST, HttpMethod.PUT],
        summary = "Deploy artifact to the repository",
        description = "Deploy supports both, POST and PUT, methods and allows to deploy artifact builds",
        pathParams = [
            OpenApiParam(name = "repository", description = "Destination repository", required = true),
            OpenApiParam(name = "gav", description = "Artifact path qualifier", required = true)
        ],
        headers = [
            OpenApiParam(name = X_GENERATE_CHECKSUMS, description = "Determines if Ingot should generate checksums for this file", required = false)
        ],
        responses = [
            OpenApiResponse(status = "200", description = "Input stream of requested file", content = [OpenApiContent(type = FORM_DATA_MULTIPART)]),
            OpenApiResponse(status = "401", description = "Returns 401 for invalid credentials"),
            OpenApiResponse(status = "400", description = "Returns 400 if the version policy of the repository refuses the version of the artifact, e.g. a snapshot into a RELEASE repository"),
            OpenApiResponse(status = "405", description = "Returns 405 if the repository is a proxy or a group, which take no uploads, or if it is read-only"),
            OpenApiResponse(status = "507", description = "Returns 507 if Ingot does not have enough disk space to store the uploaded file")
        ]
    )
    fun deployFile(ctx: Context, by: String, repositoryName: String, gav: Location): Result<Unit, ErrorResponse> {
        val repository = mavenFacade.getRepository(repositoryName) ?: return notFoundError("Repository not found")
        return mavenFacade.checkAcceptsUploads(repository)
            .onError { ctx.header(Header.ALLOW, READ_METHODS) }
            .flatMap {
                DeployRequest(repository, gav, by, ctx.bodyInputStream(), ctx.header(X_GENERATE_CHECKSUMS) == "true")
                    .let { request -> mavenFacade.deployFile(request) }
            }
            .onError { error -> mavenFacade.logger.debug("Cannot deploy artifact due to: ${error.message}") }
    }

    @OpenApi(
        security = [OpenApiSecurity(name = TOKEN_BASIC_SCHEME), OpenApiSecurity(name = SESSION_BEARER_SCHEME)],
        tags = ["Maven"],
        path = "/{repository}/{gav}",
        summary = "Delete the given file from repository",
        description = "Returns 405 if the repository is a proxy or a group, which take no uploads and no deletions, or if it is read-only.",
        methods = [HttpMethod.DELETE],
        pathParams = [
            OpenApiParam(name = "repository", description = "Destination repository", required = true),
            OpenApiParam(name = "gav", description = "Artifact path qualifier", required = true)
        ],
        responses = [
            OpenApiResponse(status = "405", description = "Returns 405 if the repository is a proxy or a group, which take no deletions, or if it is read-only")
        ]
    )
    fun deleteFile(ctx: Context, identifier: AccessTokenIdentifier?, by: String, repositoryName: String, gav: Location): Result<Unit, ErrorResponse> {
        val repository = mavenFacade.getRepository(repositoryName) ?: return notFoundError("Repository not found")
        return mavenFacade.checkAcceptsUploads(repository)
            .onError { ctx.header(Header.ALLOW, READ_METHODS) }
            .flatMap { mavenFacade.deleteFile(DeleteRequest(requireNotNull(identifier) { "Deletion needs an authenticated token" }, repository, gav, by)) }
    }

}

/** Hands the requests of the maven2 format to the Maven handlers, by the method of the request. */
internal class MavenRepositoryRequestHandler(private val endpoints: MavenEndpoints) : RepositoryRequestHandler {

    override fun handle(request: RepositoryRequest): Result<Unit, ErrorResponse> =
        when (request.ctx.method()) {
            HandlerType.HEAD, HandlerType.GET ->
                endpoints.findFile(request.ctx, request.identifier, request.repository, request.path)
            HandlerType.POST, HandlerType.PUT ->
                endpoints.deployFile(request.ctx, request.by, request.repository, request.path)
            HandlerType.DELETE ->
                endpoints.deleteFile(request.ctx, request.identifier, request.by, request.repository, request.path)
            else -> error("The maven format does not answer ${request.ctx.method()}")
        }

}
