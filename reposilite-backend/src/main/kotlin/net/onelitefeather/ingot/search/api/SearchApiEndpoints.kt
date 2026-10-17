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

package net.onelitefeather.ingot.search.api

import com.fasterxml.jackson.core.JsonProcessingException
import com.reposilite.ReposiliteObjectMapper
import com.reposilite.maven.MavenFacade
import com.reposilite.maven.RepositoryVisibility
import com.reposilite.shared.ContextDsl
import com.reposilite.shared.ErrorResponse
import com.reposilite.shared.toErrorResponse
import com.reposilite.token.AccessTokenFacade
import com.reposilite.token.api.AccessTokenDto
import com.reposilite.web.api.ReposiliteRoute
import com.reposilite.web.api.ReposiliteRoutes
import io.javalin.community.routing.Route.GET
import io.javalin.community.routing.Route.POST
import io.javalin.http.Context
import io.javalin.http.HttpStatus.ACCEPTED
import io.javalin.http.HttpStatus.BAD_REQUEST
import io.javalin.http.HttpStatus.CONFLICT
import io.javalin.http.HttpStatus.NOT_FOUND
import io.javalin.http.HttpStatus.NO_CONTENT
import io.javalin.http.HttpStatus.SERVICE_UNAVAILABLE
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiContent
import io.javalin.openapi.OpenApiParam
import io.javalin.openapi.OpenApiRequestBody
import io.javalin.openapi.OpenApiResponse
import net.onelitefeather.ingot.search.ArtifactFileItem
import net.onelitefeather.ingot.search.ArtifactFilesQuery
import net.onelitefeather.ingot.search.ArtifactSearchEngine
import net.onelitefeather.ingot.search.ArtifactSearchItem
import net.onelitefeather.ingot.search.ArtifactSearchQuery
import net.onelitefeather.ingot.search.IndexState
import net.onelitefeather.ingot.search.IndexStatus
import net.onelitefeather.ingot.search.InvalidSearchQueryException
import net.onelitefeather.ingot.search.OriginFilter
import net.onelitefeather.ingot.search.SearchFacets
import net.onelitefeather.ingot.search.SearchOrder
import net.onelitefeather.ingot.search.SearchSort
import net.onelitefeather.ingot.search.SearchUnavailableException
import net.onelitefeather.ingot.search.SnapshotFilter
import net.onelitefeather.ingot.search.access.SearchAccess
import net.onelitefeather.ingot.search.access.SearchAccessCompiler
import net.onelitefeather.ingot.search.access.SearchGrantSetTooLargeException
import net.onelitefeather.ingot.search.application.CancelOutcome
import net.onelitefeather.ingot.search.application.LastVerifyReport
import net.onelitefeather.ingot.search.application.RunStart
import net.onelitefeather.ingot.search.application.SearchFacade
import net.onelitefeather.ingot.search.application.SearchIndexAdmin
import net.onelitefeather.ingot.search.runs.RunCounters
import net.onelitefeather.ingot.search.runs.RunKind
import net.onelitefeather.ingot.search.runs.RunStatus
import net.onelitefeather.ingot.search.runs.RunSummary
import net.onelitefeather.ingot.search.runs.RunTrigger
import panda.std.Result
import panda.std.asError
import java.time.Instant

/**
 * The search API of artifact search (artifact search spec, section 6). The search and the file lookup are open to anonymous
 * callers: the subject of the request decides which rows it sees. The index administration is for managers only.
 *
 * Every route answers 404 while search is disabled, as if it did not exist, whoever asks (6.4). A query the engine refuses
 * answers 400 and names the parameter; a read that cannot finish, or a grant set that is too large, answers 503.
 */
