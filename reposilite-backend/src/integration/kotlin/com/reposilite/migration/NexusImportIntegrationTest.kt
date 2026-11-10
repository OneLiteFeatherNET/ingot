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
@file:Suppress("FunctionName")

package com.reposilite.migration

import com.fasterxml.jackson.databind.JsonNode
import com.reposilite.ReposiliteObjectMapper
import com.reposilite.ReposiliteParameters
import com.reposilite.ReposiliteSpecification
import com.reposilite.RecommendedLocalSpecificationJunitExtension
import com.reposilite.configuration.shared.SharedConfigurationFacade
import com.reposilite.maven.RepositoryType
import com.reposilite.maven.application.MavenSettings
import com.reposilite.maven.application.RepositorySettings
import kong.unirest.core.Unirest
import net.onelitefeather.ingot.nexusimport.NexusImportFacade
import net.onelitefeather.ingot.nexusimport.fake.FakeNexus
import net.onelitefeather.ingot.nexusimport.fake.FakeReply
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/**
 * The Nexus import inside a running instance: the routes, the switch of the bundled jar, the dry run, the import with its verify,
 * the conflict policy, operator pause and resume, the audit modes and the admin-only access. The fake Nexus replays the
 * documented responses of the plugin's fixtures; no test waits on the clock, because the scheduler ticks only on request.
 */
