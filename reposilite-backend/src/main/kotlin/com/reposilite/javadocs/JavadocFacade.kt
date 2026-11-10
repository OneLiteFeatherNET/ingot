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

package com.reposilite.javadocs

import com.reposilite.javadocs.api.JavadocPageRequest
import com.reposilite.javadocs.api.JavadocRawRequest
import com.reposilite.javadocs.api.JavadocRawResponse
import com.reposilite.javadocs.api.JavadocResponse
import com.reposilite.journalist.Journalist
import com.reposilite.journalist.Logger
import com.reposilite.maven.MavenFacade
import com.reposilite.maven.Repository
import com.reposilite.maven.api.VersionLookupRequest
import com.reposilite.plugin.api.Facade
import com.reposilite.shared.ErrorResponse
import com.reposilite.shared.badRequest
import com.reposilite.shared.badRequestError
import com.reposilite.shared.notFound
import com.reposilite.shared.notFoundError
import com.reposilite.storage.api.Location
import com.reposilite.storage.deleteRecursivelyInside
import com.reposilite.token.AccessTokenIdentifier
import io.javalin.http.ContentType
import panda.std.Result
import panda.std.Result.supplyThrowing
import panda.std.asSuccess
import panda.utilities.StringUtils
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.notExists
import kotlin.io.path.readLines

private const val LATEST_PATTERN = "/latest"

/**
 * The artifact directory whose unpacked javadoc cache is evicted when a jar at [gav] is deployed, or null when the jar is not a
 * javadoc jar. The directory holds every version of the artifact, so it is two levels above the jar.
 */
internal fun javadocEvictionRoot(gav: Location, suffixes: List<String>): Location? =
    gav.takeIf { location -> suffixes.any { location.toString().endsWith(it) } }
        ?.getParent()
        ?.getParent()

class JavadocFacade internal constructor(
    private val journalist: Journalist,
    val mavenFacade: MavenFacade,
    private val javadocFolder: Path,
    private val javadocContainerService: JavadocContainerService
) : Journalist, Facade {

    private val supportedExtensions = mapOf(
        "html" to ContentType.TEXT_HTML,
        "css" to ContentType.TEXT_CSS,
        "js" to ContentType.TEXT_JS,
        "json" to ContentType.TEXT_JS,
        "png" to ContentType.IMAGE_PNG,
        "svg" to ContentType.IMAGE_SVG,
    )

    private data class JavadocPlainFile(
        val targetPath: Path,
        val extension: String,
        val contentType: ContentType
    )

    fun findJavadocPage(request: JavadocPageRequest): Result<JavadocResponse, ErrorResponse> =
        with (request) {
            mavenFacade.canAccessResource(accessToken, repository, gav)
                .flatMap { createPage(accessToken, repository, resolveGav(request)) }
                .onError { logger.error("Cannot extract javadoc: ${it.message} (${it.status})}") }
        }

    fun findRawJavadocResource(request: JavadocRawRequest): Result<JavadocRawResponse, ErrorResponse> =
        with (request) {
            mavenFacade.canAccessResource(accessToken, repository, gav)
                .flatMap { javadocContainerService.loadContainer(accessToken, repository, gav) }
                .map { it.javadocUnpackPath.resolve(resource.toString()) }
                .filter({ it.exists() }, { notFound("Resource $resource not found") })
                .map {
                    JavadocRawResponse(
                        contentType = supportedExtensions[resource.getExtension()] ?: ContentType.APPLICATION_OCTET_STREAM,
                        content = it.inputStream()
                    )
                }
        }

    private fun createPage(accessToken: AccessTokenIdentifier?, repository: Repository, gav: Location): Result<JavadocResponse, ErrorResponse> {
        val resourcesFile = createPlainFile(javadocFolder, repository, gav)

        return when {
            /* File not found */
            resourcesFile != null && resourcesFile.targetPath.notExists() ->
                JavadocResponse(resourcesFile.contentType.mimeType, StringUtils.EMPTY).asSuccess()
            /* File exists */
            resourcesFile != null ->
                supplyThrowing {
                    JavadocResponse(resourcesFile.contentType.mimeType, readFile(resourcesFile.targetPath))
                }.mapErr {
                    notFound("Resource not found!")
                }
            /* Premature resources request */
            gav.contains("/resources/") ->
                notFoundError("Resources are unavailable before extraction")
            /* Load resource */
            else ->
                javadocContainerService
                    .loadContainer(accessToken, repository, gav)
                    .map { JavadocResponse(ContentType.HTML, readFile(it.javadocContainerIndex)) }
        }
    }

    private fun createPlainFile(javadocFolder: Path, repository: Repository, gav: Location): JavadocPlainFile? =
        javadocFolder
            .resolve(repository.name)
            .resolve(gav.toString())
            .let { targetPath -> targetPath to supportedExtensions[gav.getExtension()] }
            .takeIf { (_, contentType) -> contentType != null }
            ?.let { (targetPath, contentType) -> JavadocPlainFile(targetPath, gav.getExtension(), contentType!!) }

    private fun readFile(indexFile: Path): String =
        indexFile.readLines().joinToString(separator = "\n")

    private fun resolveGav(request: JavadocPageRequest): Location =
        request.gav
            .takeIf { it.contains("/latest") }
            ?.let { request.gav.locationBeforeLast("/latest") }
            ?.let { gavWithoutVersion -> VersionLookupRequest(request.accessToken, request.repository, gavWithoutVersion) }
            ?.let { mavenFacade.findLatestVersion(it) }
            ?.map { request.gav.replace(LATEST_PATTERN, "/%s".format(it.version)) }
            ?.orNull()
            ?: request.gav

    /**
     * Drops the unpacked javadoc cache of every version of the artifact at [artifactRoot] in [repository]. An absent cache is a no-op.
     * Throws when the directory is not strictly inside the javadoc cache of the repository.
     */
    fun evictArtifact(repository: String, artifactRoot: Location) {
        val repositoryRoot = javadocFolder.resolve(repository)
        val artifactDirectory = repositoryRoot.resolve(artifactRoot.toString())

        if (artifactDirectory.exists()) {
            artifactDirectory.deleteRecursivelyInside(repositoryRoot)
        }
    }

    /**
     * Applies a `javadoc.invalidated` event received from the cluster bus. The payload is not trusted: the repository must exist on
     * this instance, the path must be a valid repository location, and the directory it names must lie strictly inside the javadoc
     * cache of that repository. A refused event deletes nothing.
     */
    fun evictReceived(repository: String, path: String): Result<Unit, ErrorResponse> {
        if (mavenFacade.getRepository(repository) == null) {
            return notFoundError("Javadoc eviction refused, unknown repository $repository")
        }
        // Location.of silently strips leading slashes, so an absolute path is refused here rather than rebased under the root.
        if (path.startsWith("/")) {
            return badRequestError("Javadoc eviction refused, absolute path $path")
        }

        return Location.ofRequest(path).flatMap { artifactRoot ->
            supplyThrowing { evictArtifact(repository, artifactRoot) }
                .mapErr { badRequest("Javadoc eviction refused for $path: ${it.message}") }
        }
    }

    override fun getLogger(): Logger =
        journalist.logger

}
