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

import com.fasterxml.jackson.databind.ObjectMapper
import com.reposilite.maven.RepositoryType
import com.reposilite.maven.RepositoryVisibility
import com.reposilite.maven.application.RepositorySettings
import net.onelitefeather.ingot.nexusimport.AuditMode
import net.onelitefeather.ingot.nexusimport.NexusImportSettings
import net.onelitefeather.ingot.nexusimport.copy.MemoryTarget
import net.onelitefeather.ingot.nexusimport.copy.TargetWriter
import net.onelitefeather.ingot.nexusimport.definitions.FakeMavenDomain
import net.onelitefeather.ingot.nexusimport.definitions.MavenDomainPort
import net.onelitefeather.ingot.nexusimport.fake.AdvancingClock
import net.onelitefeather.ingot.nexusimport.fake.FakeNexus
import net.onelitefeather.ingot.nexusimport.fake.FakeReply
import net.onelitefeather.ingot.nexusimport.nexus.JdkNexusTransport
import net.onelitefeather.ingot.nexusimport.nexus.NexusClient
import org.jetbrains.exposed.v1.jdbc.Database
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Duration

/**
 * One import against the fake Nexus and in-memory Ingot ports: a Nexus hosted repository `releases` with three files, a
 * database file, a fake clock and a fake `maven` domain. Every test builds its own scenario in its own temporary directory.
 */
internal class ImportScenario(private val root: Path, initialDomain: List<RepositorySettings> = emptyList()) : AutoCloseable {

    val nexus = FakeNexus()
    val clock = AdvancingClock()
    val store = JobStore(Database.connect("jdbc:sqlite:${root.resolve("jobs.db")}", driver = "org.sqlite.JDBC"), clock, lease = LEASE)
    val target = MemoryTarget()
    val domain = FakeMavenDomain(initial = initialDomain)
    val ports = RecordingPorts(target, domain)
    var readOnly = false
    val references = mutableMapOf<String, String>()
    private val json = ObjectMapper()
    private var tokens = 0

    val files = linkedMapOf(
        "com/example/app/1.0/app-1.0.jar" to "jar-bytes".toByteArray(),
        "com/example/app/1.0/app-1.0.pom" to "pom-bytes".toByteArray(),
        "com/example/app/maven-metadata.xml" to "meta-bytes".toByteArray(),
    )

    init {
        nexus.serveFixture(NexusClient.REPOSITORIES_PATH, "repositories.json")
        nexus.serveFixture("${NexusClient.REPOSITORIES_PATH}/maven/hosted/releases", "maven-hosted-releases.json")
        nexus.serveFixture("${NexusClient.REPOSITORIES_PATH}/maven/proxy/snapshots-proxy", "maven-proxy.json")
        nexus.serveFixture("${NexusClient.REPOSITORIES_PATH}/maven/group/public-group", "maven-group.json")
        nexus.on(NexusClient.ASSETS_PATH) { request ->
            val items = if (request.query["repository"] == "releases") files.map { (path, bytes) -> assetItem(path, bytes) } else emptyList()
            FakeReply.json(json.writeValueAsString(mapOf("items" to items, "continuationToken" to null)))
        }
        files.forEach { (path, bytes) -> nexus.on("/repository/releases/$path") { FakeReply.bytes(bytes) } }
    }

    private fun assetItem(path: String, bytes: ByteArray) = mapOf(
        "path" to path,
        "downloadUrl" to "${nexus.baseUri}/repository/releases/$path",
        "fileSize" to bytes.size,
        "checksum" to mapOf("sha1" to digest("SHA-1", bytes), "md5" to digest("MD5", bytes)),
    )

    fun service(token: String? = null): NexusImportService = NexusImportService(
        settings = NexusImportSettings(concurrency = 1, maxRequestsPerSecond = 100, audit = AuditMode.PER_FILE, allowedHosts = null),
        store = store,
        clock = clock,
        transport = JdkNexusTransport(),
        sleeper = { clock.advance(it) },
        ports = ports,
        readOnly = { readOnly },
        resolveReference = { reference -> references[reference] ?: error("unknown reference") },
        tempDir = root,
        newToken = { token ?: "worker-${++tokens}" },
    )

    fun scheduler(service: NexusImportService): JobScheduler = JobScheduler(service, store, clock, readOnly = { readOnly })

    fun downloads(): List<String> = nexus.requests().filter { it.path.startsWith("/repository/releases/") }.map { it.path }

    override fun close() {
        nexus.close()
    }

    companion object {
        val LEASE: Duration = Duration.ofSeconds(30)

        fun digest(algorithm: String, bytes: ByteArray): String =
            MessageDigest.getInstance(algorithm).digest(bytes).joinToString("") { "%02x".format(it) }

        fun hostedReleases(): RepositorySettings = RepositorySettings(id = "releases", visibility = RepositoryVisibility.PRIVATE, type = RepositoryType.HOSTED)
    }
}

/** Records what the service asked of the Ingot side, in order. */
internal class RecordingPorts(private val target: MemoryTarget, private val domain: MavenDomainPort) : ImportPorts {

    val bulkStarted = mutableListOf<String>()
    val bulkEnded = mutableListOf<String>()
    val runs = mutableListOf<Pair<Long, Map<String, Any>>>()
    var existing: Set<String> = emptySet()

    override fun writer(jobId: Long): TargetWriter = target

    override fun domain(jobId: Long): MavenDomainPort = domain

    override fun existingTargets(): Set<String> = existing + (domain.read().repositoryIds)

    override fun bulkSessionStarted(session: String) {
        bulkStarted += session
    }

    override fun bulkSessionEnded(session: String) {
        bulkEnded += session
    }

    override fun runFinished(jobId: Long, details: Map<String, Any>) {
        runs += jobId to details
    }
}
