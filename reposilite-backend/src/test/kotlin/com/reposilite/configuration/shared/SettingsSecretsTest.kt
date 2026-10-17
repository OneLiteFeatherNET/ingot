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

package com.reposilite.configuration.shared

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.reposilite.ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER
import com.reposilite.auth.application.AuthenticationSettings
import com.reposilite.auth.application.LdapSettings
import com.reposilite.maven.application.MavenSettings
import com.reposilite.maven.application.MirrorCredentials
import com.reposilite.maven.application.MirroredRepositorySettings
import com.reposilite.maven.application.RepositorySettings
import com.reposilite.storage.filesystem.FileSystemStorageProviderSettings
import com.reposilite.storage.s3.S3StorageProviderSettings
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class SettingsSecretsTest {

    private val placeholder = "********"

    private fun s3(secretKey: String = "s3-secret") = S3StorageProviderSettings(
        bucketName = "bucket",
        accessKey = "access-key",
        secretKey = secretKey,
        endpoint = "https://s3.example.test"
    )

    private fun mirror(reference: String, password: String) = MirroredRepositorySettings(
        reference = reference,
        authorization = MirrorCredentials(login = "login", password = password),
        httpProxy = "HTTP 127.0.0.1:1081"
    )

    private fun maven(vararg repositories: RepositorySettings) =
        DEFAULT_OBJECT_MAPPER.valueToTree<ObjectNode>(MavenSettings(repositories = repositories.toList()))

    private fun repository(id: String, secretKey: String = "s3-secret", proxied: List<MirroredRepositorySettings> = emptyList()) =
        RepositorySettings(id = id, storageProvider = s3(secretKey), proxied = proxied)

    private fun JsonNode.entry(index: Int): JsonNode =
        get("repositories").get(index)

    private fun JsonNode.maskedPaths(): Set<String> {
        val paths = mutableSetOf<String>()
        fun walk(node: JsonNode, path: String) {
            when {
                node.isTextual && node.textValue() == placeholder -> paths += path
                node.isObject -> node.fields().forEach { (name, child) -> walk(child, "$path/$name") }
                node.isArray -> node.forEachIndexed { index, child -> walk(child, "$path/$index") }
            }
        }
        walk(this, "")
        return paths
    }

    @Test
    fun `should replace a non-empty secret with the placeholder when masking`() {
        // given: an authentication domain with a stored LDAP search password
        val settings = DEFAULT_OBJECT_MAPPER.valueToTree<ObjectNode>(
            AuthenticationSettings(ldap = LdapSettings(searchUserPassword = "ldap-secret"))
        )

        // when: the domain is masked
        val masked = SettingsSecrets.mask(AuthenticationSettings::class.java, settings)

        // then: the password is replaced with the placeholder
        assertThat(masked.get("ldap").get("searchUserPassword").textValue())
            .describedAs("LDAP search password must be hidden")
            .isEqualTo(placeholder)
    }

    @Test
    fun `should keep an empty secret empty when masking`() {
        // given: an authentication domain without an LDAP search password
        val settings = DEFAULT_OBJECT_MAPPER.valueToTree<ObjectNode>(
            AuthenticationSettings(ldap = LdapSettings(searchUserPassword = ""))
        )

        // when: the domain is masked
        val masked = SettingsSecrets.mask(AuthenticationSettings::class.java, settings)

        // then: the empty value stays empty so the dashboard can show that nothing is set
        assertThat(masked.get("ldap").get("searchUserPassword").textValue())
            .describedAs("an empty secret must stay empty")
            .isEmpty()
    }

    @Test
    fun `should leave fields that are not secrets readable when masking`() {
        // given: an LDAP search user with a password
        val settings = DEFAULT_OBJECT_MAPPER.valueToTree<ObjectNode>(
            AuthenticationSettings(ldap = LdapSettings(searchUserDn = "cn=reposilite", searchUserPassword = "ldap-secret"))
        )

        // when: the domain is masked
        val masked = SettingsSecrets.mask(AuthenticationSettings::class.java, settings)

        // then: the search user DN is not a secret and stays readable
        assertThat(masked.get("ldap").get("searchUserDn").textValue()).isEqualTo("cn=reposilite")
    }

    @Test
    fun `should mask exactly the secrets of the maven domain`() {
        // given: a repository with S3 storage and a mirror with credentials and an HTTP proxy
        val settings = maven(repository("releases", proxied = listOf(mirror("https://mirror.example.test/releases", "mirror-secret"))))

        // when: the domain is masked
        val masked = SettingsSecrets.mask(MavenSettings::class.java, settings)

        // then: S3 access key, secret key and endpoint, mirror password and HTTP proxy are hidden, nothing else
        assertThat(masked.maskedPaths())
            .describedAs("hidden fields of the maven domain")
            .containsExactlyInAnyOrder(
                "/repositories/0/storageProvider/accessKey",
                "/repositories/0/storageProvider/secretKey",
                "/repositories/0/storageProvider/endpoint",
                "/repositories/0/proxied/0/authorization/password",
                "/repositories/0/proxied/0/httpProxy"
            )
    }

    @Test
    fun `should not mask storage fields of a filesystem repository`() {
        // given: a repository stored on the local file system
        val settings = maven(RepositorySettings(id = "local", storageProvider = FileSystemStorageProviderSettings()))

        // when: the domain is masked
        val masked = SettingsSecrets.mask(MavenSettings::class.java, settings)

        // then: nothing is hidden
        assertThat(masked.maskedPaths()).isEmpty()
    }

    @Test
    fun `should restore the stored tree after a masked round trip`() {
        // given: a stored maven domain with secrets, read by the dashboard and sent back unchanged
        val stored = maven(repository("releases", proxied = listOf(mirror("https://mirror.example.test/releases", "mirror-secret"))))
        val incoming = SettingsSecrets.mask(MavenSettings::class.java, stored)

        // when: the masked document is restored against the stored one
        val restored = SettingsSecrets.restore(MavenSettings::class.java, incoming, stored).get()

        // then: every secret is back, so the round trip is lossless
        assertThat(restored).isEqualTo(stored)
    }

    @Test
    fun `should replace the stored secret when a new value is sent`() {
        // given: a stored maven domain and an incoming document with a new S3 secret key
        val stored = maven(repository("releases", secretKey = "old-secret"))
        val incoming = maven(repository("releases", secretKey = "rotated-secret"))

        // when: the incoming document is restored
        val restored = SettingsSecrets.restore(MavenSettings::class.java, incoming, stored).get()

        // then: the new value is taken as it is
        assertThat(restored.entry(0).get("storageProvider").get("secretKey").textValue()).isEqualTo("rotated-secret")
    }

    @Test
    fun `should clear the stored secret when an empty value is sent`() {
        // given: a stored maven domain and an incoming document with an empty S3 secret key
        val stored = maven(repository("releases", secretKey = "old-secret"))
        val incoming = maven(repository("releases", secretKey = ""))

        // when: the incoming document is restored
        val restored = SettingsSecrets.restore(MavenSettings::class.java, incoming, stored).get()

        // then: the empty value clears the stored secret
        assertThat(restored.entry(0).get("storageProvider").get("secretKey").textValue()).isEmpty()
    }

    @Test
    fun `should match repositories by id when they are reordered`() {
        // given: two repositories with different secret keys, sent back in reverse order with placeholders
        val stored = maven(repository("first", secretKey = "first-secret"), repository("second", secretKey = "second-secret"))
        val incoming = SettingsSecrets.mask(
            MavenSettings::class.java,
            maven(repository("second", secretKey = "s"), repository("first", secretKey = "s"))
        )

        // when: the incoming document is restored
        val restored = SettingsSecrets.restore(MavenSettings::class.java, incoming, stored).get()

        // then: each repository keeps its own secret key
        assertThat(restored.entry(0).get("storageProvider").get("secretKey").textValue()).isEqualTo("second-secret")
        assertThat(restored.entry(1).get("storageProvider").get("secretKey").textValue()).isEqualTo("first-secret")
    }

    @Test
    fun `should match mirrors by reference when they are reordered`() {
        // given: a repository with two mirrors, sent back in reverse order with placeholders
        val stored = maven(
            repository(
                "releases",
                proxied = listOf(mirror("https://a.example.test/", "a-secret"), mirror("https://b.example.test/", "b-secret"))
            )
        )
        val incoming = SettingsSecrets.mask(
            MavenSettings::class.java,
            maven(
                repository(
                    "releases",
                    proxied = listOf(mirror("https://b.example.test/", "x"), mirror("https://a.example.test/", "x"))
                )
            )
        )

        // when: the incoming document is restored
        val restored = SettingsSecrets.restore(MavenSettings::class.java, incoming, stored).get()

        // then: each mirror keeps the password of its own reference
        val proxied = restored.entry(0).get("proxied")
        assertThat(proxied.get(0).get("authorization").get("password").textValue()).isEqualTo("b-secret")
        assertThat(proxied.get(1).get("authorization").get("password").textValue()).isEqualTo("a-secret")
    }

    @Test
    fun `should reject a placeholder for a repository that is not stored`() {
        // given: a stored maven domain and a new repository whose secret is sent as a placeholder
        val stored = maven(repository("releases"))
        val incoming = SettingsSecrets.mask(MavenSettings::class.java, maven(repository("releases"), repository("new-repo")))

        // when: the incoming document is restored
        val result = SettingsSecrets.restore(MavenSettings::class.java, incoming, stored)

        // then: the request is rejected and the message names the entry and the field
        assertThat(result.isErr).describedAs("restore must fail for an unknown entry").isTrue
        assertThat(result.error)
            .describedAs("error message")
            .contains("new-repo")
            .contains("storageProvider")
    }

    @Test
    fun `should reject a placeholder for a repository that was renamed`() {
        // given: a stored repository "old" and an incoming repository "new" that keeps the placeholder
        val stored = maven(repository("old"))
        val incoming = SettingsSecrets.mask(MavenSettings::class.java, maven(repository("new")))

        // when: the incoming document is restored
        val result = SettingsSecrets.restore(MavenSettings::class.java, incoming, stored)

        // then: the secret is not moved to a different entry, the caller has to send the real value
        assertThat(result.isErr).describedAs("a renamed entry must not inherit a secret by position").isTrue
    }

    @Test
    fun `should reject a placeholder for a storage provider that was switched from filesystem`() {
        // given: a stored filesystem repository and an incoming S3 repository with the placeholder
        val stored = maven(RepositorySettings(id = "releases", storageProvider = FileSystemStorageProviderSettings()))
        val incoming = SettingsSecrets.mask(MavenSettings::class.java, maven(repository("releases")))

        // when: the incoming document is restored
        val result = SettingsSecrets.restore(MavenSettings::class.java, incoming, stored)

        // then: there is no stored secret to keep
        assertThat(result.isErr).describedAs("no stored S3 secret exists to keep").isTrue
    }

    @Test
    fun `should accept an empty secret for a new repository`() {
        // given: a stored maven domain and a new repository with an empty secret key
        val stored = maven(repository("releases"))
        val incoming = maven(repository("releases"), repository("new-repo", secretKey = ""))

        // when: the incoming document is restored
        val result = SettingsSecrets.restore(MavenSettings::class.java, incoming, stored)

        // then: an empty value is a real value and needs no stored counterpart
        assertThat(result.isOk).describedAs("an empty value is not a placeholder").isTrue
    }

    @Test
    fun `should withhold secret values from a shared configuration document`() {
        // given: a shared configuration document with an LDAP password and an unknown plugin domain
        val document = """{"authentication":{"ldap":{"searchUserPassword":"ldap-secret"}},"plugin":{"token":"plugin-secret"}}"""

        // when: the document is redacted for the debug log
        val redacted = SettingsSecrets.redactDocument(
            document = document,
            types = mapOf("authentication" to AuthenticationSettings::class.java)
        )

        // then: no secret value appears in the output
        assertThat(redacted)
            .describedAs("redacted document")
            .doesNotContain("ldap-secret")
            .doesNotContain("plugin-secret")
            .contains(placeholder)
    }

    @Test
    fun `should withhold the whole content of an unreadable shared configuration document`() {
        // given: a shared configuration document that is not valid JSON but contains a secret
        val document = """{"authentication":{"ldap":{"searchUserPassword":"ldap-secret"}"""

        // when: the document is redacted for the debug log
        val redacted = SettingsSecrets.redactDocument(document, types = emptyMap())

        // then: nothing from the document is printed
        assertThat(redacted).doesNotContain("ldap-secret")
    }

    @Test
    fun `should keep the stored credential of a matched mirror that the body leaves out`() {
        // given: a stored mirror with a credential, and an incoming entry for the same reference without it
        val stored = maven(repository("releases", proxied = listOf(mirror("https://a.example.test/", "a-secret"))))
        val incoming = maven(repository("releases", proxied = listOf(MirroredRepositorySettings(reference = "https://a.example.test/"))))
        (incoming.entry(0).get("proxied").get(0) as ObjectNode).remove("authorization")

        // when: the incoming document is restored
        val restored = SettingsSecrets.restore(MavenSettings::class.java, incoming, stored).get()

        // then: the credential is the stored one, and the other settings are the incoming ones
        val mirror = restored.entry(0).get("proxied").get(0)
        assertThat(mirror.get("authorization").get("password").textValue()).isEqualTo("a-secret")
    }

    @Test
    fun `should remove the stored credential of a mirror when null is sent for it`() {
        // given: a stored mirror with a credential, and an incoming entry that sends null for it
        val stored = maven(repository("releases", proxied = listOf(mirror("https://a.example.test/", "a-secret"))))
        val incoming = DEFAULT_OBJECT_MAPPER.valueToTree<ObjectNode>(
            MavenSettings(repositories = listOf(repository("releases", proxied = listOf(mirror("https://a.example.test/", "x")))))
        )
        (incoming.entry(0).get("proxied").get(0) as ObjectNode).putNull("authorization")

        // when: the incoming document is restored
        val restored = SettingsSecrets.restore(MavenSettings::class.java, incoming, stored).get()

        // then: the credential is removed, because the null was sent on purpose
        assertThat(restored.entry(0).get("proxied").get(0).get("authorization").isNull).isTrue
    }

    @Test
    fun `should not restore an absent list of mirrors, so a removed list stays removed`() {
        // given: a stored repository with mirrors, and an incoming entry without a proxied list
        val stored = maven(repository("releases", proxied = listOf(mirror("https://a.example.test/", "a-secret"))))
        val incoming = maven(repository("releases"))
        (incoming.entry(0) as ObjectNode).remove("proxied")

        // when: the incoming document is restored
        val restored = SettingsSecrets.restore(MavenSettings::class.java, incoming, stored).get()

        // then: the list is not brought back from the stored entry
        assertThat(restored.entry(0).has("proxied")).isFalse
    }

    @Test
    fun `should refuse to keep a credential when several stored entries share the reference`() {
        // given: two stored mirrors of the same reference, each with its own credential, and an incoming entry without credentials
        val stored = maven(
            repository(
                "releases",
                proxied = listOf(mirror("https://a.example.test/", "first-secret"), mirror("https://a.example.test/", "second-secret"))
            )
        )
        val incoming = maven(repository("releases", proxied = listOf(MirroredRepositorySettings(reference = "https://a.example.test/"))))
        (incoming.entry(0).get("proxied").get(0) as ObjectNode).remove("authorization")

        // when: the incoming document is restored
        val result = SettingsSecrets.restore(MavenSettings::class.java, incoming, stored)

        // then: no credential is guessed for the entry
        assertThat(result.isErr).describedAs("a credential must not be taken from one of two entries that share the reference").isTrue
    }

    @Test
    fun `should keep a placeholder as the literal value when secrets are not hidden`() {
        // given: a stored secret and an incoming document that carries the placeholder as a literal password
        val stored = maven(repository("releases", proxied = listOf(mirror("https://a.example.test/", "a-secret"))))
        val incoming = maven(repository("releases", proxied = listOf(mirror("https://a.example.test/", placeholder))))

        // when: the incoming document is restored without the placeholder rule
        val restored = SettingsSecrets.restore(MavenSettings::class.java, incoming, stored, placeholders = false).get()

        // then: the literal value is written, as the document of an instance that shows secrets always has been
        assertThat(restored.entry(0).get("proxied").get(0).get("authorization").get("password").textValue()).isEqualTo(placeholder)
    }

}
