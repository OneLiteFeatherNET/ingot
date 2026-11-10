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
import com.reposilite.ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER
import com.reposilite.storage.s3.S3StorageProviderSettings
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Masking and restoring of a secret with settings secret references on and off. The tests use the tree of one settings class
 * and no environment, so nothing outside the test is read.
 */
internal class SettingsSecretReferenceMaskingTest {

    private val placeholder = "********"

    private fun s3(secretKey: String): JsonNode =
        DEFAULT_OBJECT_MAPPER.valueToTree(
            S3StorageProviderSettings(bucketName = "bucket", accessKey = "access-key", secretKey = secretKey, endpoint = "https://s3.example.test")
        )

    private fun JsonNode.secretKey(): String =
        get("secretKey").asText()

    private fun mask(secretKey: String, keepReferences: Boolean): String =
        SettingsSecrets.mask(S3StorageProviderSettings::class.java, s3(secretKey), keepReferences).secretKey()

    @Test
    fun `should show a reference as written when secrets are hidden and references are on`() {
        assertThat(mask("\${file:/run/secrets/s3}", keepReferences = true))
            .describedAs("masked value of a file reference with references on")
            .isEqualTo("\${file:/run/secrets/s3}")
    }

    @Test
    fun `should show an environment reference as written when references are on`() {
        assertThat(mask("\${env:S3_SECRET}", keepReferences = true))
            .describedAs("masked value of an environment reference with references on")
            .isEqualTo("\${env:S3_SECRET}")
    }

    @Test
    fun `should keep hiding a literal secret when references are on`() {
        assertThat(mask("s3cret", keepReferences = true))
            .describedAs("masked value of a literal secret with references on")
            .isEqualTo(placeholder)
    }

    @Test
    fun `should hide a value that mixes a reference with literal text when references are on`() {
        assertThat(mask("s3cret-\${env:S3_SECRET}", keepReferences = true))
            .describedAs("masked value with literal text next to a reference")
            .isEqualTo(placeholder)
    }

    @Test
    fun `should hide an escaped reference, which is literal text, when references are on`() {
        assertThat(mask("\$\${env:S3_SECRET}", keepReferences = true))
            .describedAs("masked value of an escaped reference")
            .isEqualTo(placeholder)
    }

    @Test
    fun `should hide a reference like any other text when references are off`() {
        assertThat(mask("\${file:/run/secrets/s3}", keepReferences = false))
            .describedAs("masked value of a reference with references off")
            .isEqualTo(placeholder)
    }

    @Test
    fun `should hide a value with an unclosed reference when references are on`() {
        assertThat(mask("\${env:S3_SECRET", keepReferences = true))
            .describedAs("masked value of an unclosed reference")
            .isEqualTo(placeholder)
    }

    @Test
    fun `should keep the stored reference when the placeholder is sent back`() {
        // given: a stored reference and a body that carries the placeholder the dashboard shows
        val incoming = s3(placeholder)
        val stored = s3("\${file:/run/secrets/s3}")

        // when: the body is restored from the stored document
        val restored = SettingsSecrets.restore(S3StorageProviderSettings::class.java, incoming, stored, placeholders = true)

        // then: the stored reference is kept, not the placeholder
        assertThat(restored.isOk).describedAs("restore result").isTrue()
        assertThat(restored.get().secretKey())
            .describedAs("secret key after the placeholder was sent back")
            .isEqualTo("\${file:/run/secrets/s3}")
    }

    @Test
    fun `should store a reference sent back as written, not as the stored value`() {
        // given: a stored literal secret and a body that replaces it with a reference
        val incoming = s3("\${env:S3_SECRET}")
        val stored = s3("old-secret")

        // when: the body is restored from the stored document
        val restored = SettingsSecrets.restore(S3StorageProviderSettings::class.java, incoming, stored, placeholders = true)

        // then: the reference is the new value, so it is stored literally
        assertThat(restored.isOk).describedAs("restore result").isTrue()
        assertThat(restored.get().secretKey())
            .describedAs("secret key after a reference was sent back")
            .isEqualTo("\${env:S3_SECRET}")
    }
}
