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

package net.onelitefeather.ingot.storage.gcs

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** The settings capabilities (design D9): the shared namespace key and the support details, which never carry a credential. */
internal class GcsStorageProviderSettingsCapabilityTest {

    private fun settings(
        bucket: String = "repositories",
        prefix: String = "",
        projectId: String = "",
        authentication: String = "adc",
        credentialsJson: String = "",
    ) = GcsStorageProviderSettings(
        bucket = bucket,
        prefix = prefix,
        projectId = projectId,
        authentication = authentication,
        credentialsJson = credentialsJson,
    )

    @Test
    fun `should key the namespace by bucket and normalised prefix`() {
        // when: the prefix is written with surrounding slashes
        val key = settings(prefix = "/team/a/").namespaceKey("repository")

        // then: the key is the bucket and the prefix with one trailing slash
        assertThat(key).`as`("namespace key").isEqualTo("gcs:repositories/team/a/")
    }

    @Test
    fun `should key an empty prefix as the bucket root`() {
        // then: the key ends with the bucket and no prefix
        assertThat(settings().namespaceKey("repository")).`as`("namespace key").isEqualTo("gcs:repositories/")
    }

    @Test
    fun `should have no namespace when the bucket is blank`() {
        // then: a blank bucket never collides, because the settings are refused anyway
        assertThat(settings(bucket = " ").namespaceKey("repository")).`as`("namespace key").isNull()
    }

    @Test
    fun `should collide when a prefix equals another prefix in the same bucket`() {
        // then: two repositories on the same prefix of one bucket overlap
        assertThat(settings().overlaps("gcs:repositories/team/", "gcs:repositories/team/")).`as`("overlap").isTrue()
    }

    @Test
    fun `should collide when one prefix nests under the other in the same bucket`() {
        // then: the objects of a nested prefix belong to the outer one, so they overlap
        assertThat(settings().overlaps("gcs:repositories/team/", "gcs:repositories/team/a/")).`as`("overlap").isTrue()
    }

    @Test
    fun `should not collide with a sibling prefix that shares only its first characters`() {
        // then: team and teams are different directories
        assertThat(settings().overlaps("gcs:repositories/team/", "gcs:repositories/teams/")).`as`("overlap").isFalse()
    }

    @Test
    fun `should not collide with the same prefix in another bucket`() {
        // then: the buckets are different namespaces
        assertThat(settings().overlaps("gcs:repositories/team/", "gcs:other/team/")).`as`("overlap").isFalse()
    }

    @Test
    fun `should describe the bucket, the project and the authentication without any credential`() {
        // when: the support details are read
        val details = settings(projectId = "project-1", authentication = "service-account-key", credentialsJson = "{\"private_key\":\"SECRET\"}")
            .describe()

        // then: the non-sensitive values are present and the key is not
        assertThat(details).`as`("details").containsEntry("bucket", "repositories")
            .containsEntry("projectId", "project-1")
            .containsEntry("authentication", "service-account-key")
        assertThat(details.values.joinToString()).`as`("details without the key").doesNotContain("SECRET")
    }

}