internal class SearchApiEndpoints(
    private val search: SearchFacade,
    private val accessTokens: AccessTokenFacade,
    private val maven: MavenFacade,
) : ReposiliteRoutes() {

    @OpenApi(
        tags = ["Search"],
        path = "/api/maven/search",
        methods = [HttpMethod.GET],
        summary = "Search artifacts across all repositories the caller may browse",
        description = "One page of groups (GAVs), each with the repositories that hold it, the facets over the visible files and the state of the index. " +
            "A row is returned exactly when the caller may browse its path: PUBLIC repositories to everyone, HIDDEN and PRIVATE ones to callers with READ on the path.",
        queryParams = [
            OpenApiParam(name = "q", description = "Free text, at most 200 characters. Tokens are matched by prefix; a token of the form group:artifact:version matches coordinates.", required = false),
            OpenApiParam(name = "groupId", description = "Prefix of the groupId", required = false),
            OpenApiParam(name = "artifactId", description = "Prefix of the artifactId", required = false),
            OpenApiParam(name = "version", description = "Exact version, case-insensitive. Cannot be combined with versionPrefix.", required = false),
            OpenApiParam(name = "versionPrefix", description = "Prefix of the version", required = false),
            OpenApiParam(name = "repository", description = "Exact repository name, repeatable", required = false, allowEmptyValue = false),
            OpenApiParam(name = "extension", description = "Exact file extension, repeatable", required = false),
            OpenApiParam(name = "classifier", description = "Exact classifier, repeatable; - selects files without a classifier", required = false),
            OpenApiParam(name = "sha1", description = "40 hexadecimal characters of the SHA-1 of a file", required = false),
            OpenApiParam(name = "snapshot", description = "true, false or all (default all)", required = false),
            OpenApiParam(name = "origin", description = "hosted, mirror or all (default all)", required = false),
            OpenApiParam(name = "modifiedAfter", description = "ISO-8601 instant, inclusive", required = false),
            OpenApiParam(name = "modifiedBefore", description = "ISO-8601 instant, exclusive", required = false),
            OpenApiParam(name = "sizeMin", description = "Minimum size in bytes, inclusive", required = false),
            OpenApiParam(name = "sizeMax", description = "Maximum size in bytes, inclusive", required = false),
            OpenApiParam(name = "sort", description = "groupId, artifactId, version, modified or size (default groupId)", required = false),
            OpenApiParam(name = "order", description = "asc or desc (default asc)", required = false),
            OpenApiParam(name = "page", description = "0-based page (default 0)", required = false),
            OpenApiParam(name = "size", description = "Groups per page, 1 to 100 (default 20)", required = false),
        ],
        responses = [
            OpenApiResponse(status = "200", content = [OpenApiContent(from = ArtifactSearchResponse::class)]),
            OpenApiResponse(status = "400", description = "Invalid parameter or result window exceeded. The message names the parameter."),
            OpenApiResponse(status = "404", description = "Search is turned off on this instance"),
            OpenApiResponse(status = "503", description = "Statement timeout or grant set too large for search"),
        ]
    )
    private val searchArtifacts = ReposiliteRoute<ArtifactSearchResponse>("/api/maven/search", GET) {
        whenEnabled { engine, _ ->
            accessed {
                val access = accessOf(this)
                response = guarded {
                    val query = ctx.artifactSearchQuery()
                    val page = engine.search(query, access)
                    Result.ok(
                        ArtifactSearchResponse(
                            items = page.items,
                            page = page.page,
                            size = page.size,
                            total = page.total,
                            totalCapped = page.totalCapped,
                            facets = engine.facets(query, access),
                            indexStatus = page.indexStatus,
                        )
                    )
                }
            }
        }
    }

    @OpenApi(
        tags = ["Search"],
        path = "/api/maven/search/files",
        methods = [HttpMethod.GET],
        summary = "List the files of one artifact version found by search",
        description = "The stored files of one GAV, ordered by path. The coordinates are required and compared case-insensitively. " +
            "The access rules of the search apply: files the caller may not browse are not listed.",
        queryParams = [
            OpenApiParam(name = "groupId", description = "GroupId of the artifact", required = true),
            OpenApiParam(name = "artifactId", description = "ArtifactId of the artifact", required = true),
            OpenApiParam(name = "version", description = "Version of the artifact", required = true),
            OpenApiParam(name = "origin", description = "hosted, mirror or all (default all)", required = false),
            OpenApiParam(name = "repository", description = "Exact repository name, repeatable", required = false),
            OpenApiParam(name = "page", description = "0-based page (default 0)", required = false),
            OpenApiParam(name = "size", description = "Files per page, 1 to 200 (default 50)", required = false),
        ],
        responses = [
            OpenApiResponse(status = "200", content = [OpenApiContent(from = ArtifactFilesResponse::class)]),
            OpenApiResponse(status = "400", description = "A coordinate is missing or a parameter is invalid. The message names it."),
            OpenApiResponse(status = "404", description = "Search is turned off on this instance"),
            OpenApiResponse(status = "503", description = "Statement timeout or grant set too large for search"),
        ]
    )
    private val searchFiles = ReposiliteRoute<ArtifactFilesResponse>("/api/maven/search/files", GET) {
        whenEnabled { engine, _ ->
            accessed {
                val access = accessOf(this)
                response = guarded {
                    val page = engine.files(ctx.filesQuery(), access)
                    Result.ok(ArtifactFilesResponse(items = page.items, page = page.page, size = page.size, total = page.total))
                }
            }
        }
    }

    @OpenApi(
        tags = ["Search"],
        path = "/api/maven/search/index",
        methods = [HttpMethod.GET],
        summary = "Get the state of the search index",
        description = "The state, lag and last full rebuild, the row and outbox counts, the repositories, the active run, the last 20 runs and the last verify report. Requires the maintenance area.",
        responses = [
            OpenApiResponse(status = "200", content = [OpenApiContent(from = SearchIndexResponse::class)]),
            OpenApiResponse(status = "403", description = "Only principals with the maintenance area (ingot.admin.maintenance) can access this endpoint"),
            OpenApiResponse(status = "404", description = "Search is turned off on this instance"),
        ]
    )
    private val indexStatus = ReposiliteRoute<SearchIndexResponse>("/api/maven/search/index", GET) {
        whenEnabled { engine, admin ->
            managerOnly("maintenance") {
                response = guarded { Result.ok(indexResponse(engine, admin)) }
            }
        }
    }

    @OpenApi(
        tags = ["Search"],
        path = "/api/maven/search/index/rebuild",
        methods = [HttpMethod.POST],
        summary = "Start a rebuild of the index from storage",
        description = "Reads the stored files of the given repositories again and corrects the index. Without a body, or with an empty object, every repository is rebuilt. " +
            "Requires the maintenance area.",
        requestBody = OpenApiRequestBody(content = [OpenApiContent(from = RebuildRequest::class)], required = false),
        responses = [
            OpenApiResponse(status = "202", content = [OpenApiContent(from = StartedRunResponse::class)]),
            OpenApiResponse(status = "400", description = "A repository is unknown or the body is not an object of that shape"),
            OpenApiResponse(status = "403", description = "Only principals with the maintenance area (ingot.admin.maintenance) can access this endpoint"),
            OpenApiResponse(status = "404", description = "Search is turned off on this instance"),
            OpenApiResponse(status = "409", description = "A rebuild or verify run is already running"),
        ]
    )
    private val startRebuild = ReposiliteRoute<StartedRunResponse>("/api/maven/search/index/rebuild", POST) {
        whenEnabled { _, admin ->
            managerOnly("maintenance") {
                response = guarded {
                    when (val started = admin.startRebuild(ctx.rebuildRepositories())) {
                        is RunStart.Started -> {
                            ctx.status(ACCEPTED)
                            Result.ok(StartedRunResponse(started.runId))
                        }
                        RunStart.Busy -> Result.error(CONFLICT.toErrorResponse(BUSY))
                    }
                }
            }
        }
    }

    @OpenApi(
        tags = ["Search"],
        path = "/api/maven/search/index/verify",
        methods = [HttpMethod.POST],
        summary = "Start a verify run",
        description = "Compares a sample of index rows with storage and repairs what differs. Requires the maintenance area.",
        responses = [
            OpenApiResponse(status = "202", content = [OpenApiContent(from = StartedRunResponse::class)]),
            OpenApiResponse(status = "403", description = "Only principals with the maintenance area (ingot.admin.maintenance) can access this endpoint"),
            OpenApiResponse(status = "404", description = "Search is turned off on this instance"),
            OpenApiResponse(status = "409", description = "A rebuild or verify run is already running"),
        ]
    )
    private val startVerify = ReposiliteRoute<StartedRunResponse>("/api/maven/search/index/verify", POST) {
        whenEnabled { _, admin ->
            managerOnly("maintenance") {
                response = guarded {
                    when (val started = admin.startVerify()) {
                        is RunStart.Started -> {
                            ctx.status(ACCEPTED)
                            Result.ok(StartedRunResponse(started.runId))
                        }
                        RunStart.Busy -> Result.error(CONFLICT.toErrorResponse(BUSY))
                    }
                }
            }
        }
    }

    @OpenApi(
        tags = ["Search"],
        path = "/api/maven/search/index/runs/{id}/cancel",
        methods = [HttpMethod.POST],
        summary = "Cancel a live index run",
        description = "The instance that runs the job stops after the batch in flight. A run that has finished cannot be cancelled. Requires the maintenance area.",
        pathParams = [OpenApiParam(name = "id", description = "Id of the run", required = true)],
        responses = [
            OpenApiResponse(status = "204", description = "The run was cancelled"),
            OpenApiResponse(status = "403", description = "Only principals with the maintenance area (ingot.admin.maintenance) can access this endpoint"),
            OpenApiResponse(status = "404", description = "Search is turned off on this instance, or the run does not exist"),
            OpenApiResponse(status = "409", description = "The run has already finished"),
        ]
    )
    private val cancelRun = ReposiliteRoute<Unit>("/api/maven/search/index/runs/{id}/cancel", POST) {
        whenEnabled { _, admin ->
            managerOnly("maintenance") {
                response = guarded {
                    val runId = parameter("id")?.toLongOrNull()
                    when (runId?.let(admin::cancel) ?: CancelOutcome.NOT_FOUND) {
                        CancelOutcome.CANCELLED -> {
                            ctx.status(NO_CONTENT)
                            Result.ok(Unit)
                        }
                        CancelOutcome.FINISHED -> Result.error(CONFLICT.toErrorResponse("The run has already finished"))
                        CancelOutcome.NOT_FOUND -> Result.error(NOT_FOUND.toErrorResponse("The run does not exist"))
                    }
                }
            }
        }
    }

    override val routes = routes(searchArtifacts, searchFiles, indexStatus, startRebuild, startVerify, cancelRun)

    /** Runs [block] when search is enabled; otherwise answers 404 without looking at the caller. */
    private fun <R> ContextDsl<R>.whenEnabled(block: (ArtifactSearchEngine, SearchIndexAdmin) -> Unit) {
        val engine = search.engine
        val admin = search.admin
        if (engine == null || admin == null) {
            response = NOT_FOUND.toErrorResponse(DISABLED).asError()
        } else {
            block(engine, admin)
        }
    }

    /** Turns the refusals of the engine and of the grant compiler into their statuses; everything else is a success. */
    private fun <T> ContextDsl<*>.guarded(block: () -> Result<T, ErrorResponse>): Result<T, ErrorResponse> = try {
        block()
    } catch (refused: InvalidSearchQueryException) {
        Result.error(BAD_REQUEST.toErrorResponse(refused.message ?: "Invalid search query"))
    } catch (unavailable: SearchUnavailableException) {
        ctx.header(RETRY_AFTER, RETRY_AFTER_SECONDS)
        Result.error(SERVICE_UNAVAILABLE.toErrorResponse(unavailable.message ?: "The search timed out, narrow the filters"))
    } catch (tooLarge: SearchGrantSetTooLargeException) {
        Result.error(SERVICE_UNAVAILABLE.toErrorResponse(GRANT_SET_TOO_LARGE))
    }

    /** The rows the subject may see: anonymous without a token, and the token's own rights otherwise. */
    private fun accessOf(token: AccessTokenDto?): SearchAccess {
        val subject = token?.let { accessTokens.subjectOf(it.identifier) }
        val visibilities: Map<String, RepositoryVisibility> = maven.getRepositories().associate { it.name to it.visibility }
        return SearchAccessCompiler.compile(subject, visibilities)
    }

    private fun indexResponse(engine: ArtifactSearchEngine, admin: SearchIndexAdmin): SearchIndexResponse {
        val status = engine.status()
        val runs = search.indexRuns()
        val details = admin.details()
        return SearchIndexResponse(
            state = status.state,
            lagSeconds = status.lagSeconds,
            lastFullRebuild = status.lastFullRebuild,
            rows = details.rows,
            outbox = OutboxCounts(
                pending = details.outboxPending,
                deadLetters = status.deadLetters,
                droppedEnqueues = status.droppedEnqueues,
            ),
            repositories = details.repositories,
            engine = engine.id,
            activeRun = runs.activeRun?.let(::runResponse),
            runs = runs.runs.map(::runResponse),
            lastVerify = details.lastVerify,
        )
    }

    /** The repositories of a rebuild request: null for all of them, otherwise known names. A malformed body is a 400. */
    private fun Context.rebuildRepositories(): List<String>? {
        val raw = body()
        if (raw.isBlank()) return null
        val request = try {
            ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER.readValue(raw, RebuildRequest::class.java)
        } catch (malformed: JsonProcessingException) {
            throw InvalidSearchQueryException("body", "The body must be an object such as {\"repositories\": [\"releases\"]}")
        }
        val names = request.repositories ?: return null
        if (names.isEmpty()) {
            throw InvalidSearchQueryException("repositories", "Name at least one repository, or leave the list out to rebuild all of them")
        }
        val configured = maven.getRepositories().map { it.name }.toSet()
        names.firstOrNull { it !in configured }?.let { throw InvalidSearchQueryException("repositories", "Unknown repository $it") }
        return names
    }

    private companion object {
        const val DISABLED = "Artifact search is turned off on this instance"
        const val BUSY = "A rebuild or verify run is already running. Wait for it to finish or cancel it."
        const val GRANT_SET_TOO_LARGE = "grant set too large for search"
        const val RETRY_AFTER = "Retry-After"
        const val RETRY_AFTER_SECONDS = "5"
    }

}

