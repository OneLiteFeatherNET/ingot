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
import com.reposilite.configuration.local.LocalConfiguration
import com.reposilite.storage.s3.S3StorageProviderSettings
import net.onelitefeather.ingot.cluster.ClusterBusUrl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.reflect.full.memberProperties

internal class RedactionRulesTest {

    // R1: local configuration

    @Test
    fun `should write an allowlisted local value and withhold an unknown local key`() {
        // given: a local value that is allowlisted and one that no rule names
        val tally = RedactionTally()

        // when: the local values are redacted
        val local = Redaction.local(mapOf("port" to "8080", "unknownKey" to "planted-unknown-value"), tally)

        // then: the allowlisted value is written and the unknown one only by name
        assertThat(local["port"]).`as`("allowlisted port").isEqualTo("8080")
        assertThat(local["unknownKey"]).`as`("unknown key").isEqualTo(Redaction.WITHHELD)
        assertThat(local.toString()).`as`("output of the local values").doesNotContain("planted-unknown-value")
    }

    @Test
    fun `should withhold the TLS key password and the key path`() {
        // when: the TLS values are redacted
        val local = Redaction.local(mapOf("keyPassword" to "tls-planted", "keyPath" to "/secret/cert.pem"), RedactionTally())

        // then: neither value appears
        assertThat(local["keyPassword"]).`as`("key password").isEqualTo(Redaction.WITHHELD)
        assertThat(local["keyPath"]).`as`("key path").isEqualTo(Redaction.WITHHELD)
    }

    @Test
    fun `should reduce the database setting to kind and host and drop the user and the password`() {
        // when: a positional MariaDB setting is redacted
        val local = Redaction.local(mapOf("database" to "mariadb db.example:3306 shop user hunter22"), RedactionTally())

        // then: only the kind and the endpoint remain
        assertThat(local["database"]).`as`("database").isEqualTo("mariadb db.example:3306")
    }

    @Test
    fun `should count every withheld local value and every dropped part of the database setting`() {
        // given: three withheld values and a database setting that carries credentials
        val tally = RedactionTally()

        // when: the local values are redacted
        Redaction.local(
            mapOf(
                "unknownKey" to "planted",
                "keyPassword" to "tls-planted",
                "keyPath" to "/secret/cert.pem",
                "database" to "mariadb db.example:3306 shop user hunter22",
            ),
            tally,
        )

        // then: each withheld value and the database redaction is counted once
        assertThat(tally.count).`as`("redactions counted").isEqualTo(4)
    }

    @Test
    fun `should withhold every local property that is not named in the allowlist`() {
        // when: the properties of the local configuration are listed by reflection
        val properties = LocalConfiguration::class.memberProperties.map { it.name }.toSet()

        // then: each one is allowlisted, withheld by name or structured, so a new setting is an explicit decision
        val unnamed = properties - Redaction.LOCAL_ALLOWLIST - Redaction.LOCAL_WITHHELD - setOf(Redaction.LOCAL_DATABASE)
        assertThat(unnamed).`as`("local properties with no redaction rule").isEmpty()
    }

    @Test
    fun `should name no allowlisted local key that the local configuration does not have`() {
        // when: the properties of the local configuration are listed by reflection
        val properties = LocalConfiguration::class.memberProperties.map { it.name }.toSet()

        // then: every allowlisted or withheld name is a real property, so no rule is stale
        val stale = (Redaction.LOCAL_ALLOWLIST + Redaction.LOCAL_WITHHELD) - properties
        assertThat(stale).`as`("names of rules that match no property").isEmpty()
    }

    // R1: environment, system properties and JVM arguments

    @Test
    fun `should write an allowlisted environment variable and withhold any other`() {
        // given: an allowlisted variable, a read-only switch and a variable with a planted value
        val tally = RedactionTally()
        val environment = mapOf(
            "PUID" to "1000",
            "INGOT_READ_ONLY" to "true",
            "REPOSILITE_SUPPORT_BUNDLE_ENABLED" to "false",
            "PLANTED_ENV" to "env-planted-value",
        )

        // when: the environment is redacted
        val redacted = Redaction.environment(environment, tally)

        // then: the allowlisted values are written and the planted one is withheld by name
        assertThat(redacted["PUID"]).`as`("PUID").isEqualTo("1000")
        assertThat(redacted["INGOT_READ_ONLY"]).`as`("read-only switch").isEqualTo("true")
        assertThat(redacted["REPOSILITE_SUPPORT_BUNDLE_ENABLED"]).`as`("legacy switch").isEqualTo("false")
        assertThat(redacted["PLANTED_ENV"]).`as`("planted variable").isEqualTo(Redaction.WITHHELD)
    }

