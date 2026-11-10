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

package net.onelitefeather.ingot.support

import com.reposilite.ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER
import com.reposilite.storage.s3.S3StorageProviderSettings
import net.onelitefeather.ingot.cluster.ClusterBusUrl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor

internal class SupportInformationCollectorTest {

    private val clock = Clock.fixed(Instant.parse("2026-11-10T10:00:00Z"), ZoneOffset.UTC)

    private val bundleFiles = setOf(
        "manifest.json",
        "system-info.json",
        "configuration-local.json",
        "configuration-shared.json",
        "cluster.json",
        "plugins.json",
        "logs/ingot-test.log",
    )

    private val plantedSecrets = listOf(
        "env-planted-5731",
        "prop-planted-8842",
        "jvm-planted-6620",
        "tls-planted-1177",
        "db-planted-3390",
        "dbuser",
        "s3-planted-4451",
        "s3-access-planted-7712",
        "bus-planted-2268",
        "log-planted-9953",
    )

    private fun fixtureSources(
        environment: Map<String, String> = emptyMap(),
        systemProperties: Map<String, String> = emptyMap(),
        jvmArguments: List<String> = emptyList(),
        localValues: Map<String, String?> = mapOf("hostname" to "0.0.0.0", "port" to "8080"),
        sharedDomains: List<SharedDomainDocument> = emptyList(),
        busUrl: ClusterBusUrl? = null,
        cachedLog: List<String> = listOf("boot finished"),
        buildInformation: Map<String, String> = emptyMap(),
    ) = SupportSources(
        instance = "ingot-test",
        clock = clock,
        version = "1.2.3",
        buildInformation = { buildInformation },
        jvm = { JvmFacts(vendor = "Eclipse Adoptium", version = "21.0.4", uptimeMillis = 5_000, heapUsedMb = 120.5, heapMaxMb = 512, processors = 4, threads = 30) },
        operatingSystem = { OperatingSystemFacts(name = "Linux", version = "6.8", architecture = "amd64", container = true) },
        environment = { environment },
        systemProperties = { systemProperties },
        jvmArguments = { jvmArguments },
        localValues = { localValues },
        sharedDomains = { sharedDomains },
        repositoryStorage = { listOf(RepositoryStorage(repository = "releases", kind = "s3", region = "eu-central-1")) },
        plugins = { listOf(PluginFact(name = "metrics", version = "1.2.3", dependencies = listOf("status"))) },
        cluster = { null },
        busUrl = { busUrl },
        cachedLog = { cachedLog },
    )

    private fun plantedSources() = fixtureSources(
        environment = mapOf("PLANTED_ENV_SECRET" to "env-planted-5731", "PUID" to "1000"),
        systemProperties = mapOf("ingot.planted.password" to "prop-planted-8842", "ingot.supportBundle.enabled" to "true"),
        jvmArguments = listOf("-Xmx512m", "-Dplanted.token=jvm-planted-6620"),
        localValues = mapOf(
            "hostname" to "0.0.0.0",
            "keyPassword" to "tls-planted-1177",
            "database" to "mariadb db.example:3306 shop dbuser db-planted-3390",
        ),
        sharedDomains = listOf(
            SharedDomainDocument(
                name = "storage",
                type = S3StorageProviderSettings::class.java,
                document = DEFAULT_OBJECT_MAPPER.valueToTree(
                    S3StorageProviderSettings(bucketName = "bucket", secretKey = "s3-planted-4451", accessKey = "s3-access-planted-7712", region = "eu-central-1")
                ),
            )
        ),
        busUrl = ClusterBusUrl.parse("nats://bus-user:bus-planted-2268@bus.example:4222"),
        cachedLog = listOf(
            "retry s3-planted-4451 failed",
            "auth Bearer abc.def-ghi",
            "db dbuser:db-planted-3390 down",
            "env env-planted-5731 seen",
            "prop prop-planted-8842 and jvm jvm-planted-6620",
            "tls tls-planted-1177 loaded",
            "bus bus-planted-2268 seen",
            "password=log-planted-9953",
        ),
    )

    @Test
    fun `should describe the system information with every section`() {
        // when: the system information is collected
        val info = SupportInformationCollector(fixtureSources(localValues = mapOf("hostname" to "0.0.0.0", "port" to "8080"))).collect().systemInfo

        // then: each section of the page is present with its facts
        assertThat(info.ingot.version).`as`("Ingot version").isEqualTo("1.2.3")
        assertThat(info.jvm.vendor).`as`("JVM vendor").isEqualTo("Eclipse Adoptium")
        assertThat(info.jvm.heapMaxMb).`as`("heap maximum").isEqualTo(512)
        assertThat(info.operatingSystem.container).`as`("container hint").isTrue()
        assertThat(info.storage.single().kind).`as`("storage kind").isEqualTo("s3")
        assertThat(info.storage.single().region).`as`("storage region").isEqualTo("eu-central-1")
        assertThat(info.plugins.single().name).`as`("plugin name").isEqualTo("metrics")
        assertThat(info.configuration.local["hostname"]).`as`("local configuration").isEqualTo("0.0.0.0")
        assertThat(info.cluster).`as`("cluster without a bus").isNull()
    }

