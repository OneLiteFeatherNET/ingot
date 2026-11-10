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
package net.onelitefeather.ingot.nexusimport.jobs

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.reposilite.shared.ErrorResponse
import com.reposilite.shared.toErrorResponse
import io.javalin.http.HttpStatus.BAD_GATEWAY
import io.javalin.http.HttpStatus.BAD_REQUEST
import io.javalin.http.HttpStatus.CONFLICT
import io.javalin.http.HttpStatus.FORBIDDEN
import io.javalin.http.HttpStatus.NOT_FOUND
import io.javalin.http.HttpStatus.UNAUTHORIZED
import net.onelitefeather.ingot.nexusimport.AuditMode
import net.onelitefeather.ingot.nexusimport.ConnectionRequest
import net.onelitefeather.ingot.nexusimport.NexusImportSettings
import net.onelitefeather.ingot.nexusimport.copy.ConflictPolicy
import net.onelitefeather.ingot.nexusimport.definitions.DefinitionWriter
import net.onelitefeather.ingot.nexusimport.definitions.WriteResult
import net.onelitefeather.ingot.nexusimport.nexus.NexusClient
import net.onelitefeather.ingot.nexusimport.nexus.NexusConnection
import net.onelitefeather.ingot.nexusimport.nexus.NexusHttpException
import net.onelitefeather.ingot.nexusimport.nexus.NexusRefusedException
import net.onelitefeather.ingot.nexusimport.nexus.NexusTransport
import net.onelitefeather.ingot.nexusimport.nexus.NexusUnavailableException
import net.onelitefeather.ingot.nexusimport.nexus.NexusUrlPolicy
import net.onelitefeather.ingot.nexusimport.nexus.Secret
import net.onelitefeather.ingot.nexusimport.plan.ImportPlan
import net.onelitefeather.ingot.nexusimport.plan.ImportPlanner
import net.onelitefeather.ingot.nexusimport.plan.IngotType
import net.onelitefeather.ingot.nexusimport.plan.LossRow
import net.onelitefeather.ingot.nexusimport.plan.LossStatus
import net.onelitefeather.ingot.nexusimport.plan.NexusDiscovery
import panda.std.Result
import panda.std.asError
import java.io.IOException
import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** The body of a job request. [toString] hides the password, so a logged request cannot leak it. */
data class JobRequest(
    val kind: String = "",
    val url: String = "",
    val user: String = "",
    /** A literal password, or a reference such as `${env:NAME}` when secret references are switched on. */
    val password: String = "",
    /** Nexus names to import or verify; null means every repository for an import. Verify requires it. */
    val repositories: List<String>? = null,
    /** Ingot names by Nexus name, for repositories the operator renames. */
    val targets: Map<String, String> = emptyMap(),
    val conflictPolicy: String = "refuse",
    val audit: String? = null,
    /** A verify job may name the import job whose conflicts it must respect. */
    val verifyImportJob: Long? = null,
) {
    override fun toString(): String =
        "JobRequest(kind=$kind, url=$url, user=$user, password=********, repositories=$repositories, targets=$targets, " +
            "conflictPolicy=$conflictPolicy, audit=$audit, verifyImportJob=$verifyImportJob)"
}

data class JobCreated(val id: Long, val kind: String, val status: JobStatus)

/**
 * The plan a job stored: the transfer size, the formats, the blockers and the Ingot name chosen for each Nexus repository.
 * The names are kept so that every later step of the job plans the same targets. Only names and numbers, never a credential.
 */
data class DryRunSummary(
    val transferBytes: Long,
    val formats: Map<String, Int>,
    val blockers: List<String>,
    val plannedTargets: Map<String, String> = emptyMap(),
)

data class JobView(
    val id: Long,
    val kind: String,
    val status: JobStatus,
    val pauseReason: PauseReason?,
    val owner: String?,
    val lastError: String?,
    val repositories: List<RepositoryRow>,
    val results: Map<AssetResult, Int>,
    val summary: DryRunSummary?,
)