    @Test
    fun `should redact the arguments of the options variables through the JVM argument rule`() {
        // when: JAVA_OPTS carries a memory flag and a property with a secret name
        val redacted = Redaction.environment(
            mapOf("JAVA_OPTS" to "-Xmx512m -Dfoo.password=planted-option"),
            RedactionTally(),
        )

        // then: the memory flag stays and the secret property is withheld
        assertThat(redacted["JAVA_OPTS"]).`as`("JAVA_OPTS").isEqualTo("-Xmx512m -Dfoo.password=[withheld]")
    }

    @Test
    fun `should write an ingot system property and withhold a property with a secret name`() {
        // when: the system properties are redacted
        val redacted = Redaction.systemProperties(
            mapOf(
                "ingot.supportBundle.enabled" to "true",
                "ingot.db.password" to "planted-prop",
                "foo.password" to "planted-foo",
                "java.version" to "21",
            ),
            RedactionTally(),
        )

        // then: only the ingot switch keeps its value
        assertThat(redacted["ingot.supportBundle.enabled"]).`as`("ingot switch").isEqualTo("true")
        assertThat(redacted["ingot.db.password"]).`as`("ingot property with a secret name").isEqualTo(Redaction.WITHHELD)
        assertThat(redacted["foo.password"]).`as`("foreign property with a secret name").isEqualTo(Redaction.WITHHELD)
        assertThat(redacted["java.version"]).`as`("foreign property").isEqualTo(Redaction.WITHHELD)
    }

    @Test
    fun `should offer the withheld JVM argument values to the log scrub but not the withheld names`() {
        // when: the withheld values are collected from a secret property and an agent path
        val values = Redaction.withheldValues(
            environment = emptyMap(),
            systemProperties = emptyMap(),
            jvmArguments = listOf("-Dplanted.token=jvm-planted-6620", "-Xmx512m", "-javaagent:/opt/agent-planted.jar"),
        )

        // then: the secret value is among them, and the kept memory flag is not
        assertThat(values).`as`("withheld JVM values").contains("jvm-planted-6620", "/opt/agent-planted.jar").doesNotContain("-Xmx512m")
    }

    @Test
    fun `should keep memory and option arguments and withhold a secret property argument`() {
        // when: the JVM arguments are redacted
        val redacted = Redaction.jvmArguments(
            listOf(
                "-Xmx512m",
                "-XX:+UseG1GC",
                "--add-opens=java.base/java.lang=ALL-UNNAMED",
                "-Dfoo.password=x-planted",
                "-Dingot.feature=on",
                "-javaagent:/opt/agent.jar",
            ),
            RedactionTally(),
        )

        // then: the values of memory, option and ingot arguments stay, the secret property and the agent path do not
        assertThat(redacted).`as`("redacted JVM arguments").containsExactly(
            "-Xmx512m",
            "-XX:+UseG1GC",
            "--add-opens=java.base/java.lang=ALL-UNNAMED",
            "-Dfoo.password=[withheld]",
            "-Dingot.feature=on",
            "-javaagent:[withheld]",
        )
    }

    // R2: shared settings

    @Test
    fun `should mask a shared secret with the settings masking even when the hide flag is off`() {
        // given: an S3 storage setting with a secret key and an empty access key
        val storage = S3StorageProviderSettings(bucketName = "bucket", secretKey = "s3-planted-secret", region = "eu-central-1")
        val domain = SharedDomainDocument("storage", S3StorageProviderSettings::class.java, DEFAULT_OBJECT_MAPPER.valueToTree(storage))

        // when: the shared domains are redacted
        val shared = Redaction.shared(listOf(domain), RedactionTally())

        // then: the secret key is hidden, the empty secret stays empty and the region is written
        val tree = shared.getValue("storage").toString()
        assertThat(tree).`as`("masked storage domain").doesNotContain("s3-planted-secret").contains(Redaction.HIDDEN_SECRET)
        assertThat(tree).`as`("region of the storage domain").contains("eu-central-1")
    }