/** The query parameters of [ArtifactSearchQuery]. A value that does not parse is a 400 naming the parameter. */
private fun Context.artifactSearchQuery(): ArtifactSearchQuery = ArtifactSearchQuery(
    q = param("q"),
    groupId = param("groupId"),
    artifactId = param("artifactId"),
    version = param("version"),
    versionPrefix = param("versionPrefix"),
    repositories = paramList("repository"),
    extensions = paramList("extension"),
    classifiers = paramList("classifier"),
    sha1 = param("sha1"),
    snapshot = parsed("snapshot") { SNAPSHOT_VALUES[it.lowercase()] } ?: SnapshotFilter.ALL,
    origin = parsed("origin") { ORIGIN_VALUES[it.lowercase()] } ?: OriginFilter.ALL,
    modifiedAfter = parsed("modifiedAfter") { parseInstant(it) },
    modifiedBefore = parsed("modifiedBefore") { parseInstant(it) },
    sizeMin = parsed("sizeMin") { it.toLongOrNull() },
    sizeMax = parsed("sizeMax") { it.toLongOrNull() },
    sort = parsed("sort") { SORT_VALUES[it.lowercase()] } ?: SearchSort.GROUP_ID,
    order = parsed("order") { ORDER_VALUES[it.lowercase()] } ?: SearchOrder.ASC,
    page = parsed("page") { it.toIntOrNull() } ?: 0,
    size = parsed("size") { it.toIntOrNull() } ?: DEFAULT_SEARCH_SIZE,
)