    @Test
    fun `should write build information as unknown when the jar carries none`() {
        val info = SupportInformationCollector(fixtureSources()).collect().systemInfo

        assertThat(info.ingot.buildTime).`as`("build time without a manifest").isEqualTo("unknown")
        assertThat(info.ingot.commit).`as`("commit without a manifest").isEqualTo("unknown")
    }

    @Test
    fun `should write the build information of the jar when it carries one`() {
        val info = SupportInformationCollector(fixtureSources(buildInformation = mapOf("buildTime" to "2026-11-09T08:00:00Z", "commit" to "abc1234")))
            .collect().systemInfo

        assertThat(info.ingot.buildTime).`as`("build time").isEqualTo("2026-11-09T08:00:00Z")
        assertThat(info.ingot.commit).`as`("commit").isEqualTo("abc1234")
    }

    @Test
    fun `should write each file of the bundle`() {
        // when: the bundle is assembled
        val files = SupportInformationCollector(fixtureSources()).collect().files

        // then: every file of the contract is present
        assertThat(files.keys).`as`("files of the bundle").containsExactlyInAnyOrderElementsOf(bundleFiles)
    }

    @Test
    fun `should list every file and the redaction count in the manifest`() {
        // given: a bundle with a planted secret that is withheld
        val snapshot = SupportInformationCollector(plantedSources()).collect()

        // when: the manifest is read
        val manifest = DEFAULT_OBJECT_MAPPER.readTree(snapshot.files.getValue("manifest.json"))

        // then: the manifest names every other file, the rule version and a count of the redacted values
        val listed = manifest.get("files").map { it.get("path").asText() }
        assertThat(listed).`as`("files named in the manifest").containsExactlyInAnyOrderElementsOf(bundleFiles - "manifest.json")
        assertThat(manifest.get("redactionRuleVersion").asInt()).`as`("rule version").isEqualTo(1)
        assertThat(manifest.get("redactions").asInt()).`as`("redactions counted").isGreaterThan(0)
        assertThat(manifest.get("generatedAt").asText()).`as`("generation time from the clock").isEqualTo("2026-11-10T10:00:00Z")
    }

    @Test
    fun `should write the cached log of the answering instance with its name`() {
        val files = SupportInformationCollector(fixtureSources(cachedLog = listOf("boot finished"))).collect().files

        assertThat(String(files.getValue("logs/ingot-test.log"))).`as`("log of the instance").contains("boot finished")
    }

    @Test
    fun `should give the collector no parameter of a token, session, identity or audit type`() {
        // when: the constructor parameters of the collector and of its sources are listed
        val types = listOf(SupportInformationCollector::class, SupportSources::class).flatMap { owner ->
            owner.primaryConstructor!!.parameters.map { it.type.classifier as KClass<*> }
        }

        // then: none of them is a type that carries credentials or identities
        val forbidden = types.filter { type ->
            val name = type.qualifiedName.orEmpty()
            listOf("com.reposilite.token", "com.reposilite.audit", "net.onelitefeather.ingot.identity", "com.reposilite.auth")
                .any { name.startsWith(it) } || listOf("Token", "Session", "Identity", "Audit").any { type.simpleName.orEmpty().contains(it) }
        }
        assertThat(forbidden).`as`("forbidden parameter types").isEmpty()
    }

    @Test
    fun `should keep every planted secret out of the bundle and the system information`() {
        // given: secrets planted in the settings, the local configuration, the bus URL, the environment, the system
        // properties, the JVM arguments and the cached log
        val snapshot = SupportInformationCollector(plantedSources()).collect()

        // when: every entry of the bundle and the JSON of the system information are searched for each secret
        val texts = snapshot.files.mapValues { String(it.value) } + ("system-info-page" to DEFAULT_OBJECT_MAPPER.writeValueAsString(snapshot.systemInfo))

        // then: none of them is found anywhere
        plantedSecrets.forEach { secret ->
            val found = texts.filterValues { it.contains(secret) }.keys
            assertThat(found).`as`("entries containing the planted secret '$secret'").isEmpty()
        }
    }

    @Test
    fun `should pass the bundle of the planted secrets through the guard without a leak`() {
        val snapshot = SupportInformationCollector(plantedSources()).collect()

        assertThat(snapshot.guard.leaks(snapshot.files)).`as`("leaks reported by the guard").isEmpty()
    }

    @Test
    fun `should show the database by kind and endpoint and withhold the key password`() {
        val local = SupportInformationCollector(plantedSources()).collect().systemInfo.configuration.local

        assertThat(local["database"]).`as`("database").isEqualTo("mariadb db.example:3306")
        assertThat(local["keyPassword"]).`as`("key password").isEqualTo(Redaction.WITHHELD)
    }
}