    @Test
    fun `should mask a secret reference as well`() {
        // given: a secret key that is a reference to an environment variable
        val storage = S3StorageProviderSettings(secretKey = "\${env:S3_SECRET}")
        val domain = SharedDomainDocument("storage", S3StorageProviderSettings::class.java, DEFAULT_OBJECT_MAPPER.valueToTree(storage))

        // when: the shared domains are redacted
        val tree = Redaction.shared(listOf(domain), RedactionTally()).getValue("storage").toString()

        // then: the reference is masked too
        assertThat(tree).`as`("masked reference").doesNotContain("S3_SECRET").contains(Redaction.HIDDEN_SECRET)
    }

    @Test
    fun `should withhold a shared domain of unknown type completely`() {
        // given: a domain without a known type
        val domain = SharedDomainDocument("plugin-domain", null, DEFAULT_OBJECT_MAPPER.valueToTree(mapOf("token" to "planted")))

        // when: the shared domains are redacted
        val shared = Redaction.shared(listOf(domain), RedactionTally())

        // then: the domain is replaced by the withheld marker
        assertThat(shared.getValue("plugin-domain").asText()).`as`("unknown domain").isEqualTo(Redaction.WITHHELD)
    }

    // R4: cluster bus

    @Test
    fun `should write the bus URL without its credentials`() {
        // given: a bus URL with a user and a password
        val url = ClusterBusUrl.parse("nats://bus-user:bus-planted@bus.example:4222")
        val tally = RedactionTally()

        // when: the bus URL is redacted
        val redacted = Redaction.busUrl(url, tally)

        // then: the endpoint remains and the credentials do not
        assertThat(redacted).`as`("redacted bus URL").isEqualTo("nats://${Redaction.REDACTED_USER}@bus.example:4222")
        assertThat(tally.count).`as`("redactions for the bus credentials").isEqualTo(1)
    }

    @Test
    fun `should write no bus URL when no bus is configured`() {
        assertThat(Redaction.busUrl(null, RedactionTally())).`as`("bus URL without a bus").isNull()
    }

    // R3: database forms

    @Test
    fun `should describe an embedded database by its kind only`() {
        val description = DatabaseDescription.describe("sqlite reposilite.db")

        assertThat(description.text()).`as`("embedded database").isEqualTo("sqlite")
        assertThat(description.endpoint).`as`("endpoint of an embedded database").isNull()
    }

    @Test
    fun `should describe a positional MariaDB setting by kind and host and keep the credentials as secrets`() {
        val description = DatabaseDescription.describe("mariadb db.example:3306 shop user hunter22")

        assertThat(description.text()).`as`("text of the setting").isEqualTo("mariadb db.example:3306")
        assertThat(description.secrets).`as`("secret parts").containsExactlyInAnyOrder("user", "hunter22")
    }

    @Test
    fun `should describe a JDBC URL by kind and host and keep its query secrets`() {
        val description = DatabaseDescription.describe("jdbc:mariadb://db.example:3306/shop?sslMode=verify-full&password=query-planted-9 ops pw-planted")

        assertThat(description.text()).`as`("text of the JDBC URL").isEqualTo("jdbc:mariadb db.example:3306")
        assertThat(description.secrets).`as`("secret parts").contains("query-planted-9", "ops", "pw-planted")
    }

    @Test
    fun `should write only unparsed for a setting that cannot be read`() {
        val description = DatabaseDescription.describe("not a database at all")

        assertThat(description.text()).`as`("unreadable setting").isEqualTo("unparsed")
        assertThat(description.endpoint).`as`("endpoint of an unreadable setting").isNull()
    }

    @Test
    fun `should write only unparsed for a blank setting`() {
        assertThat(DatabaseDescription.describe("  ").text()).`as`("blank setting").isEqualTo("unparsed")
    }
}