private fun Context.filesQuery(): ArtifactFilesQuery = ArtifactFilesQuery(
    groupId = param("groupId").orEmpty(),
    artifactId = param("artifactId").orEmpty(),
    version = param("version").orEmpty(),
    origin = parsed("origin") { ORIGIN_VALUES[it.lowercase()] } ?: OriginFilter.ALL,
    repositories = paramList("repository"),
    page = parsed("page") { it.toIntOrNull() } ?: 0,
    size = parsed("size") { it.toIntOrNull() } ?: DEFAULT_FILES_SIZE,
)

private fun Context.param(name: String): String? = queryParam(name)?.takeIf { it.isNotBlank() }

private fun Context.paramList(name: String): List<String> = queryParams(name).filter { it.isNotBlank() }

/** The value of a parameter read by [parse], null when it is absent, and a 400 naming it when it does not parse. */
private fun <T> Context.parsed(name: String, parse: (String) -> T?): T? {
    val raw = param(name) ?: return null
    return parse(raw) ?: throw InvalidSearchQueryException(name, "$name has no valid value: $raw")
}

private fun parseInstant(raw: String): Instant? = runCatching { Instant.parse(raw) }.getOrNull()

private val SNAPSHOT_VALUES = mapOf("true" to SnapshotFilter.TRUE, "false" to SnapshotFilter.FALSE, "all" to SnapshotFilter.ALL)
private val ORIGIN_VALUES = mapOf("hosted" to OriginFilter.HOSTED, "mirror" to OriginFilter.MIRROR, "all" to OriginFilter.ALL)
private val SORT_VALUES = mapOf(
    "groupid" to SearchSort.GROUP_ID,
    "artifactid" to SearchSort.ARTIFACT_ID,
    "version" to SearchSort.VERSION,
    "modified" to SearchSort.MODIFIED,
    "size" to SearchSort.SIZE,
)
private val ORDER_VALUES = mapOf("asc" to SearchOrder.ASC, "desc" to SearchOrder.DESC)

