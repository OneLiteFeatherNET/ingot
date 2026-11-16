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

package net.onelitefeather.ingot.storage.azure

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** The capabilities of the settings: the shared namespace key and the support detail (design D8 and D9). */
internal class AzureBlobStorageProviderSettingsCapabilityTest {

    private fun settings(
        container: String = "repositories",
        prefix: String = "",
        accountName: String = "account",
        authentication: String = "default-credential",
        accountKey: String = "",
        sasToken: String = "",
        connectionString: String = "",
    ) = AzureBlobStorageProviderSettings(
        accountName = accountName,
        container = container,
        prefix = prefix,
        authentication = authentication,
        accountKey = accountKey,
        sasToken = sasToken,
        connectionString = connectionString,
    )

    @Test
    fun `should key the namespace by account, container and normalised prefix`() {
        // when: the prefix is written with surrounding slashes
        val key = settings(prefix = "/team/a/").namespaceKey("repository")

        // then: the key is the account, the container and the prefix with one trailing slash
        assertThat(key).isEqualTo("azure:account/repositories/team/a/")
    }

    @Test
    fun `should key an empty prefix as the container root`() {
        // when: no prefix is configured
        val key = settings().namespaceKey("repository")

        // then: the key ends with the container and no prefix
        assertThat(key).isEqualTo("azure:account/repositories/")
    }

    @Test
    fun `should not share a namespace between two containers of one account`() {
        // given: two repositories in the same account but different containers, both without a prefix
        val first = settings(container = "one").namespaceKey("a")!!
        val second = settings(container = "two").namespaceKey("b")!!

        // then: their keys do not overlap
        assertThat(settings(container = "one").overlaps(first, second)).`as`("overlap").isFalse()
    }

    @Test
    fun `should share a namespace when one prefix is nested under the other`() {
        // given: a repository at the root of a container and one below it
        val root = settings(prefix = "").namespaceKey("a")!!
        val nested = settings(prefix = "team").namespaceKey("b")!!

        // then: the keys overlap, and the refusal names the prefix
        assertThat(settings().overlaps(root, nested)).`as`("overlap").isTrue()
        assertThat(settings().overlapMessage()).`as`("refusal").contains("prefix")
    }

    @Test
    fun `should not share a namespace between sibling prefixes`() {
        // given: two sibling prefixes of one container
        val first = settings(prefix = "team-a").namespaceKey("a")!!
        val second = settings(prefix = "team-b").namespaceKey("b")!!

        // then: the keys do not overlap
        assertThat(settings().overlaps(first, second)).`as`("overlap").isFalse()
    }

    @Test
    fun `should describe the account, container, prefix and mode without any secret`() {
        // given: an account key that must never be published
        val described = settings(
            prefix = "team",
            authentication = "account-key",
            accountKey = "published-account-key-value",
        ).describe()

        // then: the description names the location and the mode, and carries no secret
        assertThat(described).containsEntry("accountName", "account")
            .containsEntry("container", "repositories")
            .containsEntry("prefix", "team")
            .containsEntry("authentication", "account-key")
        assertThat(described.values).`as`("values").noneMatch { it.contains("published-account-key-value") }
    }

}