data class ReportView(val rows: List<LossRow>, val summary: DryRunSummary?)

data class ConflictView(val repository: String, val path: String, val detail: String?)

data class DiscoveredRepository(
    val name: String,
    val format: String,
    val type: String,
    val supported: Boolean,
    val components: Long,
    val bytes: Long,
    val settingsReadable: Boolean,
)

data class FormatTotals(val repositories: Int, val components: Long, val bytes: Long)

data class DiscoverReport(val repositories: List<DiscoveredRepository>, val formats: Map<String, FormatTotals>)

/**
 * The import jobs: creating them, the operator's control, the reports, and running one step of a job for this replica. A run
 * takes the job under a lease, plans it, writes its definitions, copies its files or verifies them, and settles the rows.
 * The routes and the scheduler call into this class; nothing else writes the job tables.
 */
internal class NexusImportService(
    private val settings: NexusImportSettings,
    val store: JobStore,
    private val clock: Clock,
    private val transport: NexusTransport,
    private val sleeper: (Duration) -> Unit,
    private val ports: ImportPorts,
    private val readOnly: () -> Boolean,
    /** Resolves a password reference to its value, or throws. Null when secret references are switched off. */
    private val resolveReference: ((String) -> String)?,
    private val tempDir: Path,
    private val newToken: () -> String = { UUID.randomUUID().toString() },
) {

    private val json = ObjectMapper()

    /** Literal passwords of jobs this replica holds. They live only here and are wiped when the job ends or pauses. */
    private val passwords = ConcurrentHashMap<Long, Secret>()

    // ---- discovery -------------------------------------------------------------------------------------------------------

    fun discover(request: ConnectionRequest): Result<DiscoverReport, ErrorResponse> {
        val connection = connection(request.url, request.user, request.password).valueOrReturn { return Result.error(it) }
        return try {
            val facts = NexusDiscovery(NexusClient(connection, transport, clock, sleeper, settings.maxRequestsPerSecond)).discover()
            val repositories = facts.map {
                DiscoveredRepository(it.name, it.format, it.type, it.format == "maven2", it.components, it.bytes, it.settingsReadable)
            }
            val formats = repositories.groupBy { it.format }.mapValues { (_, rows) ->
                FormatTotals(rows.size, rows.sumOf { it.components }, rows.sumOf { it.bytes })
            }
            Result.ok(DiscoverReport(repositories, formats))
        } catch (exception: NexusHttpException) {
            Result.error(nexusFailure(exception))
        } catch (exception: NexusUnavailableException) {
            Result.error(BAD_GATEWAY.toErrorResponse(exception.message ?: "Nexus did not answer"))
        } catch (exception: NexusRefusedException) {
            Result.error(BAD_REQUEST.toErrorResponse(exception.message ?: "Nexus refused the request"))
        }
    }

    // ---- jobs ------------------------------------------------------------------------------------------------------------

    fun create(request: JobRequest): Result<JobCreated, ErrorResponse> {
        val stored = validate(request).valueOrReturn { return Result.error(it) }
        val id = store.createJob(stored.kind, stored.toJson())
        if (stored.credential == CREDENTIAL_LITERAL) passwords[id] = Secret(request.password)
        return Result.ok(JobCreated(id, stored.kind, JobStatus.QUEUED))
    }

    fun jobs(): List<JobView> = store.jobs().map(::view)

    fun job(id: Long): Result<JobView, ErrorResponse> =
        store.job(id)?.let { Result.ok(view(it)) } ?: notFound(id)

    fun pause(id: Long): Result<Unit, ErrorResponse> {
        if (store.job(id) == null) return notFound(id)
        return if (store.requestPause(id)) Result.ok(Unit) else conflict("The job cannot be paused in its current state")
    }

    /** Resumes a job the operator paused, or one that waits for read-only mode to end. Credentials are supplied with [supplyCredentials]. */
    fun resume(id: Long): Result<Unit, ErrorResponse> {
        if (store.job(id) == null) return notFound(id)
        val resumed = store.resume(id, PauseReason.OPERATOR) || store.resume(id, PauseReason.READ_ONLY)
        return if (resumed) Result.ok(Unit) else conflict("The job is not paused")
    }

    fun cancel(id: Long): Result<Unit, ErrorResponse> {
        if (store.job(id) == null) return notFound(id)
        if (!store.requestCancel(id)) return conflict("The job has already ended")
        if (store.job(id)?.status == JobStatus.CANCELLED) passwords.remove(id)?.wipe()
        return Result.ok(Unit)
    }

    /**
     * Supplies the password again for a job paused with [PauseReason.CREDENTIALS_NEEDED]. The job is queued on this replica,
     * which holds the password from now on, and runs at its next tick. A reference job reads its reference at each run, so
     * it takes no password here.
     */
    fun supplyCredentials(id: Long, password: String): Result<Unit, ErrorResponse> {
        val job = store.job(id) ?: return notFound(id)
        if (job.status != JobStatus.PAUSED || job.pauseReason != PauseReason.CREDENTIALS_NEEDED) {
            return conflict("The job does not wait for credentials")
        }
        val stored = parameters(job)
        if (stored.credential == CREDENTIAL_REFERENCE) {
            if (password.isNotEmpty() && !password.startsWith("\${")) {
                return BAD_REQUEST.toErrorResponse("A reference job reads its password from the reference; correct the value it names").asError()
            }
        } else {
            if (password.isEmpty()) return BAD_REQUEST.toErrorResponse("A password is required").asError()
            if (password.startsWith("\${")) return BAD_REQUEST.toErrorResponse("Enter the literal password, not a reference, for this job").asError()
            passwords[id] = Secret(password)
        }
        return if (store.resume(id, PauseReason.CREDENTIALS_NEEDED)) Result.ok(Unit) else conflict("The job does not wait for credentials")
    }

    fun report(id: Long): Result<ReportView, ErrorResponse> {
        val job = store.job(id) ?: return notFound(id)
        return Result.ok(ReportView(store.report(id), job.summary?.let(::summary)))
    }

    fun conflicts(id: Long): Result<List<ConflictView>, ErrorResponse> {
        if (store.job(id) == null) return notFound(id)
        return Result.ok(store.conflicts(id).map { ConflictView(it.repository, it.path, it.detail) })
    }

    fun delete(id: Long): Result<Unit, ErrorResponse> {
        if (store.job(id) == null) return notFound(id)
        return if (store.deleteJob(id)) Result.ok(Unit) else conflict("Only a finished job can be deleted")
    }

    /** Whether this replica holds the literal password of [id]. A replica without it must not run the job. */
    fun holdsPassword(id: Long): Boolean = passwords.containsKey(id)

    /** Whether the job authenticates with a literal password, which only the replica that received it holds. */
    fun needsLocalPassword(job: JobRow): Boolean = parameters(job).credential == CREDENTIAL_LITERAL

    // ---- run -------------------------------------------------------------------------------------------------------------

    /** Runs one step of job [id] for this replica, or reports why it did not run. Never throws for a job's own failure. */
    fun run(id: Long): RunOutcome {
        val job = store.job(id) ?: return RunOutcome.NOT_RUNNABLE
        if (job.status != JobStatus.QUEUED && job.status != JobStatus.RUNNING) return RunOutcome.NOT_RUNNABLE
        if (readOnly()) return RunOutcome.NOT_STARTED_READ_ONLY
        val stored = parameters(job)
        if (stored.credential == CREDENTIAL_LITERAL && !holdsPassword(id)) {
            // The lease of a job that a lost replica held has run out: the password is gone with that replica.
            if (job.status == JobStatus.RUNNING) store.markCredentialsNeeded(id)
            return RunOutcome.NO_PASSWORD_HERE
        }

        val token = newToken()
        if (!store.acquire(id, token)) return RunOutcome.NOT_ACQUIRED
        val session = "migration@$id"
        val bulk = stored.audit == AuditMode.SUMMARY.wire()
        if (bulk) ports.bulkSessionStarted(session)
        return try {
            val password = passwordFor(id, stored) ?: return failRun(id, token, "The password reference cannot be resolved")
            val client = NexusClient(NexusConnection(java.net.URI.create(stored.url), stored.user, password), transport, clock, sleeper, settings.maxRequestsPerSecond)
            val outcome = when (stored.kind) {
                KIND_DRY_RUN -> dryRun(id, token, stored, client)
                KIND_IMPORT -> importRun(id, token, stored, client)
                else -> verify(id, token, stored, client)
            }
            if (outcome == RunOutcome.PAUSED_CREDENTIALS) passwords.remove(id)?.wipe()
            finishedRun(id)
            outcome
        } catch (exception: NexusHttpException) {
            if (exception.status == 401 || exception.status == 403) {
                store.pause(id, token, PauseReason.CREDENTIALS_NEEDED)
                passwords.remove(id)?.wipe()
                RunOutcome.PAUSED_CREDENTIALS
            } else {
                failRun(id, token, "Nexus answered HTTP ${exception.status}")
            }
        } catch (exception: NexusUnavailableException) {
            failRun(id, token, exception.message ?: "Nexus did not answer")
        } catch (exception: NexusRefusedException) {
            failRun(id, token, exception.message ?: "Nexus refused a request")
        } catch (exception: IOException) {
            failRun(id, token, "Local I/O failed: ${exception.javaClass.simpleName}")
        } finally {
            if (bulk) ports.bulkSessionEnded(session)
        }
    }

    private fun dryRun(id: Long, token: String, stored: StoredParameters, client: NexusClient): RunOutcome {
        val plan = plan(stored, client)
        store.replaceReport(id, plan.rows)
        store.planRepositories(id, hostedRows(plan))
        store.commitSummary(id, token, summaryOf(plan).toJson())
        return if (store.finish(id, token, JobStatus.COMPLETED)) RunOutcome.COMPLETED else RunOutcome.LEASE_LOST
    }

    private fun importRun(id: Long, token: String, stored: StoredParameters, client: NexusClient): RunOutcome {
        // Discovery and definitions run on every start, because a takeover has no plan in memory. The names chosen by the
        // first plan are reused, so a later start cannot pick a different name for a repository this job created itself.
        val previous = store.job(id)?.summary?.let(::summary)
        val plan = plan(stored, client, (previous?.plannedTargets.orEmpty()) + stored.targets)
        store.commitSummary(id, token, summaryOf(plan).toJson())
        val definitions = DefinitionWriter(ports.domain(id)).write(plan.repositories)
        // The report describes the first plan. A later start would otherwise report the definitions it created as existing.
        if (previous == null) store.replaceReport(id, plan.rows + definitionRows(definitions, plan))
        store.planRepositories(id, hostedRows(plan))

        val hosted = plan.repositories.filter { it.type == IngotType.HOSTED }.map { CopyTarget(it.nexusName, it.targetName) }
        val policy = ConflictPolicy.valueOf(stored.conflictPolicy.uppercase())
        val worker = ImportWorker(store, clock, store.lease, client, ports.writer(id), tempDir, readOnly, token)
        val outcome = worker.copy(id, hosted, policy)
        if (outcome == RunOutcome.COMPLETED) {
            settle(id, hosted)
        }
        return outcome
    }

    private fun verify(id: Long, token: String, stored: StoredParameters, client: NexusClient): RunOutcome {
        val pairs = stored.repositories.orEmpty().map { CopyTarget(it, stored.targets[it] ?: it) }
        store.planRepositories(id, pairs.map { RepositoryRow(it.nexusName, it.targetName, TYPE_HOSTED, RepositoryStatus.PLANNED) })
        val importedConflicts = stored.verifyImportJob?.let { importId ->
            store.repositories(importId).filter { it.status == RepositoryStatus.CONFLICTED }.map { it.targetName }
        }.orEmpty().toSet()
        val statuses = Verifier(client, ports.writer(id)).verify(pairs, importedConflicts)
        for (pair in pairs) {
            val status = when (statuses.getValue(pair.targetName)) {
                VerifyStatus.VERIFIED -> RepositoryStatus.VERIFIED
                VerifyStatus.MISMATCH -> RepositoryStatus.MISMATCH
                VerifyStatus.CONFLICTED -> RepositoryStatus.CONFLICTED
            }
            if (!store.setRepositoryStatus(id, token, pair.nexusName, status)) return RunOutcome.LEASE_LOST
        }
        return if (store.finish(id, token, JobStatus.COMPLETED)) RunOutcome.COMPLETED else RunOutcome.LEASE_LOST
    }

    /** The final state of each copied repository: conflicted, failed or copied. A verify pass then decides verified. */
    private fun settle(id: Long, hosted: List<CopyTarget>) {
        val assets = store.assets(id)
        for (copyTarget in hosted) {
            val results = assets.filter { it.repository == copyTarget.targetName }.map { it.result }
            val status = when {
                AssetResult.CONFLICT in results -> RepositoryStatus.CONFLICTED
                results.any { it == AssetResult.FAILED || it == AssetResult.CHECKSUM_FAILED } -> RepositoryStatus.FAILED
                else -> RepositoryStatus.COPIED
            }
            store.settleRepository(id, copyTarget.nexusName, status)
        }
    }

    private fun plan(stored: StoredParameters, client: NexusClient, targets: Map<String, String> = stored.targets): ImportPlan {
        val facts = NexusDiscovery(client).discover()
        return ImportPlanner(ports.existingTargets()).plan(facts, stored.repositories?.toSet(), targets)
    }

    private fun definitionRows(result: WriteResult, plan: ImportPlan): List<LossRow> {
        val byTarget = plan.repositories.associateBy { it.targetName }
        val manual = result.manual.map { definition ->
            val source = byTarget[definition.target]?.nexusName ?: definition.target
            LossRow(
                "definition", source, definition.target, LossStatus.MANUAL,
                "The shared settings file owns the Maven settings, so the definition is not written",
                "The repository does not exist until it is added", "Add this definition to the Maven settings: ${definition.json}",
            )
        }
        val unwritten = result.unwritten.map { target ->
            LossRow(
                "definition", byTarget[target]?.nexusName ?: target, target, LossStatus.MANUAL,
                "The Maven settings kept changing while the definitions were written",
                "The repository does not exist until it is added", "Run the import again",
            )
        }
        val existing = result.skipped.map { target ->
            LossRow(
                "definition", byTarget[target]?.nexusName ?: target, target, LossStatus.PARTIAL,
                "The Ingot repository already exists and was not changed",
                "Its files are compared with Nexus, and differing ones are conflicts", "Check the existing repository",
            )
        }
        return manual + unwritten + existing
    }

    private fun hostedRows(plan: ImportPlan): List<RepositoryRow> =
        plan.repositories.filter { it.type == IngotType.HOSTED }
            .map { RepositoryRow(it.nexusName, it.targetName, TYPE_HOSTED, RepositoryStatus.PLANNED) }

    private fun summaryOf(plan: ImportPlan) = DryRunSummary(
        transferBytes = plan.transferBytes,
        formats = plan.formats,
        blockers = plan.blockers,
        plannedTargets = plan.repositories.associate { it.nexusName to it.targetName },
    )

    /** Wipes the literal password once the job has reached an end state. */
    private fun finishedRun(id: Long) {
        val job = store.job(id) ?: return
        if (job.status == JobStatus.COMPLETED || job.status == JobStatus.FAILED || job.status == JobStatus.CANCELLED) {
            passwords.remove(id)?.wipe()
            ports.runFinished(id, runDetails(job))
        }
    }

    private fun runDetails(job: JobRow): Map<String, Any> {
        val counts = store.assets(job.id).groupingBy { it.result.name }.eachCount()
        return buildMap {
            put("kind", job.kind)
            put("status", job.status.name)
            put("files", store.assets(job.id).size)
            AssetResult.entries.forEach { put(it.name.lowercase(), counts[it.name] ?: 0) }
        }
    }

    private fun failRun(id: Long, token: String, reason: String): RunOutcome {
        passwords.remove(id)?.wipe()
        return if (store.finish(id, token, JobStatus.FAILED, reason)) RunOutcome.FAILED else RunOutcome.LEASE_LOST
    }

    private fun passwordFor(id: Long, stored: StoredParameters): Secret? =
        when (stored.credential) {
            CREDENTIAL_LITERAL -> passwords[id]
            else -> {
                val resolver = resolveReference ?: return null
                try {
                    Secret(resolver(stored.reference.orEmpty()))
                } catch (exception: RuntimeException) {
                    null
                }
            }
        }

    // ---- validation and views --------------------------------------------------------------------------------------------

    private fun validate(request: JobRequest): Result<StoredParameters, ErrorResponse> {
        val kind = request.kind
        if (kind !in KINDS) return bad("The kind must be one of ${KINDS.joinToString()}")
        val connection = connection(request.url, request.user, request.password)
        if (!connection.isOk) return Result.error(connection.error)

        val credential = if (request.password.startsWith("\${")) CREDENTIAL_REFERENCE else CREDENTIAL_LITERAL
        if (credential == CREDENTIAL_REFERENCE && resolveReference == null) {
            return bad("The password is a reference, but secret references are switched off (INGOT_SETTINGS_SECRET_REFERENCES)")
        }
        val policy = request.conflictPolicy.lowercase()
        if (policy !in POLICIES) return bad("The conflict policy must be refuse, abort or overwrite")
        if (policy == "overwrite" && kind != KIND_IMPORT) return bad("Only an import may overwrite")
        val audit = request.audit?.lowercase() ?: settings.audit.wire()
        if (audit != "per-file" && audit != "summary") return bad("The audit mode must be per-file or summary")

        val names = request.repositories.orEmpty() + request.targets.keys + request.targets.values
        if (names.any { !NAME.matches(it) }) return bad("Repository names may use letters, digits, dots, dashes and underscores")
        if (kind == KIND_VERIFY && request.repositories.isNullOrEmpty()) return bad("A verify job names the repositories to verify")
        if (kind != KIND_VERIFY && request.verifyImportJob != null) return bad("Only a verify job takes an import job")

        return Result.ok(
            StoredParameters(
                kind = kind,
                url = connection.get().baseUri.toString(),
                user = request.user,
                credential = credential,
                reference = request.password.takeIf { credential == CREDENTIAL_REFERENCE },
                repositories = request.repositories,
                targets = request.targets,
                conflictPolicy = policy,
                audit = audit,
                verifyImportJob = request.verifyImportJob,
            ),
        )
    }

    private fun connection(url: String, user: String, password: String): Result<NexusConnection, ErrorResponse> {
        val outcome = NexusUrlPolicy(settings.allowedHosts).check(url)
        val uri = outcome.uri
        if (outcome.refusal != null || uri == null) return bad(outcome.refusal ?: "The Nexus URL is not valid")
        if (user.isBlank() || password.isEmpty()) return bad("A user name and a password are required")
        val resolved = if (password.startsWith("\${")) {
            val resolver = resolveReference ?: return bad("The password is a reference, but secret references are switched off (INGOT_SETTINGS_SECRET_REFERENCES)")
            try {
                resolver(password)
            } catch (exception: RuntimeException) {
                return bad("The password reference cannot be resolved")
            }
        } else {
            password
        }
        return Result.ok(NexusConnection(uri, user, Secret(resolved)))
    }

    private fun view(job: JobRow): JobView = JobView(
        id = job.id,
        kind = job.kind,
        status = job.status,
        pauseReason = job.pauseReason,
        owner = job.owner,
        lastError = job.lastError,
        repositories = store.repositories(job.id),
        results = store.assets(job.id).groupingBy { it.result }.eachCount(),
        summary = job.summary?.let(::summary),
    )

    private fun parameters(job: JobRow): StoredParameters = StoredParameters.parse(json.readTree(job.parameters))

    private fun summary(text: String): DryRunSummary {
        val node = json.readTree(text)
        return DryRunSummary(
            transferBytes = node.path("transferBytes").asLong(),
            formats = node.path("formats").fields().asSequence().associate { (name, value) -> name to value.asInt() },
            blockers = node.path("blockers").map { it.asText() },
            plannedTargets = node.path("plannedTargets").fields().asSequence().associate { (name, value) -> name to value.asText() },
        )
    }

    private fun DryRunSummary.toJson(): String = json.writeValueAsString(
        mapOf("transferBytes" to transferBytes, "formats" to formats, "blockers" to blockers, "plannedTargets" to plannedTargets),
    )

    private fun nexusFailure(exception: NexusHttpException): ErrorResponse = when (exception.status) {
        401 -> UNAUTHORIZED.toErrorResponse("Nexus refused the credential")
        403 -> FORBIDDEN.toErrorResponse("The credential may not read this area of Nexus")
        else -> BAD_GATEWAY.toErrorResponse("Nexus answered HTTP ${exception.status}")
    }

    private fun <T> notFound(id: Long): Result<T, ErrorResponse> = NOT_FOUND.toErrorResponse("No job $id").asError()

    private fun <T> conflict(message: String): Result<T, ErrorResponse> = CONFLICT.toErrorResponse(message).asError()

    private fun <T> bad(message: String): Result<T, ErrorResponse> = BAD_REQUEST.toErrorResponse(message).asError()

    /** The parameters of a job as stored. The literal password is never among them. */
    internal data class StoredParameters(
        val kind: String,
        val url: String,
        val user: String,
        val credential: String,
        val reference: String?,
        val repositories: List<String>?,
        val targets: Map<String, String>,
        val conflictPolicy: String,
        val audit: String,
        val verifyImportJob: Long?,
    ) {
        fun toJson(): String = MAPPER.writeValueAsString(
            linkedMapOf(
                "kind" to kind, "url" to url, "user" to user, "credential" to credential, "reference" to reference,
                "repositories" to repositories, "targets" to targets, "conflictPolicy" to conflictPolicy, "audit" to audit,
                "verifyImportJob" to verifyImportJob,
            ),
        )

        companion object {
            fun parse(node: JsonNode) = StoredParameters(
                kind = node.path("kind").asText(),
                url = node.path("url").asText(),
                user = node.path("user").asText(),
                credential = node.path("credential").asText(),
                reference = node.path("reference").takeUnless { it.isNull || it.isMissingNode }?.asText(),
                repositories = node.path("repositories").takeUnless { it.isNull || it.isMissingNode }?.map { it.asText() },
                targets = node.path("targets").fields().asSequence().associate { (name, value) -> name to value.asText() },
                conflictPolicy = node.path("conflictPolicy").asText("refuse"),
                audit = node.path("audit").asText("per-file"),
                verifyImportJob = node.path("verifyImportJob").takeUnless { it.isNull || it.isMissingNode }?.asLong(),
            )
        }
    }

    private companion object {
        const val KIND_DRY_RUN = "DRY_RUN"
        const val KIND_IMPORT = "IMPORT"
        const val KIND_VERIFY = "VERIFY"
        val KINDS = setOf(KIND_DRY_RUN, KIND_IMPORT, KIND_VERIFY)
        val POLICIES = setOf("refuse", "abort", "overwrite")
        const val CREDENTIAL_LITERAL = "literal"
        const val CREDENTIAL_REFERENCE = "reference"
        const val TYPE_HOSTED = "hosted"
        val NAME = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,99}")
        val MAPPER = ObjectMapper()
    }
}

private fun AuditMode.wire(): String = if (this == AuditMode.SUMMARY) "summary" else "per-file"

private inline fun <T, E> Result<T, E>.valueOrReturn(onError: (E) -> Nothing): T =
    if (isOk) get() else onError(error)