private const val DEFAULT_SEARCH_SIZE = 20
private const val DEFAULT_FILES_SIZE = 50

private fun runResponse(run: RunSummary) = SearchRunResponse(
    id = run.id,
    kind = run.kind,
    trigger = run.trigger,
    status = run.status,
    startedAt = run.startedAt,
    finishedAt = run.finishedAt,
    leaseOwner = run.leaseOwner,
    leaseUntil = run.leaseUntil,
    repositories = run.repositories,
    cursorRepository = run.cursorRepository,
    cursorPath = run.cursorPath,
    counters = run.counters,
    lastError = run.lastError,
)

/** A group search page: the groups with their repositories and the index status, and the facets over the visible files. */
internal data class ArtifactSearchResponse(
    val items: List<ArtifactSearchItem>,
    val page: Int,
    val size: Int,
    val total: Long,
    val totalCapped: Boolean,
    val facets: SearchFacets,
    val indexStatus: IndexStatus,
)

internal data class ArtifactFilesResponse(
    val items: List<ArtifactFileItem>,
    val page: Int,
    val size: Int,
    val total: Long,
)

internal data class SearchIndexResponse(
    val state: IndexState,
    val lagSeconds: Long,
    val lastFullRebuild: Instant?,
    val rows: Long,
    val outbox: OutboxCounts,
    val repositories: List<String>,
    val engine: String,
    val activeRun: SearchRunResponse?,
    val runs: List<SearchRunResponse>,
    val lastVerify: LastVerifyReport?,
)

internal data class OutboxCounts(
    val pending: Long,
    val deadLetters: Long,
    val droppedEnqueues: Long,
)

internal data class SearchRunResponse(
    val id: Long,
    val kind: RunKind,
    val trigger: RunTrigger,
    val status: RunStatus,
    val startedAt: Instant,
    val finishedAt: Instant?,
    val leaseOwner: String?,
    val leaseUntil: Instant?,
    val repositories: List<String>?,
    val cursorRepository: String?,
    val cursorPath: String?,
    val counters: RunCounters,
    val lastError: String?,
)

internal data class StartedRunResponse(val runId: Long)

/** The body of a rebuild request. Leaving out [repositories] rebuilds every repository. */
internal data class RebuildRequest(val repositories: List<String>? = null)