@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class NexusImportIntegrationTest : ReposiliteSpecification() {

    private lateinit var nexus: FakeNexus

    override fun overrideParameters(parameters: ReposiliteParameters) {
        // given: the shipped import jar in the plugins directory, and no background tick, so the test decides when jobs run
        val jar = Path.of(checkNotNull(System.getProperty(PLUGIN_JAR_PROPERTY)) { "$PLUGIN_JAR_PROPERTY is not set, run the suite through :reposilite-backend:integration" })
        Files.createDirectories(parameters.pluginDirectory)
        Files.copy(jar, parameters.pluginDirectory.resolve(jar.fileName))
        System.setProperty(TICK_PROPERTY, "0")
    }

    override fun overrideSharedConfiguration(sharedConfigurationFacade: SharedConfigurationFacade) {
        // a target that already exists, for the conflict test
        sharedConfigurationFacade.getDomainSettings<MavenSettings>().update { settings ->
            settings.copy(repositories = settings.repositories + RepositorySettings(id = "imported", type = RepositoryType.HOSTED))
        }
    }

    @BeforeEach
    fun startNexus() {
        nexus = FakeNexus()
        nexus.serveFixture("/service/rest/v1/repositories", "repositories.json")
        nexus.serveFixture("/service/rest/v1/repositories/maven/hosted/releases", "maven-hosted-releases.json")
        nexus.serveFixture("/service/rest/v1/repositories/maven/proxy/snapshots-proxy", "maven-proxy.json")
        nexus.serveFixture("/service/rest/v1/repositories/maven/group/public-group", "maven-group.json")
        nexus.on("/service/rest/v1/assets") { request ->
            val items = if (request.query["repository"] == "releases") FILES.map { (path, bytes) -> assetItem(path, bytes) } else emptyList()
            FakeReply.json(ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER.writeValueAsString(mapOf("items" to items, "continuationToken" to null)))
        }
        FILES.forEach { (path, bytes) -> nexus.on("/repository/releases/$path") { FakeReply.bytes(bytes) } }
    }

    @AfterEach
    fun stopNexus() {
        nexus.close()
        System.clearProperty(TICK_PROPERTY)
    }

    @Test
    fun `should refuse every import route to a token without instance admin and send nothing to Nexus`() {
        // given: a token with no admin permission
        val (name, secret) = useAuth("reader", "reader-secret")

        // when: the routes are called with it
        val create = Unirest.post("$base$JOBS").basicAuth(name, secret).header(JSON, JSON_TYPE).body(job("DRY_RUN")).asString()
        val list = Unirest.get("$base$JOBS").basicAuth(name, secret).asString()
        val discover = Unirest.post("$base/api/migration/nexus/discover").basicAuth(name, secret).header(JSON, JSON_TYPE).body(connection()).asString()

        // then: each is refused and Nexus was never contacted
        assertThat(listOf(create.status, list.status, discover.status)).containsOnly(403)
        assertThat(nexus.requests()).isEmpty()
    }

    @Test
    fun `should answer a dry run with the loss report and change nothing in Ingot`() {
        // given: a dry run of the Nexus repositories
        val id = createJob("DRY_RUN")

        // when: the job runs
        tick()

        // then: it completed, the report maps the hosted repository, and no file exists in Ingot
        assertThat(status(id)).isEqualTo("COMPLETED")
        val releases = report(id).single { it["nexusId"].asText() == "releases" }
        assertThat(releases["status"].asText()).isEqualTo("MAPPED")
        assertThat(get("/releases/com/example/app/1.0/app-1.0.jar").status).isEqualTo(404)
        nexus.assertOnlyReadMethods()
    }

    @Test
    fun `should import a hosted repository and verify it, so that the copied file is served`() {
        // given: an import of the hosted repository under its own name
        val id = createJob("IMPORT")

        // when: the import runs, and then a verify job checks it against Nexus
        tick()
        val verify = createJob("VERIFY", mapOf("repositories" to listOf("releases"), "verifyImportJob" to id))
        tick()

        // then: the import completed, the verify pass verified the repository and the file is served with its bytes
        assertThat(status(id)).isEqualTo("COMPLETED")
        assertThat(repositoryStatus(verify, "releases")).isEqualTo("VERIFIED")
        val served = get("/releases/com/example/app/1.0/app-1.0.jar")
        assertThat(served.status).isEqualTo(200)
        assertThat(served.body).isEqualTo("jar-bytes")
        nexus.assertOnlyReadMethods()
    }

    @Test
    fun `should keep a differing local file under the refuse policy and leave the repository conflicted`() {
        // given: the existing target holds a different pom at a path Nexus also has
        useDocument("imported", "com/example/app/1.0", "app-1.0.pom", "local-pom", store = true)
        val id = createJob("IMPORT", mapOf("targets" to mapOf("releases" to "imported")))

        // when: the import runs under the default policy
        tick()

        // then: the local file is untouched, the conflict is listed with both checksums, and the repository is conflicted
        assertThat(get("/imported/com/example/app/1.0/app-1.0.pom").body).isEqualTo("local-pom")
        val conflicts = json(Unirest.get("$base$JOBS/$id/conflicts").let { admin(it) }.asString().body)
        assertThat(conflicts.single()["path"].asText()).isEqualTo("com/example/app/1.0/app-1.0.pom")
        assertThat(conflicts.single()["detail"].asText()).contains("nexus sha1").contains("target sha1")
        assertThat(repositoryStatus(id, "imported")).isEqualTo("CONFLICTED")
    }

    @Test
    fun `should pause at the operator's request and copy each file once after the resume`() {
        // given: the operator pauses the job from the route while the pom is downloaded
        val id = createJob("IMPORT")
        var requested = false
        nexus.on("/repository/releases/com/example/app/1.0/app-1.0.pom") {
            if (!requested) {
                requested = true
                assertThat(admin(Unirest.post("$base$JOBS/$id/pause")).asString().status).isEqualTo(204)
            }
            FakeReply.bytes("pom-bytes".toByteArray())
        }

        // when: the job runs and stops before the next file
        tick()
        assertThat(status(id)).isEqualTo("PAUSED")

        // when: the operator resumes it and the job runs to its end
        assertThat(admin(Unirest.post("$base$JOBS/$id/resume")).asString().status).isEqualTo(204)
        tick()

        // then: it completed, and every file of Nexus was downloaded exactly once
        assertThat(status(id)).isEqualTo("COMPLETED")
        FILES.keys.forEach { path ->
            assertThat(nexus.requests().count { it.path == "/repository/releases/$path" }).describedAs(path).isEqualTo(1)
        }
    }

    @Test
    fun `should summarise the deploys of a summary audit job into one run event`() {
        // given: an import that asks for the summary audit mode
        val id = createJob("IMPORT", mapOf("audit" to "summary"))

        // when: it runs
        tick()

        // then: the audit trail holds the run event of the job and no per-file deploy of the job
        assertThat(status(id)).isEqualTo("COMPLETED")
        val events = auditEvents()
        assertThat(events.filter { it["action"].asText() == "NEXUS_IMPORT_RUN" }).hasSize(1)
        assertThat(events.filter { it["action"].asText() == "ARTIFACT_DEPLOYED" && it["actor"].asText() == "migration" }).isEmpty()
    }

    @Test
    fun `should record each copied file as a deploy by the migration actor in the default audit mode`() {
        // given: an import in the default per-file audit mode
        val id = createJob("IMPORT")

        // when: it runs
        tick()

        // then: every copied file has its own deploy event with the migration actor
        assertThat(status(id)).isEqualTo("COMPLETED")
        val deploys = auditEvents().filter { it["action"].asText() == "ARTIFACT_DEPLOYED" && it["actor"].asText() == "migration" }
        assertThat(deploys).hasSize(FILES.size)
    }

    // ---- helpers -----------------------------------------------------------------------------------------------------------

    private fun tick() {
        useFacade<NexusImportFacade>().tick()
    }

    private fun admin(request: kong.unirest.core.HttpRequest<*>): kong.unirest.core.HttpRequest<*> {
        val (name, secret) = useDefaultManagementToken()
        return request.basicAuth(name, secret)
    }

    private fun connection(): String = ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER.writeValueAsString(
        mapOf("url" to nexus.baseUri.toString(), "user" to "importer", "password" to "pw"),
    )

    private fun job(kind: String, extra: Map<String, Any> = emptyMap()): String = ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER.writeValueAsString(
        mapOf("kind" to kind, "url" to nexus.baseUri.toString(), "user" to "importer", "password" to "pw") + extra,
    )

    private fun createJob(kind: String, extra: Map<String, Any> = emptyMap()): String {
        val response = admin(Unirest.post("$base$JOBS").header(JSON, JSON_TYPE).body(job(kind, extra))).asString()
        assertThat(response.status).describedAs(response.body).isEqualTo(202)
        return json(response.body)["id"].asText()
    }

    private fun status(id: String): String = json(admin(Unirest.get("$base$JOBS/$id")).asString().body)["status"].asText()

    private fun repositoryStatus(id: String, repository: String): String =
        json(admin(Unirest.get("$base$JOBS/$id")).asString().body)["repositories"].single { it["nexusName"].asText() == repository }["status"].asText()

    private fun report(id: String): List<JsonNode> =
        json(admin(Unirest.get("$base$JOBS/$id/report")).asString().body)["rows"].toList()

    private fun auditEvents(): List<JsonNode> =
        json(admin(Unirest.get("$base/api/audit")).asString().body)["events"].toList()

    private fun get(path: String) = admin(Unirest.get("$base$path")).asString()

    private fun json(text: String): JsonNode = ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER.readTree(text)

    private fun assetItem(path: String, bytes: ByteArray) = mapOf(
        "path" to path,
        "downloadUrl" to "${nexus.baseUri}/repository/releases/$path",
        "fileSize" to bytes.size,
        "checksum" to mapOf("sha1" to sha("SHA-1", bytes), "md5" to sha("MD5", bytes)),
    )

    private fun sha(algorithm: String, bytes: ByteArray) =
        MessageDigest.getInstance(algorithm).digest(bytes).joinToString("") { "%02x".format(it) }

    private companion object {
        const val PLUGIN_JAR_PROPERTY = "reposilite.nexusImport.plugin"
        const val TICK_PROPERTY = "ingot.nexusImport.tickSeconds"
        const val JOBS = "/api/migration/nexus/jobs"
        const val JSON = "Content-Type"
        const val JSON_TYPE = "application/json"
        val FILES = linkedMapOf(
            "com/example/app/1.0/app-1.0.jar" to "jar-bytes".toByteArray(),
            "com/example/app/1.0/app-1.0.pom" to "pom-bytes".toByteArray(),
            "com/example/app/maven-metadata.xml" to "meta-bytes".toByteArray(),
        )
    }
}
